package dev.jingtao.aicodebackend.core;

import cn.hutool.core.util.StrUtil;
import dev.jingtao.aicodebackend.ai.model.message.AppGenerationMessage;
import dev.langchain4j.model.chat.response.PartialToolCall;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 流式 writeFile 工具调用累加器。
 * <p>
 * 按工具调用 index 累积参数分片，实时从（可能未闭合的）JSON 中解析出文件路径与内容，
 * 并以增量 file_delta 事件边写边显；供 VUE 项目的 TokenStream 事件流使用。
 * 把这部分有状态、较长的解析逻辑从 facade 的回调中抽离出来，便于阅读与维护。
 */
class StreamingWriteFileAccumulator {

    private final Long appId;
    private final Consumer<AppGenerationMessage> emitter;

    /** 每个工具调用 index 累积的参数分片 */
    private final Map<Integer, StringBuilder> toolArgs = new HashMap<>();
    /** 每个 index 对应的工具名 */
    private final Map<Integer, String> toolNames = new HashMap<>();
    /** 每个 index 已发出 file_start 的文件路径 */
    private final Map<Integer, String> startedPaths = new HashMap<>();
    /** 每个 index 已增量输出的内容长度 */
    private final Map<Integer, Integer> emittedLen = new HashMap<>();
    /** 已通过分片流式输出过的文件路径 */
    private final Set<String> streamedPaths = new HashSet<>();

    StreamingWriteFileAccumulator(Long appId, Consumer<AppGenerationMessage> emitter) {
        this.appId = appId;
        this.emitter = emitter;
    }

    /**
     * 处理一个工具调用参数分片：仅对 writeFile 实时解析并以增量 file_delta 流式输出文件内容，
     * 实现真正的边写边显。其它工具的分片在此忽略（由 onToolExecuted 阶段统一处理）。
     */
    void onPartialToolCall(PartialToolCall partialToolCall) {
        int index = partialToolCall.index();
        if (StrUtil.isNotBlank(partialToolCall.name())) {
            toolNames.put(index, partialToolCall.name());
        }
        if (!"writeFile".equals(toolNames.get(index))) {
            return;
        }
        StringBuilder acc = toolArgs.computeIfAbsent(index, k -> new StringBuilder());
        if (partialToolCall.partialArguments() != null) {
            acc.append(partialToolCall.partialArguments());
        }
        String path = extractJsonString(acc.toString(), "relativeFilePath", true);
        if (path == null) {
            return;
        }
        path = path.replaceAll("\\\\(.)", "$1");
        if (!startedPaths.containsKey(index)) {
            startedPaths.put(index, path);
            emittedLen.put(index, 0);
            streamedPaths.add(path);
            emitter.accept(AppGenerationMessage.fileStart(appId, path));
        }
        String content = extractJsonString(acc.toString(), "content", false);
        if (content != null) {
            int emitted = emittedLen.getOrDefault(index, 0);
            if (content.length() > emitted) {
                String delta = content.substring(emitted);
                emittedLen.put(index, content.length());
                // overwrite=false 表示增量追加，前端逐片拼接
                emitter.accept(AppGenerationMessage.fileDelta(appId, startedPaths.get(index), delta, false));
            }
        }
    }

    /**
     * 该文件路径是否已通过分片流式输出过（用于 onToolExecuted 判断是否需要兜底完整输出）。
     */
    boolean wasStreamed(String path) {
        return streamedPaths.contains(path);
    }

    /**
     * 从（可能不完整的）JSON 字符串中增量解析指定字符串字段的值。
     * content 解析时（requireClosed=false）始终返回一个“稳定前缀”——遇到不完整的转义序列即停止，
     * 保证多次解析的结果只增不变，便于以增量方式向前端追加输出。
     *
     * @param json          累积到当前的（可能未闭合的）JSON 文本
     * @param key           字段名，如 relativeFilePath / content
     * @param requireClosed 是否要求读到结束引号才返回（路径需要完整，内容允许部分）
     * @return 解析出的字符串值；不满足条件时返回 null
     */
    private static String extractJsonString(String json, String key, boolean requireClosed) {
        String needle = "\"" + key + "\"";
        int keyIdx = json.indexOf(needle);
        if (keyIdx < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIdx + needle.length());
        if (colon < 0) {
            return null;
        }
        int quote = json.indexOf('"', colon + 1);
        if (quote < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean closed = false;
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                if (i + 1 >= json.length()) {
                    // 不完整的转义，停止，等待后续分片
                    break;
                }
                char next = json.charAt(i + 1);
                if (next == 'u') {
                    if (i + 6 > json.length()) {
                        // 不完整的 \\uXXXX，停止，等待后续分片
                        break;
                    }
                    try {
                        sb.append((char) Integer.parseInt(json.substring(i + 2, i + 6), 16));
                    } catch (NumberFormatException ex) {
                        sb.append('u');
                    }
                    i += 5;
                } else {
                    switch (next) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        default -> sb.append(next);
                    }
                    i += 1;
                }
            } else if (c == '"') {
                closed = true;
                break;
            } else {
                sb.append(c);
            }
        }
        if (requireClosed && !closed) {
            return null;
        }
        return sb.toString();
    }
}

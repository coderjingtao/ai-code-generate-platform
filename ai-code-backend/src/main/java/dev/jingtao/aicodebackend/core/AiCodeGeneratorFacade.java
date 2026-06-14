package dev.jingtao.aicodebackend.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import dev.jingtao.aicodebackend.ai.AiCodeGenerateServiceFactory;
import dev.jingtao.aicodebackend.ai.model.HtmlCodeResult;
import dev.jingtao.aicodebackend.ai.model.MultiFileCodeResult;
import dev.jingtao.aicodebackend.ai.model.message.*;
import dev.jingtao.aicodebackend.constant.AppConstant;
import dev.jingtao.aicodebackend.core.builder.VueProjectBuilder;
import dev.jingtao.aicodebackend.core.parser.CodeParserExecutor;
import dev.jingtao.aicodebackend.core.parser.FileBlockStreamParser;
import dev.jingtao.aicodebackend.core.saver.CodeFileSaverExecutor;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.exception.ThrowUtils;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import dev.jingtao.aicodebackend.service.AppFileService;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 直接面对的是AI底层大模型LLM层面的处理
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGenerateServiceFactory aiCodeGenerateServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private AppFileService appFileService;

    /**
     * 统一入口：根据代码生成的类型，生成代码并保存到文件（流式）
     * @param userPrompt 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @return 生成代码的实时流式字符串
     */
    public Flux<String> generateAndSaveCodeStream(String userPrompt, CodeGenTypeEnum codeGenTypeEnum, Long appId){
        return generateAndSaveCodeStream(userPrompt, codeGenTypeEnum, appId, false);
    }

    /**
     * 统一入口：根据代码生成的类型，生成代码并保存到文件（流式，支持控制是否跳过构建）
     * @param userPrompt 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @param appId 应用 ID
     * @param skipBuild 是否跳过流式结束后的构建步骤
     * @return 生成代码的实时流式字符串
     */
    public Flux<String> generateAndSaveCodeStream(String userPrompt, CodeGenTypeEnum codeGenTypeEnum, Long appId, boolean skipBuild){
        ThrowUtils.throwIf(codeGenTypeEnum == null, new BusinessException(ErrorCode.SYSTEM_ERROR, "生成代码类型为空"));
        var aiService = aiCodeGenerateServiceFactory.getAiService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum){
            case HTML -> {
                Flux<String> codeStream = aiService.generateHtmlCodeStream(userPrompt);
                yield processCodeStream(codeStream, codeGenTypeEnum, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiService.generateMultiFileCodeStream(userPrompt);
                yield processCodeStream(codeStream, codeGenTypeEnum, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiService.generateVueProjectCodeStream(appId, userPrompt);
                yield processTokenStream(tokenStream, appId, skipBuild);
            }
            default -> throw new IllegalArgumentException("Unsupported code generation type: " + codeGenTypeEnum);
        };
    }

    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId){
        if (CodeGenTypeEnum.VUE_PROJECT.equals(codeGenType)) {
            return codeStream;
        }
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                //实时收集代码片段
                .doOnNext(codeBuilder::append)
                //流式返回完成后保存代码
                .doOnComplete(() -> {
                    try{
                        String completedCode = codeBuilder.toString();
                        //使用执行器解析代码
                        Object parsedResult = CodeParserExecutor.executeParser(completedCode,codeGenType);
                        //使用执行器保存代码
                        File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult,codeGenType, appId);
                        log.info("{} mode files saved successfully: {}", codeGenType, savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("{} mode file saved failed, error: {}", codeGenType, e.getMessage(),e);
                    }
                });
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     * 它是面向AI底层模型/工具执行层面的回调，处理的是 TokenStream 回调状态
     * [核心职责]：
     * 把 LangChain4j 的 TokenStream 事件，根据回调状态包装成项目内部统一的 JSON 消息流
     *
     * @param tokenStream LangChain4j 的 TokenStream 对象
     * @param appId       应用 ID
     * @param skipBuild   是否跳过构建
     * @return 系统内部的JSON Message流
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId, boolean skipBuild){
        return Flux.create(sink -> {
            AtomicInteger emittedChunkCount = new AtomicInteger(0);

            TokenStream configuredStream = tokenStream
                    // AI普通文本分片: 模型每吐出一小段普通文本就触发(打字机效果)
                    .onPartialResponse(partialResponse -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                        emittedChunkCount.incrementAndGet();
                    })
                    // 工具调用流分片: 模型在"写"工具调用,比如它要调 writeFile(path, content),这个 content 参数是流式生成的,所以这里会触发很多次,partialArguments 一点点变长。此刻工具还没执行。
                    .onPartialToolCall(partialToolCall -> {
                        ToolCallMessage toolCallMessage = new ToolCallMessage(partialToolCall);
                        sink.next(JSONUtil.toJsonStr(toolCallMessage));
                        emittedChunkCount.incrementAndGet();
                    })
                    // 工具即将执行: 参数写完整了,LangChain4j 框架即将替你执行这个工具方法。这里拿到的 arguments 是完整的。
                    .beforeToolExecution(beforeToolExecution -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(beforeToolExecution);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                        emittedChunkCount.incrementAndGet();
                    })
                    // 工具执行完成,拿到返回值之后: 工具方法跑完了,result 就是它的返回值
                    .onToolExecuted(toolExecution -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                        emittedChunkCount.incrementAndGet();
                    })
                    // 模型完整响应结束
                    .onCompleteResponse(response -> {
                        // 兜底：某些模型实现可能只触发 onCompleteResponse，不触发 onPartialResponse 回调
                        if (emittedChunkCount.get() == 0
                                && response != null
                                && response.aiMessage() != null
                                && StrUtil.isNotBlank(response.aiMessage().text())) {
                            sink.next(JSONUtil.toJsonStr(new AiResponseMessage(response.aiMessage().text())));
                            emittedChunkCount.incrementAndGet();
                        }
                        log.info("TokenStream 完成，appId={}, emittedChunkCount={}", appId, emittedChunkCount.get());
                        if (!skipBuild) {
                            // 执行同步构建 Vue 项目，确保预览时项目已就绪
                            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
                            vueProjectBuilder.buildProject(projectPath);
                        } else {
                            log.info("工作流模式下跳过流式结束后的 Vue 项目构建，由后续构建节点统一执行，appId={}", appId);
                        }
                        sink.complete();
                    })
                    // 模型在任意阶段出错
                    .onError(error -> {
                        log.error("AI response error: {}", error.getMessage(), error);
                        sink.error(error);
                    });
            // 仅当LLM支持深度思考时，才会收到思考分片
            try {
                // 思考内容分片: 模型每吐出一小段思考/推理就触发,通常在正式回答之前
                configuredStream = configuredStream.onPartialThinking(partialThinking -> {
                    ThinkingMessage thinkingMessage = new ThinkingMessage(partialThinking);
                    sink.next(JSONUtil.toJsonStr(thinkingMessage));
                    emittedChunkCount.incrementAndGet();
                });
            } catch (UnsupportedOperationException e) {
                log.debug("当前 TokenStream 实现不支持 onPartialThinking，已降级为普通流式输出");
            }
            configuredStream.start();
        });
    }


    /**
     * 统一入口：根据代码生成的类型，生成代码并保存到文件（流式，支持控制是否跳过构建）
     * @param userPrompt 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @param appId 应用 ID
     * @param skipBuild 是否跳过流式结束后的构建步骤
     * @return 生成代码的实时流式字符串
     */
    public Flux<AppGenerationMessage> generateAndSaveCodeEventStream(String userPrompt, CodeGenTypeEnum codeGenTypeEnum, Long appId, boolean skipBuild){
        ThrowUtils.throwIf(codeGenTypeEnum == null, new BusinessException(ErrorCode.SYSTEM_ERROR, "生成代码类型为空"));
        var aiService = aiCodeGenerateServiceFactory.getAiService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum){
            case HTML -> {
                Flux<String> codeStream = aiService.generateHtmlCodeStream(userPrompt);
                yield processCodeStreamWithEvents(codeStream, codeGenTypeEnum, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiService.generateMultiFileCodeStream(userPrompt);
                yield processCodeStreamWithEvents(codeStream, codeGenTypeEnum, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiService.generateVueProjectCodeStream(appId, userPrompt);
                yield processTokenStreamWithEvents(tokenStream, appId, skipBuild);
            }
            default -> throw new IllegalArgumentException("Unsupported code generation type: " + codeGenTypeEnum);
        };
    }

    /**
     * 普通代码流转 v2 事件，解析 file:path 代码块并实时写入文件。
     *
     * @param codeStream  原始代码流
     * @param codeGenType 代码生成类型
     * @param appId       应用 ID
     * @return 事件流
     */
    private Flux<AppGenerationMessage> processCodeStreamWithEvents(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId){
        ThrowUtils.throwIf(codeGenType == CodeGenTypeEnum.VUE_PROJECT, ErrorCode.SYSTEM_ERROR);
        FileBlockStreamParser parser = new FileBlockStreamParser(appId);
        StringBuilder completedCodeBuilder = new StringBuilder();
        // 记录每个文件最新的累积内容，等收到 file_done 再一次性落盘
        java.util.Map<String, String> pendingFileContent = new java.util.HashMap<>();
        AtomicInteger savedFileCount = new AtomicInteger(0);
        return codeStream
                .concatMap(chunk -> {
                    completedCodeBuilder.append(chunk);
                    List<AppGenerationMessage> events = parser.accept(chunk);
                    persistFileEvents(events, codeGenType, pendingFileContent, savedFileCount);
                    return Flux.fromIterable(events);
                })
                .concatWith(Flux.defer(() -> {
                    List<AppGenerationMessage> remainingEvents = new ArrayList<>(parser.complete());
                    persistFileEvents(remainingEvents, codeGenType, pendingFileContent, savedFileCount);
                    if(savedFileCount.get() == 0) {
                        List<AppGenerationMessage> fallbackEvents = createFallbackFileEvents(completedCodeBuilder.toString(), codeGenType, appId);
                        persistFileEvents(fallbackEvents, codeGenType, pendingFileContent, savedFileCount);
                        remainingEvents.addAll(fallbackEvents);
                    }
                    remainingEvents.add(AppGenerationMessage.buildStatus(appId, "success", "代码已保存"));
                    remainingEvents.add(AppGenerationMessage.previewReady(appId, "预览已更新"));
                    return Flux.fromIterable(remainingEvents);
                }))
                .onErrorResume(error ->
                        Flux.just(AppGenerationMessage.error(appId, "生成失败：" + error.getMessage()))
                );
    }
    /**
     * 当流中没有 file:path 代码块时，使用传统解析器兜底生成文件事件
     */
    private List<AppGenerationMessage> createFallbackFileEvents(String completeCode, CodeGenTypeEnum codeGenType,
                                                                Long appId) {
        List<AppGenerationMessage> events = new ArrayList<>();
        Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
        if (codeGenType == CodeGenTypeEnum.HTML) {
            HtmlCodeResult result = (HtmlCodeResult) parsedResult;
            addFileEvents(events, appId, "index.html", result.getHtmlCode());
        } else if (codeGenType == CodeGenTypeEnum.MULTI_FILE) {
            MultiFileCodeResult result = (MultiFileCodeResult) parsedResult;
            addFileEvents(events, appId, "index.html", result.getHtmlCode());
            addFileEvents(events, appId, "style.css", result.getCssCode());
            addFileEvents(events, appId, "script.js", result.getJsCode());
        }
        return events;
    }

    /** 将单个文件内容包装为 file_start + file_delta + file_done 事件序列 */
    private void addFileEvents(List<AppGenerationMessage> events, Long appId, String path, String content) {
        if (content == null || content.isBlank()) {
            return;
        }
        events.add(AppGenerationMessage.toolCall(appId, "正在生成 " + path));
        events.add(AppGenerationMessage.fileStart(appId, path));
        events.add(AppGenerationMessage.fileDelta(appId, path, content, true));
        events.add(AppGenerationMessage.fileDone(appId, path));
    }

    /**
     * 将 TokenStream 转换为 v2 事件流（用于 Vue/React 项目类型）。
     *
     * @param tokenStream LangChain4j TokenStream
     * @param appId       应用 ID
     * @param skipBuild   是否跳过构建
     * @return AppGenerationMessage 事件流
     */
    private Flux<AppGenerationMessage> processTokenStreamWithEvents(TokenStream tokenStream, Long appId, boolean skipBuild) {
        return Flux.create(sink -> tokenStream
                // AI普通文本分片
                .onPartialResponse(partialResponse ->
                        sink.next(AppGenerationMessage.assistantMessage(appId, partialResponse)))
                // 工具即将执行：参数已完整，每个工具只触发一次（替代会按分片重复触发的 onPartialToolCall，避免刷屏）
                .beforeToolExecution(beforeToolExecution ->
                        sink.next(AppGenerationMessage.toolCall(appId, "正在执行工具：" + beforeToolExecution.request().name())))
                // 工具执行完成
                .onToolExecuted(toolExecution -> {
                    String toolName = toolExecution.request().name();
                    sink.next(AppGenerationMessage.toolCall(appId, "工具执行完成：" + toolName));
                    emitToolFileEvents(appId,toolName,toolExecution.request().arguments(), sink::next);
                })
                .onCompleteResponse(response -> {
                    try {
                        if (!skipBuild) {
                            // 执行同步构建 Vue 项目，确保预览时项目已就绪
                            buildProject(appId, sink::next);
                            sink.next(AppGenerationMessage.previewReady(appId, "预览已更新"));
                            sink.complete();
                        } else {
                            log.info("Mode : Workflow下跳过Vue 项目构建，由后续ProjectBuildNode统一执行，appId={}", appId);
                            sink.complete();
                        }
                    } catch (Exception e) {
                        sink.next(AppGenerationMessage.error(appId, "构建失败：" + e.getMessage()));
                        sink.complete();
                    }
                })
                .onError((Throwable error) -> {
                    sink.next(AppGenerationMessage.error(appId, "AI 回复失败：" + error.getMessage()));
                    sink.complete();
                })
                .start());
    }

    /**
     * 根据代码生成类型决定是否需要执行项目构建，并发射构建状态事件
     */
    private void buildProject(Long appId,
                              Consumer<AppGenerationMessage> consumer) {
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
        consumer.accept(AppGenerationMessage.buildStatus(appId, "building", "正在构建 Vue 项目"));
        boolean success = vueProjectBuilder.buildProject(projectPath);
        consumer.accept(AppGenerationMessage.buildStatus(appId, success ? "success" : "error",
                success ? "Vue 项目构建完成" : "Vue 项目构建失败"));
    }

    /**
     * 处理文件事件：file_delta 只记录该文件当前最新的累积内容，收到 file_done 时才一次性落盘，
     * 避免对同一个文件按行反复全量重写（原先每个增量都写一次，整体 O(N^2)）。
     */
    private void persistFileEvents(List<AppGenerationMessage> events, CodeGenTypeEnum codeGenType,
                                   java.util.Map<String, String> pendingFileContent, AtomicInteger savedFileCount) {
        for (AppGenerationMessage event : events) {
            if ("file_delta".equals(event.getType())) {
                pendingFileContent.put(event.getPath(), event.getContent());
            } else if ("file_done".equals(event.getType())) {
                String content = pendingFileContent.remove(event.getPath());
                if (content != null) {
                    // 先计入已完成文件（无论写盘成功与否），保证兜底解析的判定正确
                    savedFileCount.incrementAndGet();
                    try {
                        appFileService.writeFile(event.getAppId(), codeGenType, event.getPath(), content);
                    } catch (Exception e) {
                        // 单个文件写盘失败只记录日志，不中断整个生成流
                        log.error("写入文件失败，appId={}, path={}, error={}", event.getAppId(), event.getPath(), e.getMessage(), e);
                    }
                }
            }
        }
    }

    /**
     * 解析工具调用参数，提取 writeFile / modifyFile / deleteFile 的文件路径并发射对应的文件事件
     */
    private void emitToolFileEvents(Long appId, String toolName, String arguments,
                                    java.util.function.Consumer<AppGenerationMessage> consumer) {
        if (arguments == null || arguments.isBlank()) {
            return;
        }
        try {
            cn.hutool.json.JSONObject jsonObject = JSONUtil.parseObj(arguments);
            if ("writeFile".equals(toolName)) {
                String path = jsonObject.getStr("relativeFilePath");
                String content = jsonObject.getStr("content");
                if (path != null) {
                    consumer.accept(AppGenerationMessage.fileStart(appId, path));
                    consumer.accept(AppGenerationMessage.fileDelta(appId, path, content == null ? "" : content, true));
                    consumer.accept(AppGenerationMessage.fileDone(appId, path));
                }
            } else if ("modifyFile".equals(toolName)) {
                String path = jsonObject.getStr("relativeFilePath");
                if (path != null) {
                    String content = appFileService.readFileContent(appId, path);
                    consumer.accept(AppGenerationMessage.fileStart(appId, path));
                    consumer.accept(AppGenerationMessage.fileDelta(appId, path, content, true));
                    consumer.accept(AppGenerationMessage.fileDone(appId, path));
                }
            } else if ("deleteFile".equals(toolName)) {
                String path = jsonObject.getStr("relativeFilePath");
                if (path != null) {
                    consumer.accept(AppGenerationMessage.fileDelete(appId, path));
                }
            }
        } catch (Exception e) {
            log.warn("解析工具文件事件失败: {}", e.getMessage());
        }
    }

}

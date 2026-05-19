package dev.jingtao.aicodebackend.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.jingtao.aicodebackend.ai.model.message.*;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.ChatHistoryMessageTypeEnum;
import dev.jingtao.aicodebackend.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class JsonMessageStreamHandler {
    /**
     * 处理 TokenStream（VUE_PROJECT）
     * 解析 JSON 消息并重组为完整的响应格式
     *
     * @param originalFlux         原始流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的流
     */
    public Flux<String> handle(Flux<String> originalFlux,
                               ChatHistoryService chatHistoryService,
                               Long appId,
                               Users loginUser) {
        // 收集数据用于生成AI聊天记录便于生成历史记忆
        StringBuilder aiMessageBuilder = new StringBuilder();
        // 用于跟踪已经见过的工具ID，判断是否是第一次调用
        Set<String> seenToolIds = new HashSet<>();
        return originalFlux
                .map(chunk ->
                    //解析每一个json消息块，包含工具的调用信息
                     handleJsonMessageChunk(chunk, aiMessageBuilder, seenToolIds)
                )
                .filter(StrUtil::isNotEmpty)
                .doOnComplete(() -> {
                    // AI 流式响应完成后，添加AI消息到对话历史
                    String aiMessage = aiMessageBuilder.toString();
                    if(StrUtil.isNotBlank(aiMessage)) {
                        chatHistoryService.addChatHistory(appId, aiMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    }
                })
                .doOnError(error -> {
                    // 如果 AI 回复失败，也要记录错误信息
                    String aiErrorMessage = "AI response failed: " + error.getMessage();
                    chatHistoryService.addChatHistory(appId, aiErrorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                    log.error("AI response failed: {}", error.getMessage());
                });
    }

    /**
     * 解析并收集 TokenStream 返回的每一个json消息块，包含工具的调用信息
     */
    private String handleJsonMessageChunk(String chunk, StringBuilder aiMessageBuilder, Set<String> seenToolIds) {
        if (StrUtil.isBlank(chunk)) {
            return StrUtil.EMPTY;
        }
        String trimmedChunk = StrUtil.trimStart(chunk);
        if (!StrUtil.startWith(trimmedChunk, "{")) {
            aiMessageBuilder.append(chunk);
            return chunk;
        }
        //解析JSON
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum messageType = StreamMessageTypeEnum.getMessageType(streamMessage.getType());
        if (messageType == null) {
            aiMessageBuilder.append(chunk);
            return chunk;
        }
        switch (messageType) {
            case AI_RESPONSE -> {
                AiResponseMessage aiResponseMessage = JSONUtil.toBean(chunk, AiResponseMessage.class);
                String data = aiResponseMessage.getData();
                aiMessageBuilder.append(data);
                return data;
            }
            case TOOL_CALL -> {
                ToolCallMessage toolCallMessage = JSONUtil.toBean(chunk, ToolCallMessage.class);
                String toolId = toolCallMessage.getId();
                String toolName = toolCallMessage.getName();
                //检查是否为第一次看到这个工具ID
                if (toolId != null && !seenToolIds.contains(toolId)) {
                    //第一次调用该工具，记录这个工具ID
                    seenToolIds.add(toolId);
                    return "\n\n [⚙️选择工具: " + toolName + " 写入文件]\n\n";
                } else{
                    // 不是第一次调用该工具
                    return StrUtil.EMPTY;
                }
            }
            case TOOL_EXECUTED -> {
                ToolExecutedMessage toolExecutedMessage = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
                JSONObject jsonObject = JSONUtil.parseObj(toolExecutedMessage.getArguments());
                String relativeFilePath = jsonObject.getStr("fileRelativePath");
                if (StrUtil.isBlank(relativeFilePath)) {
                    relativeFilePath = jsonObject.getStr("relativeFilePath");
                }
                String suffix = FileUtil.getSuffix(relativeFilePath);
                String content = jsonObject.getStr("content");
                String result = String.format(
                        """
                                [工具调用] 写入文件 %s 
                                ```%s
                                %s
                                ```
                                """, relativeFilePath, suffix, content
                );
                //输出前端和要持久化的内容
                String output = String.format("\n\n%s\n\n", result);
                aiMessageBuilder.append(output);
                return output;
            }
            default -> {
                log.error("Unknown message type: {}", messageType);
                return StrUtil.EMPTY;
            }
        }
    }
}

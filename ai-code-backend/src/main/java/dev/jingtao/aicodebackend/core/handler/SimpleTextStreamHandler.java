package dev.jingtao.aicodebackend.core.handler;

import cn.hutool.core.util.StrUtil;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.ChatHistoryMessageTypeEnum;
import dev.jingtao.aicodebackend.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 直接收集完整的文本响应
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
        StringBuilder aiMessageBuilder = new StringBuilder();
        return originalFlux
                .doOnNext(aiMessageBuilder::append)
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
}

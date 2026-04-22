package dev.jingtao.aicodebackend.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCodeGenerateServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Bean
    public AiCodeGenerateService aiCodeGenerateService(){
        // For Chat Model only
//        return AiServices.create(AiCodeGenerateService.class, chatModel);

        // For Chat Model and Streaming Chat Model
        return AiServices.builder(AiCodeGenerateService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}

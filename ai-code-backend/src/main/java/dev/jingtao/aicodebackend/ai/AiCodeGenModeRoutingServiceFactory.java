package dev.jingtao.aicodebackend.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class AiCodeGenModeRoutingServiceFactory {

    @Resource(name = "stableOpenAiChatModel")
    private ChatModel chatModel;

    @Bean
    public AiCodeGenModeRoutingService aiCodeGenModeRoutingService(){
        return AiServices.builder(AiCodeGenModeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }
}

package dev.jingtao.aicodebackend.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiCodeGenerateServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Bean
    public AiCodeGenerateService aiCodeGenerateService(){
        return AiServices.create(AiCodeGenerateService.class, chatModel);
    }
}

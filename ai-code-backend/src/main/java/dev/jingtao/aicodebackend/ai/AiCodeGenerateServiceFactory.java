package dev.jingtao.aicodebackend.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.jingtao.aicodebackend.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class AiCodeGenerateServiceFactory {

    @Resource
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel streamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    /**
     *  存放【AI服务】实例的内存缓存
     *  缓存策略：
     *  1.最大缓存1000个实例
     *  2.写入后30分钟过期
     *  3.访问后10分钟过期
     */
    private final Cache<Long, AiCodeGenerateService> aiServiceCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener((key, value, cause) -> {
                log.debug("AiService removed from cache for AppId: {}, Cause: {}", key, cause);
            })
            .build();

    /**
     * 根据 App Id 获取相应的AI服务实例，如果实例不存在则创建一个实例
     * @param appId 应用ID
     * @return AI service
     */
    public AiCodeGenerateService getAiService(Long appId){
        return aiServiceCache.get(appId, this::createAiCodeGenerateService);
    }

    private AiCodeGenerateService createAiCodeGenerateService(Long appId){
        log.info("Creating new AiService for AppId: {}", appId);
        //根据 appId 构建独立的对话记忆空间
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        // 加载该 appId 的历史消息到记忆中
        log.info("Loading chat history for AppId: {}", appId);
        int loadCount = chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, 20);
        log.info("Loaded {} messages into memory for AppId: {}", loadCount, appId);
        return AiServices.builder(AiCodeGenerateService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .chatMemory(chatMemory)
                .build();
    }

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

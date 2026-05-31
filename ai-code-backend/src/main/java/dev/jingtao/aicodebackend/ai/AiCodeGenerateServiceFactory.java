package dev.jingtao.aicodebackend.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.jingtao.aicodebackend.ai.tools.ToolManager;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import dev.jingtao.aicodebackend.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@Slf4j
public class AiCodeGenerateServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private StreamingChatModel openAiStreamingChatModel;

    @Resource(name = "reasoningStreamingChatModel")
    private StreamingChatModel reasoningStreamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private ToolManager toolManager;

    /**
     *  存放【AI服务】实例的内存缓存
     *  缓存策略：
     *  1.最大缓存1000个实例
     *  2.写入后30分钟过期
     *  3.访问后10分钟过期
     */
    private final Cache<String, AiCodeGenerateService> aiServiceCache = Caffeine.newBuilder()
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
     * @param codeGenType 网站生成的模式
     * @return AI service
     */
    public AiCodeGenerateService getAiService(Long appId, CodeGenTypeEnum codeGenType){
        String cacheKey = buildCacheKey(appId, codeGenType);
        return aiServiceCache.get(cacheKey, key -> createAiCodeGenerateService(appId, codeGenType));
    }

    private AiCodeGenerateService createAiCodeGenerateService(Long appId, CodeGenTypeEnum codeGenType){
        log.info("Creating new AiService for AppId: {}, Type: {}", appId, codeGenType);
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

        // 根据代码生成类型选择不同的模型配置
        return switch (codeGenType) {
            // HTML 和 MULTI_FILE 使用默认模型
            case HTML, MULTI_FILE ->
                    AiServices.builder(AiCodeGenerateService.class)
                            .chatModel(chatModel)
                            .streamingChatModel(openAiStreamingChatModel)
                            .chatMemory(chatMemory)
                            .build();
            // VUE_PROJECT 使用推理模型
            case VUE_PROJECT ->
                    AiServices.builder(AiCodeGenerateService.class)
                            .streamingChatModel(reasoningStreamingChatModel)
                            .chatMemoryProvider(memoryId -> chatMemory)
                            .tools(toolManager.getAllTools())
                            .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(toolExecutionRequest, "Error: No such tool called " + toolExecutionRequest.name()))
                            .build();
            default -> throw new IllegalArgumentException("Unsupported code generation type: " + codeGenType);
        };
    }

    private String buildCacheKey(Long appId, CodeGenTypeEnum codeGenType){
        return appId + "_" + codeGenType.getValue();
    }
}

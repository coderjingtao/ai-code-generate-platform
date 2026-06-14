package dev.jingtao.aicodebackend.langgraph4j.state;

import dev.jingtao.aicodebackend.ai.model.message.AppGenerationMessage;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 工作流流式分片回调注册器。
 * 使用 sessionId 在节点间恢复 streamConsumer，避免 transient 字段在状态流转中丢失。
 */
@Component
public class WorkflowStreamConsumerRegistry {

    private final Map<String, Consumer<String>> consumerMap = new ConcurrentHashMap<>();

    public void register(String sessionId, Consumer<String> consumer) {
        if (sessionId == null || sessionId.isBlank() || consumer == null) {
            return;
        }
        consumerMap.put(sessionId, consumer);
    }

    public Consumer<String> get(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return consumerMap.get(sessionId);
    }

    public void remove(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        consumerMap.remove(sessionId);
    }

    // ===== v2 事件消费者（AppGenerationMessage） =====

    private final Map<String, Consumer<AppGenerationMessage>> eventConsumerMap = new ConcurrentHashMap<>();

    public void registerEvent(String sessionId, Consumer<AppGenerationMessage> consumer) {
        if (sessionId == null || sessionId.isBlank() || consumer == null) {
            return;
        }
        eventConsumerMap.put(sessionId, consumer);
    }

    public Consumer<AppGenerationMessage> getEvent(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return eventConsumerMap.get(sessionId);
    }

    public void removeEvent(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        eventConsumerMap.remove(sessionId);
    }
}

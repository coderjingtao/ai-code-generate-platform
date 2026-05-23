package dev.jingtao.aicodebackend.ai.model.message;

import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 流式消息类型枚举
 */
@Getter
public enum StreamMessageTypeEnum {
    AI_RESPONSE("AI响应","ai_response"),
    TOOL_CALL("工具执行请求","tool_call"),
    TOOL_EXECUTED("工具执行结果","tool_executed"),
    PARTIAL_THINKING("中间思考消息","partial_thinking")
    ;
    private final String desc;
    private final String value;

    StreamMessageTypeEnum(String desc, String value) {
        this.desc = desc;
        this.value = value;
    }

    private static final Map<String, StreamMessageTypeEnum> VALUE_MAP = Stream.of(values()).collect(Collectors.toUnmodifiableMap(e -> e.value, e -> e));

    public static StreamMessageTypeEnum getMessageType(String value) {
        return VALUE_MAP.get(value);
    }
}

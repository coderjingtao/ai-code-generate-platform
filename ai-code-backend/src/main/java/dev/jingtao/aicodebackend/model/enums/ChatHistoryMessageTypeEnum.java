package dev.jingtao.aicodebackend.model.enums;

import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum ChatHistoryMessageTypeEnum {

    USERS("USERS", "users"),
    AI("AI", "ai"),
    ;

    private final String name;

    private final String value;

    ChatHistoryMessageTypeEnum(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public static final Map<String, ChatHistoryMessageTypeEnum> VALUE_MAP = Stream.of(values()).collect(Collectors.toUnmodifiableMap(e -> e.value, e -> e));

    public static ChatHistoryMessageTypeEnum getEnumByValue(String value) {
        return VALUE_MAP.get(value);
    }
}

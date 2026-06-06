package dev.jingtao.aicodebackend.model.enums;

import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 代码生成模式枚举
 */
@Getter
public enum CodeGenModeEnum {

    CLASSIC("标准模式","classic"),
    WORKFLOW("工作流模式","workflow"),
    ;
    private final String desc;
    private final String value;

    CodeGenModeEnum(String desc, String value) {
        this.desc = desc;
        this.value = value;
    }

    private static final Map<String, CodeGenModeEnum> VALUE_MAP = Stream.of(values()).collect(Collectors.toUnmodifiableMap(e -> e.value, e -> e));

    public static CodeGenModeEnum getEnumByValue(String value) {
        return VALUE_MAP.get(value);
    }
}

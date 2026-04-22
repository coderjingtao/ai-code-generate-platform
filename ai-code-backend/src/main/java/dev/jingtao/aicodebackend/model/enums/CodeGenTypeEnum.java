package dev.jingtao.aicodebackend.model.enums;

import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum CodeGenTypeEnum {

    HTML("原生HTML模式","html"),
    MULTI_FILE("原生多文件模式","multi_file")
    ;
    private final String desc;
    private final String value;

    CodeGenTypeEnum(String desc, String value) {
        this.desc = desc;
        this.value = value;
    }

    private static final Map<String,CodeGenTypeEnum> VALUE_MAP = Stream.of(values()).collect(Collectors.toUnmodifiableMap(e -> e.value, e -> e));

    public static CodeGenTypeEnum getEnumByValue(String value) {
        return VALUE_MAP.get(value);
    }
}

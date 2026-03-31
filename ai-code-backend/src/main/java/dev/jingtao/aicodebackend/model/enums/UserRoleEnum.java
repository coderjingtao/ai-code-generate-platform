package dev.jingtao.aicodebackend.model.enums;

import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public enum UserRoleEnum {

    USER("用户","user"),
    ADMIN("管理员","admin");

    private final String name;
    private final String value;

    UserRoleEnum(String name, String value) {
        this.name = name;
        this.value = value;
    }

    private static final Map<String, UserRoleEnum> NAME_MAP =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(e -> e.name, e -> e));

    private static final Map<String, UserRoleEnum> VALUE_MAP =
            Stream.of(values()).collect(Collectors.toUnmodifiableMap(e -> e.value, e -> e));

    public static UserRoleEnum getEnumByValue(String value) {
        return VALUE_MAP.get(value);
    }
    public static UserRoleEnum getEnumByName(String name) {
        return NAME_MAP.get(name);
    }
}

package dev.jingtao.aicodebackend.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserRoleEnumTest {

    @Test
    public void testGetEnumByValueByValue() {
        String value = "user";
        assertEquals(UserRoleEnum.USER, UserRoleEnum.getEnumByValue(value));
    }
}
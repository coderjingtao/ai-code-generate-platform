package dev.jingtao.aicodebackend.ai;

import dev.jingtao.aicodebackend.ai.model.HtmlCodeResult;
import dev.jingtao.aicodebackend.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGenerateServiceTest {

    @Resource
    private AiCodeGenerateService aiCodeGenerateService;

    @Test
    void generateCode() {
        String result = aiCodeGenerateService.generateCode("你是什么模型");
        System.out.println(result);
        Assertions.assertNotNull(result);
    }

    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGenerateService.generateHtmlCode("做个最简单的登录页");
        System.out.println(result);
        Assertions.assertNotNull(result);
    }

    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult result = aiCodeGenerateService.generateMultiFileCode("做个最简单的登录页");
        System.out.println(result);
        Assertions.assertNotNull(result);
    }
}
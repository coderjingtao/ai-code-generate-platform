package dev.jingtao.aicodebackend.langgraph4j.tools;

import dev.jingtao.aicodebackend.langgraph4j.model.ImageResource;
import dev.jingtao.aicodebackend.langgraph4j.model.enums.ImageCategoryEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LogoSearchToolTest {

    @Resource
    private LogoSearchTool logoSearchTool;

    @Test
    void searchLogos() {
        // 测试正常搜索
        List<ImageResource> logos = logoSearchTool.searchLogos("Happy");
        assertNotNull(logos);
        // 如果 API Key 正确且网络通畅，应该能搜到结果
        if (!logos.isEmpty()) {
            ImageResource firstLogo = logos.getFirst();
            assertEquals(ImageCategoryEnum.LOGO, firstLogo.getCategory());
            assertNotNull(firstLogo.getDescription());
            assertNotNull(firstLogo.getUrl());
            assertTrue(firstLogo.getUrl().startsWith("http"));
            System.out.println("搜索到 " + logos.size() + " 个 Logo");
            logos.forEach(logo ->
                    System.out.println("Logo: " + logo.getDescription() + " - " + logo.getUrl())
            );
        } else {
            System.out.println("未搜索到 Logo，请检查 API Key 或网络状况。");
        }
    }
}

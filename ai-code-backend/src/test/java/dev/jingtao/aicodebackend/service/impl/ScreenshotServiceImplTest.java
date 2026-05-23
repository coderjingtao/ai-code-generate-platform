package dev.jingtao.aicodebackend.service.impl;

import dev.jingtao.aicodebackend.service.ScreenshotService;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ScreenshotServiceImplTest {

    @Resource
    private ScreenshotService screenshotService;

    @Test
    void takeAndUploadScreenshot() {
        String webUrl = "http://localhost:8080/gJuSFY/#/";
        String screenshotUrl = screenshotService.takeAndUploadScreenshot(webUrl);
        System.out.println(screenshotUrl);
        assertNotNull(screenshotUrl);
    }
}
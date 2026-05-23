package dev.jingtao.aicodebackend.utils;


import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WebScreenshotUtilsTest {

    @Test
    void saveScreenshot() {
        String webUrl = "https://www.google.com";
        String s = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        System.out.println(s);
        Assertions.assertNotNull(s);
    }

}
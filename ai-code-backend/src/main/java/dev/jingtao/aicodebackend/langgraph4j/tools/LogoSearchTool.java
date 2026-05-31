package dev.jingtao.aicodebackend.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.jingtao.aicodebackend.langgraph4j.model.ImageResource;
import dev.jingtao.aicodebackend.langgraph4j.model.enums.ImageCategoryEnum;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Logo 图片生成工具
 */
@Slf4j
@Component
public class LogoSearchTool {

    private static final String LOGO_DEV_API_URL = "https://api.logo.dev/search?q=%s";

    @Value("${logoDev.api-key}")
    private String apiKey;

    @Tool("根据关键字生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> searchLogos(@P("搜索关键词, 只接收一个英文单词作为关键词") String query) {
        List<ImageResource> imageList = new ArrayList<>();
        if (StrUtil.isBlank(query)) {
            return imageList;
        }
        String encodedQuery = encodePathSegment(query);
        String apiUrl = String.format(LOGO_DEV_API_URL, encodedQuery);
        // 调用 API，注意释放资源
        try (HttpResponse response = HttpRequest.get(apiUrl)
                .header("Authorization", apiKey)
                .execute()) {
            if (response.isOk()) {
                JSONArray results = JSONUtil.parseArray(response.body());
                for (int i = 0; i < results.size(); i++) {
                    JSONObject logo = results.getJSONObject(i);
                    imageList.add(ImageResource.builder()
                            .category(ImageCategoryEnum.LOGO)
                            .description(logo.getStr("name", query))
                            .url(logo.getStr("logo_url"))
                            .build());
                }
            }
        } catch (Exception e) {
            log.error("Logo.dev API 调用失败: {}", e.getMessage(), e);
        }
        return imageList;
    }

    private String encodePathSegment(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }
}

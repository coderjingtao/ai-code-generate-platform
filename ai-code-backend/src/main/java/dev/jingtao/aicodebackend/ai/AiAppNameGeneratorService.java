package dev.jingtao.aicodebackend.ai;

import dev.langchain4j.service.SystemMessage;

public interface AiAppNameGeneratorService {
    /**
     * 根据用户的应用描述，生成一个短小、贴切的应用名称
     * @param userPrompt 用户对需求的描述
     * @return 应用名称（仅名称本身）
     */
    @SystemMessage(fromResource = "prompt/app-name-generator-system-prompt.txt")
    String generateAppName(String userPrompt);
}

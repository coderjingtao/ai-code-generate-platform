package dev.jingtao.aicodebackend.ai;

import dev.jingtao.aicodebackend.model.enums.CodeGenModeEnum;
import dev.langchain4j.service.SystemMessage;

public interface AiCodeGenModeRoutingService {
    /**
     * 根据用户需求智能选择代码生成模式（classic / workflow）
     * @param userPrompt 用户对需求的描述
     * @return 推荐的代码生成模式
     */
    @SystemMessage(fromResource = "prompt/codegen-mode-routing-system-prompt.txt")
    CodeGenModeEnum routeCodeGenMode(String userPrompt);
}

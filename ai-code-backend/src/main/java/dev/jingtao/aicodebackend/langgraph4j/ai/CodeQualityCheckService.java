package dev.jingtao.aicodebackend.langgraph4j.ai;

import dev.jingtao.aicodebackend.langgraph4j.model.QualityResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 代码质量检查服务
 */
public interface CodeQualityCheckService {

    /**
     * 检查代码质量
     * AI 会分析代码并返回质量检查结果
     */
    @SystemMessage(fromResource = "prompt/code-quality-check-system-prompt.txt")
    @UserMessage("{{codeContent}}")
    QualityResult checkCodeQuality(@V("codeContent") String codeContent);
}

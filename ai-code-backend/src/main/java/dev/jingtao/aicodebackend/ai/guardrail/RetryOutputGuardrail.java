package dev.jingtao.aicodebackend.ai.guardrail;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryOutputGuardrail implements OutputGuardrail {

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        if (responseFromLLM == null) {
            return OutputGuardrailResult.successWith(AiMessage.from(""));
        }
        // 工具调用阶段常见为“仅 tool_calls、无 text”，不应触发重试。
        if (responseFromLLM.hasToolExecutionRequests()) {
            String safeText = responseFromLLM.text() == null ? "" : responseFromLLM.text();
            return OutputGuardrailResult.successWith(responseFromLLM.withText(safeText));
        }
        String text = responseFromLLM.text();
        if (StrUtil.isBlank(text)) {
            return OutputGuardrailResult.successWith(responseFromLLM.withText(""));
        }
        if(text.length() < 3){
            return reprompt("响应内容过短", "请提供更详细的内容");
        }
        if (containsSensitiveContent(text)) {
            log.warn("Output contains sensitive content: {}", text);
            return reprompt("包含敏感信息", "请重新生成内容，避免包含敏感信息");
        }
        return success();
    }

    /**
     * 检查是否包含敏感内容
     */
    private boolean containsSensitiveContent(String response) {
        String lowerResponse = response.toLowerCase();
        String[] sensitiveWords = {
                "密码", "password", "secret", "token",
                "api key", "私钥", "证书"
        };
        for (String word : sensitiveWords) {
            if (lowerResponse.contains(word)) {
                return true;
            }
        }
        return false;
    }
}

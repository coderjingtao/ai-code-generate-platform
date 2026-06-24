package dev.jingtao.aicodebackend.exception;

import dev.jingtao.aicodebackend.utils.PromptLanguageUtils;
import lombok.Getter;

/**
 * 面向前端的 AI 服务不可用错误信息。
 */
@Getter
public class AiServiceUnavailableError {

    private static final long DEFAULT_RETRY_AFTER_SECONDS = 30L;

    private final int code;

    private final String errorType;

    private final String message;

    private final Long retryAfterSeconds;

    private AiServiceUnavailableError(String lang) {
        this.code = ErrorCode.AI_SERVICE_UNAVAILABLE_ERROR.getCode();
        this.errorType = "AI_SERVICE_UNAVAILABLE";
        this.message = PromptLanguageUtils.ZH.equals(lang)
                ? "当前 AI 模型请求量较高，请稍后再试"
                : "The AI model is currently under high demand, please try again later";
        this.retryAfterSeconds = DEFAULT_RETRY_AFTER_SECONDS;
    }

    public static AiServiceUnavailableError create(String lang) {
        return new AiServiceUnavailableError(lang);
    }
}

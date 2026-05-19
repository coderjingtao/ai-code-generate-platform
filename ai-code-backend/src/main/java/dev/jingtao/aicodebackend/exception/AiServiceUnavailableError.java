package dev.jingtao.aicodebackend.exception;

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

    private AiServiceUnavailableError() {
        this.code = ErrorCode.AI_SERVICE_UNAVAILABLE_ERROR.getCode();
        this.errorType = "AI_SERVICE_UNAVAILABLE";
        this.message = "当前 AI 模型请求量较高，请稍后再试";
        this.retryAfterSeconds = DEFAULT_RETRY_AFTER_SECONDS;
    }

    public static AiServiceUnavailableError create() {
        return new AiServiceUnavailableError();
    }
}

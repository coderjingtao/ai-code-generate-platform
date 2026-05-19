package dev.jingtao.aicodebackend.exception;

import cn.hutool.core.util.StrUtil;
import lombok.Getter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 面向前端的 AI 限流错误信息。
 */
@Getter
public class AiRateLimitError {

    private static final Pattern RETRY_IN_PATTERN = Pattern.compile("retry in\\s+(\\d+(?:\\.\\d+)?)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern RETRY_DELAY_PATTERN = Pattern.compile("\"retryDelay\"\\s*:\\s*\"(\\d+)s\"", Pattern.CASE_INSENSITIVE);

    private final int code;

    private final String errorType;

    private final String message;

    private final Long retryAfterSeconds;

    private AiRateLimitError(Long retryAfterSeconds) {
        this.code = ErrorCode.AI_RATE_LIMIT_ERROR.getCode();
        this.errorType = "AI_RATE_LIMIT";
        this.message = buildFriendlyMessage(retryAfterSeconds);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static AiRateLimitError from(Throwable throwable) {
        return new AiRateLimitError(parseRetryAfterSeconds(throwable));
    }

    private static String buildFriendlyMessage(Long retryAfterSeconds) {
        if (retryAfterSeconds == null) {
            return "当前 AI 服务请求过于频繁，请稍后再试";
        }
        return "当前 AI 服务请求过于频繁，请约 " + retryAfterSeconds + " 秒后重试";
    }

    private static Long parseRetryAfterSeconds(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (StrUtil.isBlank(message)) {
            return null;
        }
        Long retrySeconds = parseByPattern(message, RETRY_DELAY_PATTERN);
        if (retrySeconds != null) {
            return retrySeconds;
        }
        return parseByPattern(message, RETRY_IN_PATTERN);
    }

    private static Long parseByPattern(String message, Pattern pattern) {
        Matcher matcher = pattern.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        double seconds = Double.parseDouble(matcher.group(1));
        return Math.max(1, (long) Math.ceil(seconds));
    }
}

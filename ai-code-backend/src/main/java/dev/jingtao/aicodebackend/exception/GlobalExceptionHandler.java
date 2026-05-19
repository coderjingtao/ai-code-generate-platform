package dev.jingtao.aicodebackend.exception;

import dev.jingtao.aicodebackend.common.BaseResponse;
import dev.jingtao.aicodebackend.common.ResultUtils;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    @ExceptionHandler(RateLimitException.class)
    public BaseResponse<AiRateLimitError> rateLimitExceptionHandler(RateLimitException e) {
        AiRateLimitError error = AiRateLimitError.from(e);
        log.warn("AI rate limit, retryAfterSeconds={}", error.getRetryAfterSeconds(), e);
        return new BaseResponse<>(ErrorCode.AI_RATE_LIMIT_ERROR.getCode(), error, error.getMessage());
    }

    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    @ExceptionHandler(HttpException.class)
    public BaseResponse<?> httpExceptionHandler(HttpException e) {
        if (e.statusCode() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            AiServiceUnavailableError error = AiServiceUnavailableError.create();
            log.warn("AI service unavailable", e);
            return new BaseResponse<>(ErrorCode.AI_SERVICE_UNAVAILABLE_ERROR.getCode(), error, error.getMessage());
        }
        log.error("AI http exception, statusCode={}", e.statusCode(), e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "AI 服务异常，请稍后再试");
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}

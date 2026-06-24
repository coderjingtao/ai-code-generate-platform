package dev.jingtao.aicodebackend.common;

import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.utils.MessageTranslator;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, MessageTranslator.translate(errorCode.getMessage()));
    }
}

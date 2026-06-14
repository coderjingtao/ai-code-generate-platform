package dev.jingtao.aicodebackend.ai.model.message;

import lombok.Getter;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 流式消息类型枚举
 */
@Getter
public enum StreamMessageTypeEnum {
    AI_RESPONSE("AI响应","ai_response"),
    TOOL_REQUEST("工具调用请求","tool_request"),
    TOOL_CALL("工具执行过程","tool_call"),
    TOOL_EXECUTED("工具执行结果","tool_executed"),
    ASSISTANT_MESSAGE("AI文本响应", "assistant_message"),
    THINKING("深度思考消息","thinking"),
    FILE_START("文件开始生成", "file_start"),
    FILE_DELTA("文件内容更新", "file_delta"),
    FILE_DONE("文件生成完成", "file_done"),
    FILE_DELETE("文件删除", "file_delete"),
    BUILD_STATUS("构建状态", "build_status"),
    PREVIEW_READY("预览可用", "preview_ready"),
    ERROR("应用生成错误", "generation_error"),
    DONE("应用生成完成", "done"),
    ;
    private final String desc;
    private final String value;

    StreamMessageTypeEnum(String desc, String value) {
        this.desc = desc;
        this.value = value;
    }

    private static final Map<String, StreamMessageTypeEnum> VALUE_MAP = Stream.of(values()).collect(Collectors.toUnmodifiableMap(e -> e.value, e -> e));

    public static StreamMessageTypeEnum getMessageType(String value) {
        return VALUE_MAP.get(value);
    }
}

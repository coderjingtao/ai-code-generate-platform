package dev.jingtao.aicodebackend.ai.model.message;

import dev.langchain4j.model.chat.response.PartialToolCall;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolCallMessage extends StreamMessage{

    private String id;
    private String name;
    private String partialArguments;

    public ToolCallMessage(PartialToolCall partialToolCall) {
        super(StreamMessageTypeEnum.TOOL_CALL.getValue());
        this.id = partialToolCall.id();
        this.name = partialToolCall.name();
        this.partialArguments = partialToolCall.partialArguments();
    }
}

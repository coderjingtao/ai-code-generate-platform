package dev.jingtao.aicodebackend.ai.model.message;

import dev.langchain4j.model.chat.response.PartialThinking;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class PartialThinkingMessage extends StreamMessage{

    private String text;

    public PartialThinkingMessage(PartialThinking partialThinking) {
        super(StreamMessageTypeEnum.PARTIAL_THINKING.getValue());
        this.text = partialThinking.text();
    }
}

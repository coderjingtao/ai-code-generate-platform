package dev.jingtao.aicodebackend.ai.model.message;

import dev.langchain4j.model.chat.response.PartialThinking;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ThinkingMessage extends StreamMessage{

    private String data;

    public ThinkingMessage(String data) {
        super(StreamMessageTypeEnum.THINKING.getValue());
        this.data = data;
    }

    public ThinkingMessage(PartialThinking partialThinking) {
        this(partialThinking == null ? null : partialThinking.text());
    }
}

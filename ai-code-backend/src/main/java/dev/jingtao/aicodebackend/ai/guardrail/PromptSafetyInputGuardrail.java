package dev.jingtao.aicodebackend.ai.guardrail;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class PromptSafetyInputGuardrail implements InputGuardrail {

    // 敏感词列表
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous instructions", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );

    // 注入攻击模式
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    @Override
    public InputGuardrailResult validate(InputGuardrailRequest inputRequest) {
        if (inputRequest != null) {
            UserMessage userMessage = inputRequest.userMessage();
            if (userMessage != null && userMessage.singleText() != null) {
                String text = userMessage.singleText();

                if(StrUtil.isBlank(text)){
                    return fatal("Prompt must not be empty");
                }
//                if(text.length() > 1000){
//                    return fatal("Prompt is too long");
//                }

                // 1. 敏感词检查
                String lowerText = text.toLowerCase();
                for (String word : SENSITIVE_WORDS) {
                    if (lowerText.contains(word.toLowerCase())) {
                        log.warn("Prompt contains sensitive word: {}", word);
                        return fatal("The input contains inappropriate content. Please modify it and try again.");
                    }
                }

                // 2. 注入攻击模式检查
                for (Pattern pattern : INJECTION_PATTERNS) {
                    if (pattern.matcher(text).find()) {
                        log.warn("Prompt injection attempt detected: {}", pattern.pattern());
                        return fatal("Malicious input detected, request rejected.");
                    }
                }
            }
        }
        return success();
    }
}

package dev.jingtao.aicodebackend.core.engine;

import dev.jingtao.aicodebackend.ai.model.message.AppGenerationMessage;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.CodeGenModeEnum;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import reactor.core.publisher.Flux;

/**
 * 代码生成引擎
 */
public interface CodeGenEngine {

    /**
     * 当前引擎支持的生成模式
     */
    CodeGenModeEnum mode();

    /**
     * 生成代码流
     * @param lang 输出语言（en / zh），用于注入语言指令
     */
    Flux<String> generate(Long appId, String userPrompt, Users loginUser, CodeGenTypeEnum codeGenTypeEnum, String lang);

    /**
     * 生成event代码流
     * @param lang 输出语言（en / zh），用于注入语言指令
     */
    Flux<AppGenerationMessage> generateEvent(Long appId, String userPrompt, Users loginUser, CodeGenTypeEnum codeGenTypeEnum, String lang);
}

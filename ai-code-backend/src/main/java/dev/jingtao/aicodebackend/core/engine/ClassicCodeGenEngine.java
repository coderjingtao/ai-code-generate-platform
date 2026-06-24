package dev.jingtao.aicodebackend.core.engine;

import dev.jingtao.aicodebackend.ai.model.message.AppGenerationMessage;
import dev.jingtao.aicodebackend.core.AiCodeGeneratorFacade;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.CodeGenModeEnum;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import dev.jingtao.aicodebackend.utils.PromptLanguageUtils;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class ClassicCodeGenEngine implements CodeGenEngine{

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Override
    public CodeGenModeEnum mode() {
        return CodeGenModeEnum.CLASSIC;
    }

    @Override
    public Flux<String> generate(Long appId, String userPrompt, Users loginUser, CodeGenTypeEnum codeGenTypeEnum, String lang) {
        return aiCodeGeneratorFacade.generateAndSaveCodeStream(userPrompt, codeGenTypeEnum, appId, PromptLanguageUtils.requirement(lang));
    }

    @Override
    public Flux<AppGenerationMessage> generateEvent(Long appId, String userPrompt, Users loginUser, CodeGenTypeEnum codeGenTypeEnum, String lang) {
        return aiCodeGeneratorFacade.generateAndSaveCodeEventStream(userPrompt, codeGenTypeEnum, appId, false, PromptLanguageUtils.requirement(lang));
    }
}

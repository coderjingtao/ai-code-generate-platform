package dev.jingtao.aicodebackend.core.engine;

import dev.jingtao.aicodebackend.core.AiCodeGeneratorFacade;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.CodeGenModeEnum;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
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
    public Flux<String> generate(Long appId, String userPrompt, Users loginUser, CodeGenTypeEnum codeGenTypeEnum) {
        return aiCodeGeneratorFacade.generateAndSaveCodeStream(userPrompt, codeGenTypeEnum, appId);
    }
}

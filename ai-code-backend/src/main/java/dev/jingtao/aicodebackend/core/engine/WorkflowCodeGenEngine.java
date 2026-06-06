package dev.jingtao.aicodebackend.core.engine;

import dev.jingtao.aicodebackend.langgraph4j.CodeGenWorkflow;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.CodeGenModeEnum;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class WorkflowCodeGenEngine implements CodeGenEngine{

    @Resource
    private CodeGenWorkflow codeGenWorkflow;

    @Override
    public CodeGenModeEnum mode() {
        return CodeGenModeEnum.WORKFLOW;
    }

    @Override
    public Flux<String> generate(Long appId, String userPrompt, Users loginUser, CodeGenTypeEnum codeGenTypeEnum) {
        return codeGenWorkflow.executeWorkflowForUserChat(userPrompt,appId,codeGenTypeEnum);
    }
}

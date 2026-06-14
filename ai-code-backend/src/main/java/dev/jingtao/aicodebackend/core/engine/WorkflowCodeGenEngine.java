package dev.jingtao.aicodebackend.core.engine;

import dev.jingtao.aicodebackend.ai.model.message.AppGenerationMessage;
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

    @Override
    public Flux<AppGenerationMessage> generateEvent(Long appId, String userPrompt, Users loginUser, CodeGenTypeEnum codeGenTypeEnum) {
        // 工作流模式尚未提供 v2 结构化事件流（节点只产出原始代码块 Flux<String>）。
        // 这里以一条错误事件优雅降级，避免同步抛异常使请求直接失败。
        return Flux.just(AppGenerationMessage.error(appId,
                "工作流模式暂不支持实时事件流（v2），请切换为经典模式后重试"));
    }
}

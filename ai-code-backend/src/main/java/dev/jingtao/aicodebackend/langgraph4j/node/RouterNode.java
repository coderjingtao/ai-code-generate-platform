package dev.jingtao.aicodebackend.langgraph4j.node;

import dev.jingtao.aicodebackend.ai.AiCodeGenTypeRoutingService;
import dev.jingtao.aicodebackend.langgraph4j.state.WorkflowContext;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import dev.jingtao.aicodebackend.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class RouterNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("开始执行节点: 智能路由");

            if(context.getGenerationType() != null){
                log.info("检测到预设生成类型，跳过智能路由: {}", context.getGenerationType().getValue());
                context.setCurrentStep("智能路由(已跳过-已存在生成类型)");
                return WorkflowContext.saveContext(context);
            }

            CodeGenTypeEnum generationType;
            try{
                var routingService = SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class);
                generationType = routingService.routeCodeGenType(context.getOriginalPrompt());
                log.info("AI智能路由完成，选择类型: {} ({})", generationType.getValue(), generationType.getDesc());
            } catch (Exception e) {
                log.error("AI智能路由失败，使用默认HTML类型: {}", e.getMessage());
                generationType = CodeGenTypeEnum.HTML;
            }

            // 更新状态
            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            log.info("路由决策完成，选择类型: {}", generationType.getDesc());
            return WorkflowContext.saveContext(context);
        });
    }
}


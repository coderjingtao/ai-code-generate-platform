package dev.jingtao.aicodebackend.langgraph4j;

import cn.hutool.json.JSONUtil;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.langgraph4j.model.QualityResult;
import dev.jingtao.aicodebackend.langgraph4j.node.*;
import dev.jingtao.aicodebackend.langgraph4j.state.WorkflowContext;
import dev.jingtao.aicodebackend.langgraph4j.state.WorkflowStreamConsumerRegistry;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 创建完整的代码生成工作流：
 * 该工作流在【图片收集节点ImageCollectorNode】中，使用CompletableFuture机制并发的获取相关图片并汇总
 */
@Slf4j
@Component
public class CodeGenWorkflow {

    @Resource
    private WorkflowStreamConsumerRegistry workflowStreamConsumerRegistry;

    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try{
            return new MessagesStateGraph<String>()
                    // 添加节点 - 使用完整实现的节点
                    .addNode("image_collector", ImageCollectorNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addNode("code_generator", CodeGeneratorNode.create())
                    .addNode("code_quality_check", CodeQualityCheckNode.create())
                    .addNode("project_builder", ProjectBuilderNode.create())

                    // 添加边
                    .addEdge(START, "image_collector")
                    .addEdge("image_collector", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    .addEdge("code_generator", "code_quality_check")
                    // 新增质检条件边：根据质检结果决定下一步
                    .addConditionalEdges("code_quality_check",
                            edge_async(this::routeAfterQualityCheck),
                            Map.of(
                                    "build", "project_builder",   // 质检通过且需要构建
                                    "skip_build", END,            // 质检通过但跳过构建
                                    "fail", "code_generator"      // 质检失败，重新生成
                            ))
                    .addEdge("project_builder", END)
                    // 编译工作流
                    .compile();

        }catch (GraphStateException e) {
            throw new BusinessException(
                    ErrorCode.OPERATION_ERROR, "工作流创建失败");
        }
    }

    private String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();
        // 如果质检失败，重新生成代码
        if (qualityResult == null || !qualityResult.getIsValid()) {
            log.error("代码质检失败，需要重新生成代码");
            return "fail";
        }
        // 质检通过，使用原有的构建路由逻辑
        log.info("代码质检通过，继续后续流程");
        return routeBuildOrSkip(state);
    }

    private String routeBuildOrSkip(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        CodeGenTypeEnum generationType = context.getGenerationType();
        // HTML 和 MULTI_FILE 类型不需要构建，直接结束
        if (generationType == CodeGenTypeEnum.HTML || generationType == CodeGenTypeEnum.MULTI_FILE) {
            return "skip_build";
        }
        // VUE_PROJECT 需要构建
        return "build";
    }

    /**
     * 执行代码生成工作流
     */
    public WorkflowContext executeWorkflow(String originalPrompt){
        var workflow = createWorkflow();
        //初始化 WorkflowContext
        var initContext = buildInitialContext(originalPrompt, 0L, null, null);

        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("工作流图:\n{}", graph.content());
        log.info("开始执行代码生成工作流");

        WorkflowContext finalContext = null;
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initContext))) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            // 显示当前状态
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepCounter++;
        }
        log.info("代码生成工作流执行完成！");
        return finalContext;
    }

    /**
     * 执行代码生成工作流(Flux 流式输出版本)
     */
    public Flux<String> executeWorkflowWithFlux(String originalPrompt){
        return Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                try{
                    var workflow = createWorkflow();
                    //初始化 WorkflowContext
                    var initContext = buildInitialContext(originalPrompt, 0L, null, null);
                    sink.next(formatSseEvent("workflow_start", Map.of(
                            "message", "开始执行代码生成工作流",
                            "originalPrompt", originalPrompt
                    )));
                    GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                    log.info("工作流图:\n{}", graph.content());

                    int stepCounter = 1;
                    for (NodeOutput<MessagesState<String>> step : workflow.stream(
                            Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initContext))) {
                        log.info("--- 第 {} 步完成 ---", stepCounter);
                        // 显示当前状态
                        WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                        if (currentContext != null) {
                            sink.next(formatSseEvent("step_complete", Map.of(
                                    "stepNumber", stepCounter,
                                    "currentStep", currentContext.getCurrentStep()
                            )));
                            log.info("当前步骤上下文: {}", currentContext);
                        }
                        stepCounter++;
                    }
                    sink.next(formatSseEvent("workflow_complete", Map.of(
                            "message", "代码生成工作流执行完成！"
                    )));
                    log.info("代码生成工作流执行完成！");
                    sink.complete();
                } catch (Exception e) {
                    log.error("工作流执行失败: {}", e.getMessage(), e);
                    sink.next(formatSseEvent("workflow_error", Map.of(
                            "error", e.getMessage(),
                            "message", "工作流执行失败"
                    )));
                    sink.error(e);
                }
            });
        });
    }

    /**
     * 执行代码生成工作流(SSE 流式输出版本)
     */
    public SseEmitter executeWorkflowWithSse(String originalPrompt){
            SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
            Thread.startVirtualThread(() -> {
                try{
                    var workflow = createWorkflow();
                    //初始化 WorkflowContext
                    var initContext = buildInitialContext(originalPrompt, 1L, null, null);
                    sendSseEvent(emitter, "workflow_start", Map.of(
                            "message", "开始执行代码生成工作流",
                            "originalPrompt", originalPrompt
                    ));
                    GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                    log.info("工作流图:\n{}", graph.content());

                    int stepCounter = 1;
                    for (NodeOutput<MessagesState<String>> step : workflow.stream(
                            Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initContext))) {
                        log.info("--- 第 {} 步完成 ---", stepCounter);
                        // 显示当前状态
                        WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                        if (currentContext != null) {
                            sendSseEvent(emitter, "step_complete", Map.of(
                                    "stepNumber", stepCounter,
                                    "currentStep", currentContext.getCurrentStep()
                            ));
                            log.info("当前步骤上下文: {}", currentContext);
                        }
                        stepCounter++;
                    }
                    sendSseEvent(emitter, "workflow_complete", Map.of(
                            "message", "代码生成工作流执行完成！"
                    ));
                    log.info("代码生成工作流执行完成！");
                    emitter.complete();
                } catch (Exception e) {
                    log.error("工作流执行失败: {}", e.getMessage(), e);
                    sendSseEvent(emitter, "workflow_error", Map.of(
                            "error", e.getMessage(),
                            "message", "工作流执行失败"
                    ));
                    emitter.completeWithError(e);
                }
            });
            return emitter;
    }

    private WorkflowContext buildInitialContext(String originalPrompt,
                                                Long appId,
                                                CodeGenTypeEnum codeGenTypeEnum,
                                                Consumer<String> streamConsumer) {
        return buildInitialContext(originalPrompt, appId, codeGenTypeEnum, streamConsumer, null);
    }

    private WorkflowContext buildInitialContext(String originalPrompt,
                                                Long appId,
                                                CodeGenTypeEnum codeGenTypeEnum,
                                                Consumer<String> streamConsumer,
                                                String streamSessionId) {
        WorkflowContext context = WorkflowContext.builder()
                .appId(appId)
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .generationType(codeGenTypeEnum)
                .streamSessionId(streamSessionId)
                .build();
        context.setStreamConsumer(streamConsumer);
        return context;
    }

    /**
     * 格式化 SSE 事件的辅助方法
     */
    private String formatSseEvent(String eventType, Object data) {
        try {
            String jsonData = JSONUtil.toJsonStr(data);
            return "event: " + eventType + "\ndata: " + jsonData + "\n\n";
        } catch (Exception e) {
            log.error("格式化 SSE 事件失败: {}", e.getMessage(), e);
            return "event: error\ndata: {\"error\":\"格式化失败\"}\n\n";
        }
    }

    /**
     * 发送 SSE 事件的辅助方法
     */
    private void sendSseEvent(SseEmitter emitter, String eventType, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventType)
                    .data(data));
        } catch (IOException e) {
            log.error("发送 SSE 事件失败: {}", e.getMessage(), e);
        }
    }


    /**
     * 执行代码生成工作流 for 客户对话
     * 仅传递代码生成分片，不输出工作流等信息给客户
     */
    public Flux<String> executeWorkflowForUserChat(String originalPrompt, Long appId, CodeGenTypeEnum codeGenTypeEnum){
        return Flux.create(sink -> {
            Thread.startVirtualThread(() -> {
                String streamSessionId = UUID.randomUUID().toString();
                try{
                    var workflow = createWorkflow();
                    AtomicInteger forwardedChunkCount = new AtomicInteger(0);
                    Consumer<String> chunkConsumer = chunk -> {
                        forwardedChunkCount.incrementAndGet();
                        sink.next(chunk);
                    };
                    workflowStreamConsumerRegistry.register(streamSessionId, chunkConsumer);
                    //初始化 WorkflowContext
                    var initContext = buildInitialContext(
                            originalPrompt,
                            appId,
                            codeGenTypeEnum,
                            chunkConsumer,
                            streamSessionId
                    );

                    for (NodeOutput<MessagesState<String>> step : workflow.stream(
                            Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initContext))) {
                        // 用户对话中不额外输出工作流步骤，避免污染最终回复
                    }
                    log.info("为客户聊天定制的代码生成工作流执行完成！");
                    sink.complete();
                } catch (Exception e) {
                    log.error("为客户聊天定制的代码生成工作流执行失败: {}", e.getMessage(), e);
                    sink.error(e);
                } finally {
                    workflowStreamConsumerRegistry.remove(streamSessionId);
                }
            });
        });
    }
}

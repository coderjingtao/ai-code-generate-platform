package dev.jingtao.aicodebackend.controller;

import dev.jingtao.aicodebackend.langgraph4j.CodeGenWorkflow;
import dev.jingtao.aicodebackend.langgraph4j.state.WorkflowContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

/**
 * 工作流 SSE 控制器
 * 演示 LangGraph4j 工作流的流式输出功能
 */
@RestController
@RequestMapping("/workflow")
@Slf4j
public class WorkflowSseController {

    @Resource
    private CodeGenWorkflow codeGenWorkflow;

    /**
     * 同步执行工作流
     */
    @PostMapping("/execute")
    public WorkflowContext executeWorkflow(@RequestParam String userPrompt){
        log.info("收到[同步工作流]执行请求: {}", userPrompt);
        return codeGenWorkflow.executeWorkflow(userPrompt);
    }

    /**
     * Flux 流式执行工作流
     */
    @GetMapping(value = "/execute-flux", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> executeWorkflowWithFlex(@RequestParam String userPrompt){
        log.info("收到[Flux工作流]执行请求: {}", userPrompt);
        return codeGenWorkflow.executeWorkflowWithFlux(userPrompt);
    }

    /**
     * SSE 流式执行工作流
     */
    @GetMapping(value = "/execute-sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executeWorkflowWithSse(@RequestParam String userPrompt){
        log.info("收到[SSE工作流]执行请求: {}", userPrompt);
        return codeGenWorkflow.executeWorkflowWithSse(userPrompt);
    }
}

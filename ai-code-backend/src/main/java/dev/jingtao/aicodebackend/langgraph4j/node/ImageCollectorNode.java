package dev.jingtao.aicodebackend.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import dev.jingtao.aicodebackend.langgraph4j.ai.ImageCollectionPlanService;
import dev.jingtao.aicodebackend.langgraph4j.model.ImageCollectionPlan;
import dev.jingtao.aicodebackend.langgraph4j.model.ImageResource;
import dev.jingtao.aicodebackend.langgraph4j.state.WorkflowContext;
import dev.jingtao.aicodebackend.langgraph4j.tools.ImageSearchTool;
import dev.jingtao.aicodebackend.langgraph4j.tools.LogoSearchTool;
import dev.jingtao.aicodebackend.langgraph4j.tools.MermaidDiagramTool;
import dev.jingtao.aicodebackend.langgraph4j.tools.UndrawIllustrationTool;
import dev.jingtao.aicodebackend.utils.PromptUtil;
import dev.jingtao.aicodebackend.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集节点
 *
 * 使用AI进行工具调用，收集不同类型的图片
 */
@Slf4j
public class ImageCollectorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("开始执行节点: 图片收集");
            String originalPrompt = context.getOriginalPrompt();

            // 修改场景跳过图片收集，避免引入大量无关素材导致提示词膨胀
            if(PromptUtil.isModificationScenario(originalPrompt)){
                context.setCurrentStep("图片收集(已跳过-修改场景)");
                context.setImageList(List.of());
                return WorkflowContext.saveContext(context);
            }

            List<ImageResource> collectedImages = new ArrayList<>();

            try{
                // 1. 获取AI图片收集计划
                var planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                log.info("获取到图片收集计划，开始并发执行");

                // 2. 并发执行各种图片收集任务
                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
                // 并发执行内容图片搜索
                if(CollUtil.isNotEmpty(plan.getContentImageTasks())){
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    for(var task : plan.getContentImageTasks()){
                        futures.add(CompletableFuture.supplyAsync(() ->
                                imageSearchTool.searchContentImages(task.query())));
                    }
                }
                // 并发执行插画图片搜索
                if (CollUtil.isNotEmpty(plan.getIllustrationTasks())) {
                    UndrawIllustrationTool illustrationTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    for (var task : plan.getIllustrationTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                illustrationTool.searchIllustrations(task.query())));
                    }
                }
                // 并发执行架构图生成
                if (CollUtil.isNotEmpty(plan.getDiagramTasks())) {
                    MermaidDiagramTool diagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    for (var task : plan.getDiagramTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                diagramTool.generateMermaidDiagram(task.mermaidCode(), task.description())));
                    }
                }
                // 并发执行Logo搜索
                if (CollUtil.isNotEmpty(plan.getLogoTasks())) {
                    LogoSearchTool logoTool = SpringContextUtil.getBean(LogoSearchTool.class);
                    for (var task : plan.getLogoTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                logoTool.searchLogos(task.query())));
                    }
                }
                // 等待所有任务完成并收集结果
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                allTasks.join();
                // 收集所有结果
                for(CompletableFuture<List<ImageResource>> future : futures){
                    List<ImageResource> images = future.get();
                    if(CollUtil.isNotEmpty(images)){
                        collectedImages.addAll(images);
                    }
                }
                log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());
            } catch (Exception e) {
                log.error("图片收集失败：{}", e.getMessage(), e);
            }
            // 更新状态
            context.setCurrentStep("图片收集");
            context.setImageList(collectedImages);
            return WorkflowContext.saveContext(context);
        });
    }
}


package dev.jingtao.aicodebackend.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import dev.jingtao.aicodebackend.langgraph4j.model.ImageResource;
import dev.jingtao.aicodebackend.langgraph4j.state.WorkflowContext;
import dev.jingtao.aicodebackend.utils.PromptUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class PromptEnhancerNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("开始执行节点: 提示词增强");

            String originalPrompt = context.getOriginalPrompt();
            // 修改场景直接透传原始提示词，避免重链路引入大段素材
            if(PromptUtil.isModificationScenario(originalPrompt)){
                context.setCurrentStep("提示词增强(已跳过-修改场景)");
                context.setEnhancedPrompt(originalPrompt);
                return WorkflowContext.saveContext(context);
            }

            String imageListStr = context.getImageListStr();
            List<ImageResource> imageList = context.getImageList();
            // 构建增强后的提示词
            StringBuilder enhancedPromptBuilder = new StringBuilder(originalPrompt);
            // 如果有图片资源，则添加图片信息
            if(CollUtil.isNotEmpty(imageList) || StrUtil.isNotBlank(imageListStr)){
                enhancedPromptBuilder.append("\\n\\n## 可用素材资源\\n");
                enhancedPromptBuilder.append("请在生成网站使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。\\n");
                if(CollUtil.isNotEmpty(imageList)){
                    for(ImageResource image : imageList){
                        enhancedPromptBuilder.append("- ")
                                .append(image.getCategory().getText())
                                .append("：")
                                .append(image.getDescription())
                                .append("（")
                                .append(image.getUrl())
                                .append("）\n");
                    }
                }else{
                    enhancedPromptBuilder.append(imageListStr);
                }
            }

            // 更新状态
            context.setCurrentStep("提示词增强");
            context.setEnhancedPrompt(enhancedPromptBuilder.toString());
            log.info("提示词增强完成，增强后长度: {} 字符", enhancedPromptBuilder.length());
            return WorkflowContext.saveContext(context);
        });
    }
}


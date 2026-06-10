package dev.jingtao.aicodebackend.langgraph4j.ai;

import dev.jingtao.aicodebackend.langgraph4j.model.ImageCollectionPlan;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 图片收集规划服务
 */
public interface ImageCollectionPlanService {

    /**
     * 根据用户提示词分析需要收集的图片类型和参数
     */
    @SystemMessage(fromResource = "prompt/image-collection-plan-system-prompt.txt")
    @UserMessage("{{userPrompt}}")
    ImageCollectionPlan planImageCollection(@V("userPrompt") String userPrompt);
}

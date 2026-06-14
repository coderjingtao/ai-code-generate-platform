package dev.jingtao.aicodebackend.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import dev.jingtao.aicodebackend.service.AppFileService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 */
@Component
@Slf4j
public class FileWriteTool extends BaseTool{

    @Resource
    private AppFileService appFileService;

    @Tool("写入文件到指定路径")
    public String writeFile(
            @P("文件相对路径")
            String relativeFilePath,
            @P("要写入文件到内容")
            String content,
            @ToolMemoryId
            Long appId) {
        try{
            // 统一通过 AppFileService 落盘：自带路径穿越校验与目录布局，文件类型固定为 Vue 工程
            appFileService.writeFile(appId, CodeGenTypeEnum.VUE_PROJECT, relativeFilePath, content);
            log.info("成功写入文件: appId={}, path={}", appId, relativeFilePath);
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        }catch (Exception e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "writeFile";
    }

    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");

        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        return String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getDisplayName(), relativeFilePath, suffix, content);
    }
}

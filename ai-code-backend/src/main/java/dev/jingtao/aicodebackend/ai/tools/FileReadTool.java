package dev.jingtao.aicodebackend.ai.tools;

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
 * 文件读取工具
 * 支持 AI 通过工具调用的方式读取文件内容
 */
@Component
@Slf4j
public class FileReadTool extends BaseTool{

    @Resource
    private AppFileService appFileService;

    @Tool("Read the content of the file at the specified path")
    public String readFile(
            @P("Relative file path")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        try {
            String content = appFileService.readFileContent(appId, CodeGenTypeEnum.VUE_PROJECT, relativeFilePath);
            if (content == null) {
                return "Error: file does not exist or is not a file - " + relativeFilePath;
            }
            return content;
        } catch (Exception e) {
            String errorMessage = "Failed to read file: " + relativeFilePath + ", error: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "readFile";
    }

    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}

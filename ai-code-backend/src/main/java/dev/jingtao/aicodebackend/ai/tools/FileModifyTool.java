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
 * 文件修改工具
 * 支持 AI 通过工具调用的方式修改文件内容
 */
@Component
@Slf4j
public class FileModifyTool extends BaseTool{

    @Resource
    private AppFileService appFileService;

    @Tool("Modify file content by replacing the specified old content with new content")
    public String modifyFile(
            @P("Relative file path")
            String relativeFilePath,
            @P("The old content to be replaced")
            String oldContent,
            @P("The new content to replace with")
            String newContent,
            @ToolMemoryId Long appId
    ) {
        try {
            String originalContent = appFileService.readFileContent(appId, CodeGenTypeEnum.VUE_PROJECT, relativeFilePath);
            if (originalContent == null) {
                return "Error: file does not exist or is not a file - " + relativeFilePath;
            }
            if (!originalContent.contains(oldContent)) {
                return "Warning: content to replace not found in file, file not modified - " + relativeFilePath;
            }
            String modifiedContent = originalContent.replace(oldContent, newContent);
            if (originalContent.equals(modifiedContent)) {
                return "Info: file content unchanged after replacement - " + relativeFilePath;
            }
            appFileService.writeFile(appId, CodeGenTypeEnum.VUE_PROJECT, relativeFilePath, modifiedContent);
            log.info("成功修改文件: appId={}, path={}", appId, relativeFilePath);
            return "File modified successfully: " + relativeFilePath;
        } catch (Exception e) {
            String errorMessage = "Failed to modify file: " + relativeFilePath + ", error: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "modifyFile";
    }

    @Override
    public String getDisplayName() {
        return "修改文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        String oldContent = arguments.getStr("oldContent");
        String newContent = arguments.getStr("newContent");
        // 显示对比内容
        return String.format("""
                [工具调用] %s %s

                替换前：
                ```
                %s
                ```

                替换后：
                ```
                %s
                ```
                """, getDisplayName(), relativeFilePath, oldContent, newContent);
    }

}

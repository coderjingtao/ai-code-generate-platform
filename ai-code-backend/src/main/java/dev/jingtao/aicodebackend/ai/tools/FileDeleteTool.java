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
 * 文件删除工具
 * 支持 AI 通过工具调用的方式删除文件
 */
@Component
@Slf4j
public class FileDeleteTool extends BaseTool{

    @Resource
    private AppFileService appFileService;

    @Tool("Delete the file at the specified path")
    public String deleteFile(
            @P("Relative file path")
            String relativeFilePath,
            @ToolMemoryId Long appId
    ) {
        try {
            // 安全检查：避免删除重要文件（基于文件名）
            String fileName = FileUtil.getName(relativeFilePath);
            if (isImportantFile(fileName)) {
                return "Error: deleting important files is not allowed - " + fileName;
            }
            // 统一通过 AppFileService 删除：自带路径穿越校验与目录布局
            boolean deleted = appFileService.deleteFile(appId, CodeGenTypeEnum.VUE_PROJECT, relativeFilePath);
            if (!deleted) {
                return "Warning: file does not exist, nothing to delete - " + relativeFilePath;
            }
            log.info("成功删除文件: appId={}, path={}", appId, relativeFilePath);
            return "File deleted successfully: " + relativeFilePath;
        } catch (Exception e) {
            String errorMessage = "Failed to delete file: " + relativeFilePath + ", error: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    /**
     * 判断是否是重要文件，不允许删除
     */
    private boolean isImportantFile(String fileName) {
        String[] importantFiles = {
                "package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
                "vite.config.js", "vite.config.ts", "vue.config.js",
                "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json",
                "index.html", "main.js", "main.ts", "App.vue", ".gitignore", "README.md"
        };
        for (String important : importantFiles) {
            if (important.equalsIgnoreCase(fileName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getToolName() {
        return "deleteFile";
    }

    @Override
    public String getDisplayName() {
        return "删除文件";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        return String.format(" [工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}

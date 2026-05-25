package dev.jingtao.aicodebackend.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.exception.ThrowUtils;
import dev.jingtao.aicodebackend.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;

@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {

    /**
     * 需要过滤的文件和目录名称
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 需要过滤的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );

    @Override
    public void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response) {
        // 校验
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR, "projectPath must not be blank");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR, "downloadFileName must not be blank");
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.NOT_FOUND_ERROR, "projectPath not found");
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR, "projectPath must be a directory");
        // 设置Http响应头
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.addHeader("Content-Disposition", String.format("attachment; filename=\"%s.zip\"", downloadFileName));
        // 定义文件过滤器
        FileFilter fileFilter = file -> isPathAllowed(projectDir.toPath(), file.toPath());
        // 压缩
        try{
            ZipUtil.zip(response.getOutputStream(), StandardCharsets.UTF_8, false, fileFilter, projectDir);
        } catch (IOException e) {
            log.error("zip project failed: {}", projectPath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "zip project failed");
        }
    }

    /**
     * 校验文件夹或文件是否允许包含在项目压缩包中
     * @param projectPath 项目根目录
     * @param filePath 文件夹或文件的路径
     * @return 是否文件允许打包进去
     */
    private boolean isPathAllowed(Path projectPath, Path filePath){
        // 获取文件在项目中的相对路径
        Path fileRelativePath = projectPath.relativize(filePath);
        // 检查文件是否符合要求
        for(Path path : fileRelativePath){
            String fileName = path.toString();
            if(IGNORED_NAMES.contains(fileName)){
                return false;
            }
            if(IGNORED_EXTENSIONS.stream().anyMatch(ext -> fileName.toLowerCase().endsWith(ext))){
                return false;
            }
        }
        return true;
    }
}

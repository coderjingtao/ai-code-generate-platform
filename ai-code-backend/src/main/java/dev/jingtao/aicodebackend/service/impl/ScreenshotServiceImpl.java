package dev.jingtao.aicodebackend.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.exception.ThrowUtils;
import dev.jingtao.aicodebackend.manager.R2StorageManger;
import dev.jingtao.aicodebackend.service.ScreenshotService;
import dev.jingtao.aicodebackend.utils.WebScreenshotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ScreenshotServiceImpl implements ScreenshotService {

    private final R2StorageManger r2StorageManger;

    @Override
    public String takeAndUploadScreenshot(String webUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "Web url must not be blank");
        // 1.生成本地截图
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.OPERATION_ERROR, "Local screenshot failed");
        // 2.上传到对象存储
        try {
            String screenshotUrl = uploadScreenshotToR2(localScreenshotPath);
            ThrowUtils.throwIf(StrUtil.isBlank(screenshotUrl), ErrorCode.OPERATION_ERROR, "Upload screenshot to R2 failed");
            log.info("Upload screenshot to R2 success, url: {}", screenshotUrl);
            return screenshotUrl;
        } finally {
            // 3.清理本地文件
            cleanupLocalScreenshots(localScreenshotPath);
        }
    }


    private String uploadScreenshotToR2(String localScreenshotPath){
        if(StrUtil.isBlank(localScreenshotPath)){
            return null;
        }
        File screenshotFile = new File(localScreenshotPath);
        if(!screenshotFile.exists()){
            log.error("Local screenshot file not found: {}", localScreenshotPath);
            return null;
        }
        // generate object key
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        String screenshotKey = generateScreenshotKey(fileName);
        return r2StorageManger.upload(screenshotKey, screenshotFile);
    }

    private String generateScreenshotKey(String fileName){
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("screenshots/%s/%s", datePath, fileName);
    }

    private void cleanupLocalScreenshots(String localScreenshotPath){
        File localScreenshotFile = new File(localScreenshotPath);
        if(localScreenshotFile.exists()){
            File parentDir = localScreenshotFile.getParentFile();
            FileUtil.del(parentDir);
            log.info("Local screenshot file deleted: {}", localScreenshotPath);
        }
    }
}

package dev.jingtao.aicodebackend.core;

import dev.jingtao.aicodebackend.ai.AiCodeGenerateService;
import dev.jingtao.aicodebackend.ai.model.HtmlCodeResult;
import dev.jingtao.aicodebackend.ai.model.MultiFileCodeResult;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.exception.ThrowUtils;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGenerateService aiCodeGenerateService;

    public File generateAndSaveCode(String userPrompt, CodeGenTypeEnum codeGenTypeEnum) {
        ThrowUtils.throwIf(codeGenTypeEnum == null, new BusinessException(ErrorCode.SYSTEM_ERROR, "生成代码类型为空"));
        return switch (codeGenTypeEnum){
            case HTML -> generateAndSaveHtmlCode(userPrompt);
            case MULTI_FILE -> generateAndSaveMultiFileCode(userPrompt);
        };
    }

    /**
     * 根据用户提示词，生成HTML模式的代码并保存
     * @param userPrompt 用户提示词
     * @return 代码保存的目录
     */
    private File generateAndSaveHtmlCode(String userPrompt){
        HtmlCodeResult htmlCodeResult = aiCodeGenerateService.generateHtmlCode(userPrompt);
        return CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
    }

    /**
     * 根据用户提示词，生成多文件模式的代码并保存
     * @param userPrompt 用户提示词
     * @return 代码保存的目录
     */
    private File generateAndSaveMultiFileCode(String userPrompt){
        MultiFileCodeResult multiFileCodeResult = aiCodeGenerateService.generateMultiFileCode(userPrompt);
        return CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
    }
}

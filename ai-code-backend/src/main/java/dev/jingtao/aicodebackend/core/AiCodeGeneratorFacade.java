package dev.jingtao.aicodebackend.core;

import dev.jingtao.aicodebackend.ai.AiCodeGenerateService;
import dev.jingtao.aicodebackend.ai.model.HtmlCodeResult;
import dev.jingtao.aicodebackend.ai.model.MultiFileCodeResult;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.exception.ThrowUtils;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

@Service
@Slf4j
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

    public Flux<String> generateAndSaveCodeStream(String userPrompt, CodeGenTypeEnum codeGenTypeEnum){
        ThrowUtils.throwIf(codeGenTypeEnum == null, new BusinessException(ErrorCode.SYSTEM_ERROR, "生成代码类型为空"));
        return switch (codeGenTypeEnum){
            case HTML -> generateAndSaveHtmlCodeStream(userPrompt);
            case MULTI_FILE -> generateAndSaveMultiFileCodeStream(userPrompt);
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

    private Flux<String> generateAndSaveHtmlCodeStream(String userPrompt){
        Flux<String> result = aiCodeGenerateService.generateHtmlCodeStream(userPrompt);
        // 当流式返回生成代码完成后，再保存代码
        StringBuilder codeBuilder = new StringBuilder();
        return result
                .doOnNext(codeBuilder::append)
                .doOnComplete(() -> {
                    // 流式返回完成后保存代码
                    try{
                        String htmlCode = codeBuilder.toString();
                        HtmlCodeResult htmlCodeResult = CodeParser.parseHtmlCode(htmlCode);
                        //保存代码到文件
                        File savedDir = CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
                        log.info("HTML saved successfully: {}", savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("Error saving HTML code: {}", e.getMessage());
                    }
                });
    }

    private Flux<String> generateAndSaveMultiFileCodeStream(String userPrompt){
        Flux<String> result = aiCodeGenerateService.generateMultiFileCodeStream(userPrompt);
        StringBuilder codeBuilder = new StringBuilder();
        return result
                .doOnNext(codeBuilder::append)
                .doOnComplete(() -> {
                    try{
                        String multiFileCode = codeBuilder.toString();
                        MultiFileCodeResult multiFileCodeResult = CodeParser.parseMultiFileCode(multiFileCode);
                        File savedDir = CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
                        log.info("Multi-file saved successfully: {}", savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("Error saving multi-file code: {}", e.getMessage());
                    }
                });
    }
}

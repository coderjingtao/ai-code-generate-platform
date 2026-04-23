package dev.jingtao.aicodebackend.core;

import dev.jingtao.aicodebackend.ai.AiCodeGenerateService;
import dev.jingtao.aicodebackend.ai.model.HtmlCodeResult;
import dev.jingtao.aicodebackend.ai.model.MultiFileCodeResult;
import dev.jingtao.aicodebackend.core.parser.CodeParserExecutor;
import dev.jingtao.aicodebackend.core.saver.CodeFileSaverExecutor;
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

    /**
     * 统一入口：根据代码生成的类型，生成代码并保存到文件
     * @param userPrompt 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @return 文件保存的目录
     */
    public File generateAndSaveCode(String userPrompt, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        ThrowUtils.throwIf(codeGenTypeEnum == null, new BusinessException(ErrorCode.SYSTEM_ERROR, "生成代码类型为空"));
        return switch (codeGenTypeEnum){
            case HTML -> {
                HtmlCodeResult result = aiCodeGenerateService.generateHtmlCode(userPrompt);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenTypeEnum, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGenerateService.generateMultiFileCode(userPrompt);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenTypeEnum, appId);
            }
            default -> throw new IllegalArgumentException("Unsupported code generation type: " + codeGenTypeEnum);
        };
    }

    /**
     * 统一入口：根据代码生成的类型，生成代码并保存到文件（流式）
     * @param userPrompt 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @return 生成代码的实时流式字符串
     */
    public Flux<String> generateAndSaveCodeStream(String userPrompt, CodeGenTypeEnum codeGenTypeEnum, Long appId){
        ThrowUtils.throwIf(codeGenTypeEnum == null, new BusinessException(ErrorCode.SYSTEM_ERROR, "生成代码类型为空"));
        return switch (codeGenTypeEnum){
            case HTML -> {
                Flux<String> codeStream = aiCodeGenerateService.generateHtmlCodeStream(userPrompt);
                yield processCodeStream(codeStream, codeGenTypeEnum, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGenerateService.generateMultiFileCodeStream(userPrompt);
                yield processCodeStream(codeStream, codeGenTypeEnum, appId);
            }
            default -> throw new IllegalArgumentException("Unsupported code generation type: " + codeGenTypeEnum);
        };
    }

    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId){
        StringBuilder codeBuilder = new StringBuilder();
        return codeStream
                //实时收集代码片段
                .doOnNext(codeBuilder::append)
                //流式返回完成后保存代码
                .doOnComplete(() -> {
                    try{
                        String completedCode = codeBuilder.toString();
                        //使用执行器解析代码
                        Object parsedResult = CodeParserExecutor.executeParser(completedCode,codeGenType);
                        //使用执行器保存代码
                        File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult,codeGenType, appId);
                        log.info("{} mode files saved successfully: {}", codeGenType, savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("{} mode file saved failed, error: {}", codeGenType, e.getMessage(),e);
                    }
                });
    }
}

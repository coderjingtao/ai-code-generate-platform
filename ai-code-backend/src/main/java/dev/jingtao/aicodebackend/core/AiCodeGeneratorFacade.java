package dev.jingtao.aicodebackend.core;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import dev.jingtao.aicodebackend.ai.AiCodeGenerateServiceFactory;
import dev.jingtao.aicodebackend.ai.model.HtmlCodeResult;
import dev.jingtao.aicodebackend.ai.model.MultiFileCodeResult;
import dev.jingtao.aicodebackend.ai.model.message.AiResponseMessage;
import dev.jingtao.aicodebackend.ai.model.message.ThinkingMessage;
import dev.jingtao.aicodebackend.ai.model.message.ToolExecutedMessage;
import dev.jingtao.aicodebackend.ai.model.message.ToolRequestMessage;
import dev.jingtao.aicodebackend.constant.AppConstant;
import dev.jingtao.aicodebackend.core.builder.VueProjectBuilder;
import dev.jingtao.aicodebackend.core.parser.CodeParserExecutor;
import dev.jingtao.aicodebackend.core.saver.CodeFileSaverExecutor;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.exception.ThrowUtils;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.TokenStream;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 直接面对的是AI底层大模型LLM层面的处理
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGenerateServiceFactory aiCodeGenerateServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 统一入口：根据代码生成的类型，生成代码并保存到文件
     * @param userPrompt 用户提示词
     * @param codeGenTypeEnum 代码生成类型
     * @return 文件保存的目录
     */
    public File generateAndSaveCode(String userPrompt, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        ThrowUtils.throwIf(codeGenTypeEnum == null, new BusinessException(ErrorCode.SYSTEM_ERROR, "生成代码类型为空"));
        var aiService = aiCodeGenerateServiceFactory.getAiService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum){
            case HTML -> {
                HtmlCodeResult result = aiService.generateHtmlCode(userPrompt);
                yield CodeFileSaverExecutor.executeSaver(result, codeGenTypeEnum, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiService.generateMultiFileCode(userPrompt);
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
        var aiService = aiCodeGenerateServiceFactory.getAiService(appId, codeGenTypeEnum);
        return switch (codeGenTypeEnum){
            case HTML -> {
                Flux<String> codeStream = aiService.generateHtmlCodeStream(userPrompt);
                yield processCodeStream(codeStream, codeGenTypeEnum, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiService.generateMultiFileCodeStream(userPrompt);
                yield processCodeStream(codeStream, codeGenTypeEnum, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiService.generateVueProjectCodeStream(appId, userPrompt);
                yield processTokenStream(tokenStream, appId);
            }
            default -> throw new IllegalArgumentException("Unsupported code generation type: " + codeGenTypeEnum);
        };
    }

    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId){
        if (CodeGenTypeEnum.VUE_PROJECT.equals(codeGenType)) {
            return codeStream;
        }
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

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     * 它是面向AI底层模型/工具执行层面的回调，处理的是 TokenStream 回调状态
     * [核心职责]：
     * 把 LangChain4j 的 TokenStream 事件，根据回调状态包装成项目内部统一的 JSON 消息流
     *
     * @param tokenStream LangChain4j 的 TokenStream 对象
     * @param appId       应用 ID
     * @return 系统内部的JSON Message流
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId){
        return Flux.create(sink -> {
            AtomicInteger emittedChunkCount = new AtomicInteger(0);

            TokenStream configuredStream = tokenStream
                    // AI普通文本分片
                    .onPartialResponse(partialResponse -> {
                        AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                        sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                        emittedChunkCount.incrementAndGet();
                    })
                    // 工具即将执行
                    .beforeToolExecution(beforeToolExecution -> {
                        ToolRequestMessage toolRequestMessage = new ToolRequestMessage(beforeToolExecution);
                        sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                        emittedChunkCount.incrementAndGet();
                    })
                    // 工具执行完成
                    .onToolExecuted(toolExecution -> {
                        ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                        sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                        emittedChunkCount.incrementAndGet();
                    })
                    // 模型完整响应结束
                    .onCompleteResponse(response -> {
                        // 兜底：某些模型实现可能只触发 onCompleteResponse，不触发 onPartialResponse 回调
                        if (emittedChunkCount.get() == 0
                                && response != null
                                && response.aiMessage() != null
                                && StrUtil.isNotBlank(response.aiMessage().text())) {
                            sink.next(JSONUtil.toJsonStr(new AiResponseMessage(response.aiMessage().text())));
                            emittedChunkCount.incrementAndGet();
                        }
                        log.info("TokenStream 完成，appId={}, emittedChunkCount={}", appId, emittedChunkCount.get());
                        // 执行同步构建 Vue 项目，确保预览时项目已就绪
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
                        vueProjectBuilder.buildProject(projectPath);
                        sink.complete();
                    })
                    // 模型出现异常
                    .onError(error -> {
                        log.error("AI response error: {}", error.getMessage(), error);
                        sink.error(error);
                    });
            // 仅当LLM支持深度思考时，才会收到思考分片
            try {
                // 思考内容分片
                configuredStream = configuredStream.onPartialThinking(partialThinking -> {
                    ThinkingMessage thinkingMessage = new ThinkingMessage(partialThinking);
                    sink.next(JSONUtil.toJsonStr(thinkingMessage));
                    emittedChunkCount.incrementAndGet();
                });
            } catch (UnsupportedOperationException e) {
                log.debug("当前 TokenStream 实现不支持 onPartialThinking，已降级为普通流式输出");
            }
            configuredStream.start();
        });
    }
}

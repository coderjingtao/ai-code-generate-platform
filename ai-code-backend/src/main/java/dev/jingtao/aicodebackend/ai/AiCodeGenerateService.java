package dev.jingtao.aicodebackend.ai;

import dev.jingtao.aicodebackend.ai.model.HtmlCodeResult;
import dev.jingtao.aicodebackend.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

import dev.langchain4j.service.V;

public interface AiCodeGenerateService {

    String generateCode(String userPrompt);

    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    @UserMessage("{{userPrompt}}")
    HtmlCodeResult generateHtmlCode(@V("userPrompt") String userPrompt,
                                    @V("languageRequirement") String languageRequirement);

    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    @UserMessage("{{userPrompt}}")
    MultiFileCodeResult generateMultiFileCode(@V("userPrompt") String userPrompt,
                                              @V("languageRequirement") String languageRequirement);

    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    @UserMessage("{{userPrompt}}")
    Flux<String> generateHtmlCodeStream(@V("userPrompt") String userPrompt,
                                        @V("languageRequirement") String languageRequirement);

    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    @UserMessage("{{userPrompt}}")
    Flux<String> generateMultiFileCodeStream(@V("userPrompt") String userPrompt,
                                             @V("languageRequirement") String languageRequirement);

    @SystemMessage(fromResource = "prompt/codegen-vue-project-system-prompt.txt")
    @UserMessage("{{userPrompt}}")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId,
                                             @V("userPrompt") String userPrompt,
                                             @V("languageRequirement") String languageRequirement);
}

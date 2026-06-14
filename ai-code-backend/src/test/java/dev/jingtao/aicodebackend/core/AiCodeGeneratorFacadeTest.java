package dev.jingtao.aicodebackend.core;

import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.util.List;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCodeStream(){
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("做个最简单的登录页", CodeGenTypeEnum.MULTI_FILE,112L);
        //阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        Assertions.assertNotNull(result);
        String combinedCode = String.join("", result);
        System.out.println(combinedCode);
        Assertions.assertNotNull(combinedCode);
    }

    @Test
    void generateVueProject(){
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream("做个简单的TODO List任务记录网站，总代码量不要超过200行", CodeGenTypeEnum.VUE_PROJECT,112L);
        //阻塞等待所有数据收集完成
        List<String> result = codeStream.collectList().block();
        Assertions.assertNotNull(result);
        String combinedCode = String.join("", result);
        System.out.println(combinedCode);
        Assertions.assertNotNull(combinedCode);
    }
}
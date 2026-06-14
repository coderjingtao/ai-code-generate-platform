package dev.jingtao.aicodebackend.ai;

import dev.jingtao.aicodebackend.model.enums.CodeGenModeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Slf4j
public class AiCodeGenModeRoutingServiceTest {

    @Resource
    private AiCodeGenModeRoutingService aiCodeGenModeRoutingService;

    @Test
    public void testRouteCodeGenType(){
        String userPrompt = "设计一个婚礼请柬网站";
        CodeGenModeEnum mode = aiCodeGenModeRoutingService.routeCodeGenMode(userPrompt);
        log.info("用户需求: {} -> {}", userPrompt, mode.getValue());
        userPrompt = "做一个2046小游戏";
        mode = aiCodeGenModeRoutingService.routeCodeGenMode(userPrompt);
        log.info("用户需求: {} -> {}", userPrompt, mode.getValue());
        userPrompt = "做一个电商管理系统，包含用户管理、商品管理、订单管理，需要路由和状态管理";
        mode = aiCodeGenModeRoutingService.routeCodeGenMode(userPrompt);
        log.info("用户需求: {} -> {}", userPrompt, mode.getValue());
    }
}

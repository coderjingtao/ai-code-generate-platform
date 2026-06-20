package dev.jingtao.aicodebackend.config;

import cn.hutool.core.util.StrUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 关于对话记忆存储的Redis配置
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisConfig {

    private String host;
    private int port;
    private String password;
    private long ttl;

    @Bean
    public RedisChatMemoryStore redisChatMemoryStore(){
        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .password(password)
                .ttl(ttl);

        if(StrUtil.isNotBlank(password)){
            builder.user("default");
        }
        return builder.build();
    }
}

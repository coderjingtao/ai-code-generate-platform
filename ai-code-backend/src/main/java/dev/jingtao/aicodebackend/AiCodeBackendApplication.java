package dev.jingtao.aicodebackend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)
@SpringBootApplication
@MapperScan("dev.jingtao.aicodebackend.mapper")
public class AiCodeBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodeBackendApplication.class, args);
    }

}

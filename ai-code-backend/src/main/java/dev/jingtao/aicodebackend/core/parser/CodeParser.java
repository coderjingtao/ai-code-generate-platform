package dev.jingtao.aicodebackend.core.parser;

/**
 * 代码解析器的策略模式（Strategy Pattern）接口
 * @param <T> 策略
 */
public interface CodeParser<T> {

    /**
     * 解析代码的内容
     * @param codeContent 原始代码内容
     * @return 解析后的结果
     */
    T parse(String codeContent);
}

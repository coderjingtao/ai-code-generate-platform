package dev.jingtao.aicodebackend.core.parser;

import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;

/**
 * 代码解析执行器
 * 根据代码生成的类型执行相应的解析逻辑
 */
public class CodeParserExecutor {

    private static final HtmlCodeParser htmlCodeParser = new HtmlCodeParser();
    private static final MultiFileCodeParser multiFileCodeParser = new MultiFileCodeParser();

    /**
     * 执行代码解析
     * @param codeContent 代码内容
     * @param codeGenTypeEnum 代码生成类型
     * @return 解析结果（HtmlCodeResult or MultiFileCodeResult）
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenTypeEnum) {
        return switch (codeGenTypeEnum){
            case HTML -> htmlCodeParser.parse(codeContent);
            case MULTI_FILE -> multiFileCodeParser.parse(codeContent);
            default -> throw new IllegalArgumentException("Unsupported code generation type: " + codeGenTypeEnum);
        };
    }
}

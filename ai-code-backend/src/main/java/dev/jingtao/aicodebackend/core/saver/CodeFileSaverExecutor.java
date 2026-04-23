package dev.jingtao.aicodebackend.core.saver;

import dev.jingtao.aicodebackend.ai.model.HtmlCodeResult;
import dev.jingtao.aicodebackend.ai.model.MultiFileCodeResult;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 代码文件保存执行器
 * 根据代码生成的类型，执行相应的保存逻辑
 */
public class CodeFileSaverExecutor {

    private static final HtmlCodeFileSaver htmlCodeFileSaver = new HtmlCodeFileSaver();
    private static final MultiFileCodeFileSaver multiFileCodeFileSaver = new MultiFileCodeFileSaver();

    /**
     * 根据代码生成的类型，执行不同的代码保存器
     * @param codeResult 代码生成的结果
     * @param codeGenType 代码生成的类型
     * @return 保存文件的目录
     */
    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType, Long appId){
        return switch (codeGenType){
            case HTML -> htmlCodeFileSaver.saveCode((HtmlCodeResult) codeResult, appId);
            case MULTI_FILE -> multiFileCodeFileSaver.saveCode((MultiFileCodeResult) codeResult, appId);
            default -> throw new IllegalArgumentException("Unsupported code generation type: " + codeGenType);
        };
    }
}

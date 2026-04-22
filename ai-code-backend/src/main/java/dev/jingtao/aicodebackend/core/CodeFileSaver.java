package dev.jingtao.aicodebackend.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import dev.jingtao.aicodebackend.ai.model.HtmlCodeResult;
import dev.jingtao.aicodebackend.ai.model.MultiFileCodeResult;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class CodeFileSaver {

    // 文件保存的根目录
    private static final String FILE_SAVE_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 保存生成的 HTML 模式的代码
     * @param result 生成的html单页面代码
     * @return 保存的目录
     */
    public static File saveHtmlCodeResult(HtmlCodeResult result){
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.HTML.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        return new File(baseDirPath);
    }

    /**
     * 保存生成的 多文件 模式的代码
     * @param result 生成的多文件代码
     * @return 保存的目录
     */
    public static File saveMultiFileCodeResult(MultiFileCodeResult result){
        String baseDirPath = buildUniqueDir(CodeGenTypeEnum.MULTI_FILE.getValue());
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
        return new File(baseDirPath);
    }

    /**
     * 为每次生成的代码，构建唯一的目录路径,例如: temp/code_output/html_SnowflakeID
     * @param codeGenType 代码生成的类型
     * @return 唯一的代码路径
     */
    private static String buildUniqueDir(String codeGenType){
        String uniqueDir = StrUtil.format("{}_{}", codeGenType, IdUtil.getSnowflakeNextIdStr());
        String dir = FILE_SAVE_ROOT_DIR + File.separator + uniqueDir;
        FileUtil.mkdir(dir);
        return dir;
    }

    /**
     * 写入单个文件
     * @param dir 文件目录
     * @param fileName 文件名
     * @param content 文件内容
     */
    private static void writeToFile(String dir, String fileName, String content){
        String filePath = dir + File.separator + fileName;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }
}

package dev.jingtao.aicodebackend.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import dev.jingtao.aicodebackend.constant.AppConstant;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器 - 模版方法模式（Template Method Pattern）
 * @param <T> 代码解析后的结果类型
 */
public abstract class CodeFileSaverTemplate<T> {

    // 文件保存的根目录
    private static final String FILE_SAVE_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 模版方法：保存代码到文件的标准流程
     * @param result 代码结果对象
     * @return 保存的文件目录
     */
    public final File saveCode(T result, Long appId){
        // 1. 验证输入
        validateInput(result);
        // 2. 构建唯一保存目录
        String baseDirPath = buildUniqueDir(appId);
        // 3. 保存代码到文件（具体实现由子类提供）
        saveFiles(result, baseDirPath);
        // 4. 返回目录文件对象
        return new File(baseDirPath);
    }

    /**
     * 验证输入参数（可由子类覆盖）
     * @param result 代码解析后的结果对象
     */
    protected void validateInput(T result){
        if( result == null ){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    /**
     * 构建唯一文件保存目录
     * @return 保存文件的目录路径
     */
    protected final String buildUniqueDir(Long appId){
        String codeType = getCodeType().getValue();
        String uniqueDir = StrUtil.format("{}_{}", codeType, appId);
        String dir = FILE_SAVE_ROOT_DIR + File.separator + uniqueDir;
        FileUtil.mkdir(dir);
        return dir;
    }

    /**
     * 写入单个文件的工具方法，方便子类复用
     * @param dir 文件目录
     * @param fileName 文件名
     * @param content 文件内容
     */
    protected final void writeToFile(String dir, String fileName, String content){
        String filePath = dir + File.separator + fileName;
        FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
    }

    /**
     * 获取代码生成的类型（由子类实现）
     * @return 代码生成的类型
     */
    protected abstract CodeGenTypeEnum getCodeType();

    /**
     * 保存文件的具体实现（由子类实现）
     * @param result 代码解析后的结果对象
     * @param baseDirPath 保存文件的目录
     */
    protected abstract void saveFiles(T result, String baseDirPath);
}

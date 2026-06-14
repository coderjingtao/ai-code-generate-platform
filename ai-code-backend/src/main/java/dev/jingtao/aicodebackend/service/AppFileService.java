package dev.jingtao.aicodebackend.service;

import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import dev.jingtao.aicodebackend.model.vo.AppFileContentVO;
import dev.jingtao.aicodebackend.model.vo.AppFileNodeVO;

import java.nio.file.Path;
import java.util.List;

/**
 * 应用生成文件服务。
 */
public interface AppFileService {

    /**
     * 列出应用文件树
     * @param appId 应用ID
     * @param loginUser 当前登录用户
     * @return 文件树节点列表
     */
    List<AppFileNodeVO> listFileTree(Long appId, Users loginUser);

    /**
     * 获取文件内容（含权限校验）
     * @param appId 应用ID
     * @param path 文件相对路径
     * @param loginUser 当前登录用户
     * @return 文件内容
     */
    AppFileContentVO getFileContent(Long appId, String path, Users loginUser);

    /**
     * 读取文件内容（无权限校验，内部调用）
     * @param appId 应用ID
     * @param path 文件相对路径
     * @return 文件内容字符串
     */
    String readFileContent(Long appId, String path);

    /**
     * 写入文件
     * @param appId 应用ID
     * @param codeGenType 代码生成类型
     * @param path 文件相对路径
     * @param content 文件内容
     */
    void writeFile(Long appId, CodeGenTypeEnum codeGenType, String path, String content);

    /**
     * 读取文件内容（无权限校验，显式指定类型，供内部/工具调用）
     * @param appId 应用ID
     * @param codeGenType 代码生成类型
     * @param path 文件相对路径
     * @return 文件内容；文件不存在时返回 null
     */
    String readFileContent(Long appId, CodeGenTypeEnum codeGenType, String path);

    /**
     * 删除文件（无权限校验，供内部/工具调用）
     * @param appId 应用ID
     * @param codeGenType 代码生成类型
     * @param path 文件相对路径
     * @return 是否删除了文件（文件不存在返回 false）
     */
    boolean deleteFile(Long appId, CodeGenTypeEnum codeGenType, String path);

    /**
     * 将相对路径安全解析为应用目录下的绝对路径（防止路径穿越），供内部/工具调用。
     * relativePath 为空时返回应用根目录。
     * @param appId 应用ID
     * @param codeGenType 代码生成类型
     * @param relativePath 相对路径
     * @return 校验通过的绝对路径
     */
    Path resolveSafePath(Long appId, CodeGenTypeEnum codeGenType, String relativePath);
}

package dev.jingtao.aicodebackend.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 异步构建 Vue 项目
     *
     * @param projectPath Vue项目的根目录
     */
    public void buildProjectAsync(String projectPath){
        Thread.ofVirtual().name("vue-builder-"+System.currentTimeMillis())
                .start(()->{
                    try{
                        buildProject(projectPath);
                    } catch (Exception e) {
                        log.error("异步构建Vue项目时发生异常：{}", e.getMessage(), e);
                    }
                });
    }
    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath){
        File projectDir = new File(projectPath);
        if(!projectDir.exists() || !projectDir.isDirectory()){
            log.error("项目目录不存在：{}", projectPath);
            return false;
        }
        // AI 偶尔会把工程多套一层目录（如 vue_project_x/my-app/package.json），
        // 这里先把嵌套的工程内容上移到根目录，保证后续 build 与预览路径一致
        flattenNestedProject(projectDir);
        // 检查是否有package.json
        File packageJsonFile = new File(projectDir, "package.json");
        if(!packageJsonFile.exists()){
            log.error("项目目录中没有 package.json 文件：{}", projectPath);
            return false;
        }
        log.info("开始构建 Vue 项目：{}", projectPath);
        // 执行 npm install
        if(!executeNpmInstall(projectDir)){
            log.error("npm install 执行失败：{}", projectPath);
            return false;
        }
        // 执行 npm run build
        if(!executeNpmBuild(projectDir)){
            log.error("npm run build 执行失败：{}", projectPath);
            return false;
        }
        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if(!distDir.exists() || !distDir.isDirectory()){
            log.error("构建完成但 dist 目录未生成：{}", projectPath);
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录：{}", distDir);
        return true;
    }

    /**
     * 修正「多套一层目录」的情况：当根目录下没有 package.json，但恰好有一个子目录里含有
     * package.json 时，把该子目录的全部内容上移到根目录，并删除空的子目录。
     * 仅处理这一种明确场景，避免误判正常的多文件工程结构。
     */
    private void flattenNestedProject(File projectDir) {
        File rootPackageJson = new File(projectDir, "package.json");
        if (rootPackageJson.exists()) {
            return;
        }
        File[] children = projectDir.listFiles();
        if (children == null) {
            return;
        }
        // 找出含有 package.json 的子目录
        File nestedRoot = null;
        for (File child : children) {
            if (child.isDirectory() && new File(child, "package.json").exists()) {
                if (nestedRoot != null) {
                    // 存在多个候选，结构不明确，放弃自动修正
                    log.warn("检测到多个嵌套工程目录，跳过自动展平：{}", projectDir.getAbsolutePath());
                    return;
                }
                nestedRoot = child;
            }
        }
        if (nestedRoot == null) {
            return;
        }
        log.warn("检测到工程被多套了一层目录，自动上移：{} -> {}", nestedRoot.getName(), projectDir.getAbsolutePath());
        File[] nestedEntries = nestedRoot.listFiles();
        if (nestedEntries != null) {
            for (File entry : nestedEntries) {
                File target = new File(projectDir, entry.getName());
                try {
                    Files.move(entry.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception e) {
                    log.error("上移嵌套工程文件失败：{}，错误：{}", entry.getName(), e.getMessage(), e);
                }
            }
        }
        // 删除已清空的嵌套目录（残留也不影响构建）
        if (!nestedRoot.delete()) {
            log.warn("嵌套目录上移后未能删除（不影响构建）：{}", nestedRoot.getAbsolutePath());
        }
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300); // 5分钟超时
    }
    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir){
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 10);
    }
    /**
     * 根据操作系统构造命令
     *
     * @param baseCommand 基本命令
     * @return 构造后的命令
     */
    private String buildCommand(String baseCommand){
        if(isWindows()){
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }
    /**
     * 检测操作系统是否是Windows
     *
     * @return true 代表是windows操作系统
     */
    private boolean isWindows(){
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds){
        try{
            log.info("在目录 {} 中执行命令：{}", workingDir.getAbsolutePath(), command);
            //把命令分割为数组, 然后运行
            Process process = RuntimeUtil.exec(null, workingDir, command.split("\\s+"));
            //等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if(!finished){
                log.error("命令执行超时 （{}秒）, 强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            int exitCode = process.exitValue();
            if(exitCode == 0){
                log.info("命令执行成功：{}", command);
                return true;
            }else{
                log.error("命令执行失败：{}，退出码：{}", command, exitCode);
                return false;
            }
        }catch (Exception e){
            log.error("执行命令失败：{}, 错误信息：{}", command, e.getMessage(), e);
            return false;
        }
    }
}

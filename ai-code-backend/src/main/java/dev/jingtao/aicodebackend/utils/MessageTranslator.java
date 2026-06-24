package dev.jingtao.aicodebackend.utils;

import java.util.Map;

/**
 * 后端响应文案的中 → 英翻译器（方案：在响应边界统一翻译）。
 * <p>
 * 代码中抛出的错误信息大多是中文字面量，逐处改造为消息 key 成本高、风险大。
 * 这里以「中文原文」为键维护一份英文译表，在响应出口（{@code ResultUtils} /
 * {@code GlobalExceptionHandler} / SSE 错误）统一翻译：当前语言为英文时查表替换，
 * 命中失败则原样返回中文（不丢信息）；中文环境直接返回原文。
 * <p>
 * 新增用户可见的中文提示后，只需在此追加一条映射即可。
 */
public final class MessageTranslator {

    private MessageTranslator() {
    }

    private static final Map<String, String> ZH_TO_EN = Map.ofEntries(
            // ErrorCode 默认信息
            Map.entry("请求参数错误", "Invalid request parameters"),
            Map.entry("未登录", "Not logged in"),
            Map.entry("无权限", "No permission"),
            Map.entry("请求过于频繁", "Too many requests"),
            Map.entry("请求数据不存在", "Requested data not found"),
            Map.entry("禁止访问", "Access forbidden"),
            Map.entry("AI 服务请求过于频繁，请稍后再试", "AI service is being requested too frequently, please try again later"),
            Map.entry("AI 服务繁忙，请稍后再试", "AI service is busy, please try again later"),
            Map.entry("系统内部异常", "Internal system error"),
            Map.entry("操作失败", "Operation failed"),
            // 通用异常处理
            Map.entry("系统错误", "System error"),
            Map.entry("输入内容不合规，请修改后重试", "Your input is not allowed. Please revise and try again."),
            // 应用相关
            Map.entry("应用ID错误", "Invalid app id"),
            Map.entry("应用 ID 错误", "Invalid app id"),
            Map.entry("用户提示词不能为空", "User prompt must not be empty"),
            Map.entry("应用不存在", "App not found"),
            Map.entry("无权限访问应用", "No permission to access this app"),
            Map.entry("无权限访问该应用文件", "No permission to access this app's files"),
            Map.entry("应用代码不存在，请先生成应用", "App code not found, please generate the app first"),
            Map.entry("应用名称不能为空", "App name must not be empty"),
            Map.entry("代码生成类型不能为空", "Code generation type must not be empty"),
            Map.entry("优先级不能小于 0", "Priority must not be less than 0"),
            Map.entry("创建应用的Prompt不能为空", "The prompt for creating an app must not be empty"),
            Map.entry("每页最多查询 20 个应用", "At most 20 apps can be queried per page"),
            Map.entry("至少指定一个要更新的字段", "Specify at least one field to update"),
            Map.entry("更新应用封面字段失败", "Failed to update the app cover field"),
            Map.entry("更新应用部署信息失败", "Failed to update the app deployment info"),
            Map.entry("Vue 项目构建完成但未生成 dist 目录", "The Vue project built but no dist directory was generated"),
            Map.entry("Vue项目构建失败，请重新部署", "Vue project build failed, please redeploy"),
            // 文件相关
            Map.entry("文件路径不能为空", "File path must not be empty"),
            Map.entry("文件路径不能是绝对路径", "File path must not be an absolute path"),
            Map.entry("文件路径越界", "File path is out of bounds"),
            Map.entry("文件路径非法", "Invalid file path"),
            Map.entry("读取文件内容时应用不存在", "App not found while reading file content"),
            Map.entry("读取文件内容时文件不存在", "File not found while reading file content"),
            // 用户相关
            Map.entry("参数为空", "Parameters must not be empty"),
            Map.entry("请求参数为空", "Request parameters must not be empty"),
            Map.entry("邮箱格式不正确", "Invalid email format"),
            Map.entry("用户密码过短", "Password is too short"),
            Map.entry("两次输入的密码不一致", "The two passwords do not match"),
            Map.entry("邮箱重复", "Email already exists"),
            Map.entry("注册失败，数据库错误", "Registration failed due to a database error"),
            Map.entry("密码错误", "Incorrect password"),
            Map.entry("用户不存在或密码错误", "User does not exist or the password is incorrect"),
            Map.entry("用户未登录", "User not logged in"),
            // 生成流程中的状态/进度提示（v2 事件流）
            Map.entry("正在构建 Vue 项目", "Building the Vue project"),
            Map.entry("Vue 项目构建完成", "Vue project build completed"),
            Map.entry("Vue 项目构建失败", "Vue project build failed"),
            Map.entry("代码已保存", "Code saved"),
            Map.entry("预览已更新", "Preview updated")
    );

    /**
     * 动态前缀译表：facade 拼接了动态后缀（路径 / 工具名 / 错误详情）的进度提示。
     * 命中前缀时替换前缀、保留后缀。
     */
    private static final Map<String, String> ZH_TO_EN_PREFIX = Map.ofEntries(
            Map.entry("正在生成 ", "Generating "),
            Map.entry("正在执行工具：", "Running tool: "),
            Map.entry("生成失败：", "Generation failed: "),
            Map.entry("AI 回复失败：", "AI response failed: "),
            Map.entry("构建失败：", "Build failed: ")
    );

    /**
     * 按当前请求语言翻译文案（同步请求线程可用）。
     */
    public static String translate(String message) {
        return translate(message, PromptLanguageUtils.current());
    }

    /**
     * 按显式语言翻译文案：英文环境先查精确表、再查前缀表，命中失败原样返回；
     * 中文环境原样返回。用于响应式线程（取不到 LocaleContextHolder）显式传入语言的场景。
     */
    public static String translate(String message, String lang) {
        if (message == null) {
            return null;
        }
        if (!PromptLanguageUtils.EN.equals(lang)) {
            return message;
        }
        String exact = ZH_TO_EN.get(message);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, String> entry : ZH_TO_EN_PREFIX.entrySet()) {
            if (message.startsWith(entry.getKey())) {
                return entry.getValue() + message.substring(entry.getKey().length());
            }
        }
        return message;
    }
}

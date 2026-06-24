package dev.jingtao.aicodebackend.utils;

import cn.hutool.core.util.StrUtil;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

/**
 * 输出语言解析与 AI 提示词语言指令注入（方案 A）。
 * <p>
 * 前端通过 SSE 查询参数 {@code lang} 或 {@code Accept-Language} 请求头传递语言，
 * 后端在调用 AI 之前给用户提示词追加一段「输出语言要求」，使 AI 的对话回复与
 * 生成的网站内容都使用对应语言。仅支持中文 / 英文，非中文一律回退英文。
 */
public final class PromptLanguageUtils {

    public static final String EN = "en";
    public static final String ZH = "zh";

    private PromptLanguageUtils() {
    }

    /**
     * 解析语言标识：以 zh 开头视为中文，其余（含 null）一律回退英文。
     */
    public static String resolve(String raw) {
        if (StrUtil.isBlank(raw)) {
            return EN;
        }
        return raw.trim().toLowerCase().startsWith("zh") ? ZH : EN;
    }

    /**
     * 从 Locale 解析语言标识（用于同步请求线程，配合 LocaleContextHolder）。
     */
    public static String fromLocale(Locale locale) {
        return locale != null && ZH.equalsIgnoreCase(locale.getLanguage()) ? ZH : EN;
    }

    /**
     * 当前请求线程的语言（同步 MVC 请求可用；响应式线程中可能取不到，回退英文）。
     */
    public static String current() {
        return fromLocale(LocaleContextHolder.getLocale());
    }

    /**
     * 返回纯粹的「输出语言要求」文案，语气强硬。
     * 用于注入系统提示词（最强通道，可压过整段中文 system prompt 的语言默认倾向），
     * 也用于在用户提示词前置（工作流 / 应用名生成等无法改 system prompt 的路径）。
     */
    public static String requirement(String lang) {
        if (ZH.equals(lang)) {
            return "【最高优先级·语言要求】无论本提示词或历史对话使用何种语言，你都必须：\n"
                    + "1) 始终使用简体中文回复用户；\n"
                    + "2) 生成的网站 / 代码中所有面向最终用户的可见文案（界面文字、标题、导航、按钮、"
                    + "表单标签、占位符、示例内容）一律使用简体中文（代码标识符与注释不受此限）。";
        }
        return "[TOP-PRIORITY LANGUAGE REQUIREMENT] Regardless of the language used in this prompt or in prior "
                + "conversation, you MUST:\n"
                + "1) Always reply to the user in English;\n"
                + "2) Write ALL end-user-visible copy in the generated website/code (UI text, headings, "
                + "navigation, buttons, form labels, placeholders, sample content) in English "
                + "(code identifiers and comments are exempt).";
    }

    /**
     * 在用户提示词前置语言指令；提示词为空时原样返回。
     */
    public static String append(String userPrompt, String lang) {
        if (StrUtil.isBlank(userPrompt)) {
            return userPrompt;
        }
        return requirement(lang) + "\n\n用户需求 / User request:\n" + userPrompt;
    }
}

package dev.jingtao.aicodebackend.utils;

import java.util.regex.Pattern;

/**
 * 提示词工具类
 */
public class PromptUtil {

    /**
     * 修改场景识别：
     * 1) 前端可视化编辑会附带“选中元素信息”
     * 2) 常见修改意图关键词
     */
    public static final String MODIFY_UI_PROMPT = "选中元素信息";

    public static final Pattern MODIFY_INTENT_PATTERN = Pattern.compile(
            "(修改|改为|改成|替换|替换为|替换成|只改|仅改|局部修改|更新文案|更新文本|定点修改)"
    );

    /**
     * 判断是否为修改场景
     *
     * @param prompt 提示词
     * @return 是否为修改场景
     */
    public static boolean isModificationScenario(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return false;
        }
        if (prompt.contains(MODIFY_UI_PROMPT)) {
            return true;
        }
        return MODIFY_INTENT_PATTERN.matcher(prompt).find();
    }
}

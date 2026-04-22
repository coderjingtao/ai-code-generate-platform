package dev.jingtao.aicodebackend.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

@Description("生成多个代码文件的结果")
@Data
public class MultiFileCodeResult {

    @Description("HTML 代码")
    private String htmlCode;

    @Description("CSS 代码")
    private String cssCode;

    @Description("JS 代码")
    private String jsCode;

    @Description("生成代码的描述")
    private String description;
}

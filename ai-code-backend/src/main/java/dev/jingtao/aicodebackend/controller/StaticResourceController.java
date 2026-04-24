package dev.jingtao.aicodebackend.controller;

import dev.jingtao.aicodebackend.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

@RestController
@RequestMapping("/static")
public class StaticResourceController {

    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    /**
     * 用户请求 → /api/static/{deployKey}/{文件路径}
     * → 从本地磁盘项目根目录读取文件 /项目根目录/tmp/code_output/{deployKey}/index.html
     * → 返回给浏览器
     * /项目根目录/tmp/code_output/
     * └── abc123/               ← 这就是 deployKey
     *     ├── index.html
     *     ├── style.css
     *     └── script.js
     * @param deployKey 部署 key
     * @param request 请求
     * @return 项目根目录下的静态资源
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String deployKey, HttpServletRequest request){
        try{
            //获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/"+ deployKey).length());
            //如果是目录访问（不带斜杠）, 重定向到带斜杠的URL /api/static/abc123 -> /api/static/abc123/
            if(resourcePath.isEmpty()){
                HttpHeaders httpHeaders = new HttpHeaders();
                httpHeaders.add("Location", request.getRequestURL() + "/");
                return new ResponseEntity<>(httpHeaders, HttpStatus.MOVED_PERMANENTLY);
            }
            //如果直接访问的根路径，则自动加上index.html: /api/static/abc123/ -> /api/static/abc123/index.html
            if(resourcePath.equals("/")){
                resourcePath = "/index.html";
            }
            //如果访问的是具体文件：/api/static/abc123/style.css, filePath = /项目根目录/tmp/code_output/abc123/style.css
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            File file = new File(filePath);
            // 检查文件是否存在
            if(!file.exists()){
                return ResponseEntity.notFound().build();
            }
            // 返回文件资源
            Resource resource = new FileSystemResource(file);
            return ResponseEntity
                    .ok()
                    .header(HttpHeaders.CONTENT_TYPE, getContentTypeWithCharset(filePath))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}

package dev.jingtao.aicodebackend.controller;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.mybatisflex.core.paginate.Page;
import dev.jingtao.aicodebackend.ai.model.message.AppGenerationMessage;
import dev.jingtao.aicodebackend.annotation.AuthCheck;
import dev.jingtao.aicodebackend.common.BaseResponse;
import dev.jingtao.aicodebackend.common.DeleteRequest;
import dev.jingtao.aicodebackend.common.ResultUtils;
import dev.jingtao.aicodebackend.constant.AppConstant;
import dev.jingtao.aicodebackend.constant.UserConstant;
import dev.jingtao.aicodebackend.exception.*;
import dev.jingtao.aicodebackend.model.dto.app.*;
import dev.jingtao.aicodebackend.model.entity.App;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.vo.AppVO;
import dev.jingtao.aicodebackend.ratelimiter.annotation.RateLimit;
import dev.jingtao.aicodebackend.ratelimiter.enums.RateLimitType;
import dev.jingtao.aicodebackend.service.AppService;
import dev.jingtao.aicodebackend.service.ProjectDownloadService;
import dev.jingtao.aicodebackend.service.UsersService;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.RateLimitException;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 应用 控制层。
 *
 * @author <a href="https://github.com/coderjingtao">Jingtao Liu</a>
 */
@RestController
@RequestMapping("/app")
public class AppController {

    private static final int USER_MAX_PAGE_SIZE = 20;

    @Resource
    private AppService appService;

    @Resource
    private UsersService usersService;

    @Resource
    private ProjectDownloadService projectDownloadService;



    /**
     * 用户提示词生成应用，并流式返回给前端
     * @param appId 应用ID
     * @param userPrompt 用户提示词
     * @param request 请求
     * @return SSE 流式字符串
     */
    @RateLimit(limitType = RateLimitType.USER, rate = 2, rateInterval = 60, message = "Too many requests, please try again later.")
    @GetMapping(value = "/chat/gen/code", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatToGenCode(@RequestParam Long appId,
                                      @RequestParam String userPrompt,
                                      @RequestParam(defaultValue = "classic") String mode,
                                      HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <=0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        ThrowUtils.throwIf(StrUtil.isBlank(userPrompt), ErrorCode.PARAMS_ERROR, "User prompt must not be empty");
        Users loginUser = usersService.getLoginUser(request);
        // 调用AI服务生成流式代码
        Flux<String> codeStream = appService.chatToGenCode(appId, userPrompt, loginUser, mode);
        // 把流式代码封装成 ServerSentEvent 格式，解决前端空格丢失的问题
        return codeStream
                .map(code -> {
                    //将内容包装成JSON对象
                    Map<String, String> wrapper = Map.of("d", code);
                    String json = JSONUtil.toJsonStr(wrapper);
                    return ServerSentEvent.<String>builder()
                            .data(json)
                            .build();
                })
                .concatWith(Mono.just(
                        // 发送结束事件，明确告诉前端已经正常结束
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data("")
                                .build()
                ))
                .onErrorResume(RateLimitException.class, e -> Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data(JSONUtil.toJsonStr(AiRateLimitError.from(e)))
                                .build()
                ))
                .onErrorResume(HttpException.class, e -> {
                    if (e.statusCode() != HttpStatus.SERVICE_UNAVAILABLE.value()) {
                        return Mono.error(e);
                    }
                    return Mono.just(
                            ServerSentEvent.<String>builder()
                                    .event("error")
                                    .data(JSONUtil.toJsonStr(AiServiceUnavailableError.create()))
                                    .build()
                    );
                });
    }

    @RateLimit(limitType = RateLimitType.USER, rate = 2, rateInterval = 60, message = "Too many requests, please try again later.")
    @GetMapping(value = "/chat/gen/code/v2", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatToGenCodeV2(@RequestParam Long appId,
                                                       @RequestParam String userPrompt,
                                                       HttpServletRequest request) {
        ThrowUtils.throwIf(appId == null || appId <=0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        ThrowUtils.throwIf(StrUtil.isBlank(userPrompt), ErrorCode.PARAMS_ERROR, "User prompt must not be empty");
        Users loginUser = usersService.getLoginUser(request);
        // 调用AI服务生成流式代码
        Flux<AppGenerationMessage> eventFlux = appService.chatToGenCodeV2(appId, userPrompt, loginUser);
        // 把流式代码封装成 ServerSentEvent 格式，解决前端空格丢失的问题
        return eventFlux
                .map(event -> ServerSentEvent.<String>builder()
                        .event(event.getType())
                        .data(JSONUtil.toJsonStr(event))
                        .build())
                .concatWith(Mono.just(
                        ServerSentEvent.<String>builder()
                                .event("done")
                                .data(JSONUtil.toJsonStr(Map.of("type", "done", "appId", appId)))
                                .build()
                ));
    }

    /**
     * 应用部署
     * @param appDeployRequest 应用部署请求对象
     * @param request 请求
     * @return 部署完成后到URL
     */
    @PostMapping("/deploy")
    public BaseResponse<String> deployApp(@RequestBody AppDeployRequest appDeployRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appDeployRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取App ID
        Long appId = appDeployRequest.getAppId();
        ThrowUtils.throwIf(appId == null || appId <=0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        // 获取登录用户
        Users loginUser = usersService.getLoginUser(request);
        // 部署服务
        String deployUrl = appService.deployApp(appId, loginUser);
        return ResultUtils.success(deployUrl);
    }

    /**
     * 普通用户创建应用（initPrompt 必填）
     */
    @PostMapping("/add")
    public BaseResponse<Long> addApp(@RequestBody AppAddRequest appAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR);
        Users loginUser = usersService.getLoginUser(request);
        long appId = appService.createApp(appAddRequest, loginUser);
        return ResultUtils.success(appId);
    }

    /**
     * 用户根据 id 修改自己的应用（目前仅支持修改名称）
     */
    @PostMapping("/update")
    public BaseResponse<Boolean> updateMyApp(@RequestBody AppUpdateRequest appUpdateRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(appUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        Users loginUser = usersService.getLoginUser(request);
        boolean result = appService.updateMyApp(appUpdateRequest, loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 用户根据 id 删除自己的应用
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteMyApp(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        Users loginUser = usersService.getLoginUser(request);
        boolean result = appService.deleteMyApp(deleteRequest.getId(), loginUser);
        return ResultUtils.success(result);
    }

    /**
     * 用户根据 id 查询自己的应用详情
     */
    @GetMapping("/get/vo")
    public BaseResponse<AppVO> getMyAppById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Users loginUser = usersService.getLoginUser(request);
        AppVO appVO = appService.getMyAppById(id, loginUser);
        return ResultUtils.success(appVO);
    }

    /**
     * 用户分页查询自己的应用（每页最多 20 个）
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<AppVO>> listMyAppByPage(@RequestBody AppQueryRequest appQueryRequest,
                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageNum <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(pageSize > USER_MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        Users loginUser = usersService.getLoginUser(request);

        // 查询用户自己的应用列表
        appQueryRequest.setUserId(loginUser.getId());
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize),
                appService.getAppQueryWrapper(appQueryRequest));
        // 封装应用列表
        return ResultUtils.success(getAppVoPage(appPage, pageNum, pageSize));
    }

    /**
     * 封装 app page 到 app vo page, 方便前端显示
     * @param appPage app 查询实体结果分页
     * @param pageNum 查询的当前页数
     * @param pageSize 查询的当前每页结果数
     * @return 封装后的应用分页VO数据
     */
    private Page<AppVO> getAppVoPage(Page<App> appPage, long pageNum, long pageSize){
        Page<AppVO> appVOPage = new Page<>(pageNum, pageSize, appPage.getTotalRow());
        List<AppVO> appVOList = appService.getAppVOList(appPage.getRecords());
        appVOPage.setRecords(appVOList);
        return appVOPage;
    }

    /**
     * 用户分页查询精选应用（每页最多 20 个）
     */
    @PostMapping("/good/list/page/vo")
    @Cacheable(
            value = "good_app_page",
            key = "T(dev.jingtao.aicodebackend.utils.CacheKeyUtils).generateKey(#appQueryRequest)",
            condition = "#appQueryRequest.pageNum <= 10"
    )
    public BaseResponse<Page<AppVO>> listGoodAppByPage(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageNum <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(pageSize > USER_MAX_PAGE_SIZE, ErrorCode.PARAMS_ERROR, "每页最多查询 20 个应用");
        // 只查询精选应用
        appQueryRequest.setPriority(AppConstant.GOOD_APP_PRIORITY);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize), appService.getAppQueryWrapper(appQueryRequest));
        // 封装应用列表
        return ResultUtils.success(getAppVoPage(appPage, pageNum, pageSize));
    }

    /**
     * 管理员根据 id 删除任意应用
     */
    @PostMapping("/admin/delete")
    @AuthCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteAppByAdmin(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null || deleteRequest.getId() <= 0,
                ErrorCode.PARAMS_ERROR);
        Long id = deleteRequest.getId();
        // 判断应用是否存在
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 删除
        boolean result = appService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 管理员根据 id 更新任意应用（支持更新名称、封面、优先级）
     */
    @PostMapping("/admin/update")
    @AuthCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateAppByAdmin(@RequestBody AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwIf(appAdminUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        boolean result = appService.updateAppByAdmin(appAdminUpdateRequest);
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查询应用列表
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AppVO>> listAppByPageForAdmin(@RequestBody AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = appQueryRequest.getPageNum();
        long pageSize = appQueryRequest.getPageSize();
        ThrowUtils.throwIf(pageNum <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        Page<App> appPage = appService.page(Page.of(pageNum, pageSize),
                appService.getAppQueryWrapper(appQueryRequest));
        return ResultUtils.success(getAppVoPage(appPage,pageNum,pageSize));
    }

    /**
     * 管理员根据 id 查看应用详情
     */
    @GetMapping("/admin/get/vo")
    @AuthCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<AppVO> getAppByIdForAdmin(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        App app = appService.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        AppVO appVO = appService.getAppVO(app);
        return ResultUtils.success(appVO);
    }

    /**
     * 获取应用代码目录下的文件列表（相对路径）
     */
    @GetMapping("/files/{appId}")
    public BaseResponse<List<String>> listAppFiles(@PathVariable Long appId, HttpServletRequest request) {
        // 1.基础校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        // 2.查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "App Not Found");
        // 3.权限校验，只有应用创建者/管理员才可以查看
        Users loginUser = usersService.getLoginUser(request);
        boolean isAdmin = dev.jingtao.aicodebackend.constant.UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        if(!app.getUserId().equals(loginUser.getId()) && !isAdmin){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "No permission to view this app's files");
        }
        // 4.构建应用代码目录路径
        String codeGenType = app.getCodeGenType();
        if (cn.hutool.core.util.StrUtil.isBlank(codeGenType)) {
            return ResultUtils.success(java.util.Collections.emptyList());
        }
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            return ResultUtils.success(java.util.Collections.emptyList());
        }
        
        // 5.遍历目录获取所有文件相对路径
        List<String> filePaths = new java.util.ArrayList<>();
        scanFiles(sourceDir, sourceDir.getAbsolutePath(), filePaths);
        // 排序
        java.util.Collections.sort(filePaths);
        return ResultUtils.success(filePaths);
    }

    private void scanFiles(File dir, String rootPath, List<String> filePaths) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().startsWith(".") 
                    || "node_modules".equals(file.getName()) 
                    || "dist".equals(file.getName())) {
                continue;
            }
            if (file.isDirectory()) {
                scanFiles(file, rootPath, filePaths);
            } else {
                String relativePath = file.getAbsolutePath().substring(rootPath.length() + 1);
                relativePath = relativePath.replace('\\', '/');
                filePaths.add(relativePath);
            }
        }
    }

    @GetMapping("/download/{appId}")
    public void downloadAppCode(@PathVariable Long appId, HttpServletRequest request, HttpServletResponse response) {
        // 1.基础校验
        ThrowUtils.throwIf(appId == null || appId < 0, ErrorCode.PARAMS_ERROR, "Invalid app id");
        // 2.查询应用信息
        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "App Not Found");
        // 3.权限校验，只有应用创建者才可以下载
        Users loginUser = usersService.getLoginUser(request);
        if(!app.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "No permission to download this app's code");
        }
        // 4.构建应用代码目录路径（生成目录，非部署目录）
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 5.检查代码目录是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(), ErrorCode.NOT_FOUND_ERROR, "App Code Not Found");
        // 6.生成下载文件名
        String downloadFileName = String.valueOf(appId);
        // 7.调用通用下载服务
        projectDownloadService.downloadProjectAsZip(sourceDirPath, downloadFileName, response);
    }
}

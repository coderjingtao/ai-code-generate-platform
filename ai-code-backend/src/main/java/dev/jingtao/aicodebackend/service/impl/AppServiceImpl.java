package dev.jingtao.aicodebackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.jingtao.aicodebackend.ai.AiCodeGenTypeRoutingService;
import dev.jingtao.aicodebackend.ai.model.message.AppGenerationMessage;
import dev.jingtao.aicodebackend.constant.AppConstant;
import dev.jingtao.aicodebackend.core.AiCodeGeneratorFacade;
import dev.jingtao.aicodebackend.core.builder.VueProjectBuilder;
import dev.jingtao.aicodebackend.core.engine.CodeGenEngine;
import dev.jingtao.aicodebackend.core.handler.StreamHandlerExecutor;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.exception.ThrowUtils;
import dev.jingtao.aicodebackend.mapper.AppMapper;
import dev.jingtao.aicodebackend.model.dto.app.AppAddRequest;
import dev.jingtao.aicodebackend.model.dto.app.AppAdminUpdateRequest;
import dev.jingtao.aicodebackend.model.dto.app.AppQueryRequest;
import dev.jingtao.aicodebackend.model.dto.app.AppUpdateRequest;
import dev.jingtao.aicodebackend.model.entity.App;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.ChatHistoryMessageTypeEnum;
import dev.jingtao.aicodebackend.model.enums.CodeGenModeEnum;
import dev.jingtao.aicodebackend.model.enums.CodeGenTypeEnum;
import dev.jingtao.aicodebackend.model.vo.AppVO;
import dev.jingtao.aicodebackend.model.vo.UserVO;
import dev.jingtao.aicodebackend.service.AppService;
import dev.jingtao.aicodebackend.service.ChatHistoryService;
import dev.jingtao.aicodebackend.service.ScreenshotService;
import dev.jingtao.aicodebackend.service.UsersService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/coderjingtao">Jingtao Liu</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private UsersService usersService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ScreenshotService screenshotService;
    @Resource
    private AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService;
    @Resource
    private List<CodeGenEngine> codeGenEngineList;

    private final Map<CodeGenModeEnum, CodeGenEngine> codeGenEngineMap = new EnumMap<>(CodeGenModeEnum.class);

    @PostConstruct
    public void init(){
        if(codeGenEngineMap.isEmpty()){
            for(CodeGenEngine engine : codeGenEngineList){
                codeGenEngineMap.put(engine.mode(), engine);
            }
        }
    }

    private CodeGenEngine getCodeGenEngine(CodeGenModeEnum mode){
        CodeGenEngine selectedEngine = codeGenEngineMap.getOrDefault(mode, codeGenEngineMap.get(CodeGenModeEnum.CLASSIC));
        ThrowUtils.throwIf(selectedEngine == null, ErrorCode.SYSTEM_ERROR, "未找到可用的代码生成引擎："+ mode.name());
        return selectedEngine;
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String userPrompt, Users loginUser, String mode) {
        // 1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR,"应用ID错误");
        ThrowUtils.throwIf(StrUtil.isBlank(userPrompt),ErrorCode.PARAMS_ERROR,"用户提示词不能为空");
        // 2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR,"应用不存在");
        // 3.权限校验
        ThrowUtils.throwIf(ObjectUtil.notEqual(app.getUserId(),loginUser.getId()),ErrorCode.NO_AUTH_ERROR,"无权限访问应用");
        // 4.获取代码的生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的代码生成类型："+ codeGenType);

        CodeGenModeEnum codeGenMode = CodeGenModeEnum.getEnumByValue(mode);
        CodeGenEngine codeGenEngine = getCodeGenEngine(codeGenMode);
        // 5.添加[用户消息]到对话历史
        chatHistoryService.addChatHistory(appId, userPrompt, ChatHistoryMessageTypeEnum.USERS.getValue(), loginUser.getId());
        // 6.根据选择的代码引擎来生成代码
        Flux<String> codeStream = codeGenEngine.generate(appId, userPrompt, loginUser, codeGenTypeEnum);
        // 7.收集AI响应内容并在完成后添加到对话历史中
        return streamHandlerExecutor.doExecute(codeStream,chatHistoryService,appId,loginUser,codeGenTypeEnum);
    }

    @Override
    public Flux<AppGenerationMessage> chatToGenCodeV2(Long appId, String userPrompt, Users loginUser, String mode) {
        // 1.参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR,"应用ID错误");
        ThrowUtils.throwIf(StrUtil.isBlank(userPrompt),ErrorCode.PARAMS_ERROR,"用户提示词不能为空");
        // 2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR,"应用不存在");
        // 3.权限校验
        ThrowUtils.throwIf(ObjectUtil.notEqual(app.getUserId(),loginUser.getId()),ErrorCode.NO_AUTH_ERROR,"无权限访问应用");
        // 4.获取代码的生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        ThrowUtils.throwIf(codeGenTypeEnum == null, ErrorCode.PARAMS_ERROR, "不支持的代码生成类型："+ codeGenType);

        CodeGenModeEnum codeGenMode = CodeGenModeEnum.getEnumByValue(mode);
        CodeGenEngine codeGenEngine = getCodeGenEngine(codeGenMode);
        // 5.添加[用户消息]到对话历史
        chatHistoryService.addChatHistory(appId, userPrompt, ChatHistoryMessageTypeEnum.USERS.getValue(), loginUser.getId());
        // 6.根据选择的代码引擎来生成代码
        StringBuilder aiResponseBuilder = new StringBuilder();
        Flux<AppGenerationMessage> eventStream = codeGenEngine.generateEvent(appId, userPrompt, loginUser, codeGenTypeEnum);
        // 7.收集AI响应内容并在完成后添加到对话历史中
//        return streamHandlerExecutor.doExecute(eventStream,chatHistoryService,appId,loginUser,codeGenTypeEnum);

        return eventStream
                .doOnNext(event -> collectNonCodeMessage(event, aiResponseBuilder))
                .doOnComplete(() -> {
                    String aiResponse = aiResponseBuilder.toString();
                    if(StrUtil.isNotBlank(aiResponse)) {
                        chatHistoryService.addChatHistory(appId,aiResponse,ChatHistoryMessageTypeEnum.AI.getValue(),loginUser.getId());
                    }
                })
                .doOnError(error -> {
                    String errorMessage = "AI回复失败：" + error.getMessage();
                    chatHistoryService.addChatHistory(appId,errorMessage,ChatHistoryMessageTypeEnum.AI.getValue(),loginUser.getId());
                });
    }
    /**
     * 从事件流中收集非代码文件内容（文本响应、工具提示等），用于保存为 AI 对话历史
     */
    private void collectNonCodeMessage(AppGenerationMessage event, StringBuilder aiResponseBuilder) {
        if (event == null || StrUtil.isBlank(event.getType())) {
            return;
        }
        switch (event.getType()) {
            case "assistant_message" -> aiResponseBuilder.append(event.getContent());
            case "tool_call", "build_status", "preview_ready", "generation_error" -> {
                String message = StrUtil.blankToDefault(event.getMessage(), event.getContent());
                if (StrUtil.isNotBlank(message)) {
                    aiResponseBuilder.append("\n\n").append(message).append("\n");
                }
            }
            default -> {
            }
        }
    }

    @Override
    public long createApp(AppAddRequest appAddRequest, Users loginUser) {
        // 参数校验
        ThrowUtils.throwIf(appAddRequest == null || loginUser == null || loginUser.getId() == null, ErrorCode.PARAMS_ERROR);
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "创建应用的Prompt不能为空");
        // 构造入库对象
        App app = new App();
        BeanUtils.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        // 使用 AI 智能选择代码生成类型
        CodeGenTypeEnum selectedCodeGenType = aiCodeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(selectedCodeGenType.getValue());
        app.setInitPrompt(appAddRequest.getInitPrompt());
        app.setPriority(AppConstant.DEFAULT_APP_PRIORITY);
        // 保存到数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), app.getCodeGenType());
        return app.getId();
    }

    @Override
    public String deployApp(Long appId, Users loginUser) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // 查询应用
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        // 权限校验，只有本人可以部署
        ThrowUtils.throwIf(!Objects.equals(app.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        // 检查是否已经有deployKey, 如果没有生成6位（字母+数字）
        String deployKey = app.getDeployKey();
        if(StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 获取代码生成类型，并获取原始代码生成的保存路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 检查路径是否存在
        File sourceDir = new File(sourceDirPath);
        ThrowUtils.throwIf(!sourceDir.exists() || !sourceDir.isDirectory(), ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成应用");
        // 如果是Vue项目，需要执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if(codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            boolean success = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!success, ErrorCode.SYSTEM_ERROR, "Vue项目构建失败，请重新部署");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists() || !distDir.isDirectory(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            sourceDir = distDir;
        }
        // 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try{
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        }catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }
        // 更新数据库
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean result = this.updateById(updateApp);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"更新应用部署信息失败");
        // 构建应用访问 URL
        String appDeployUrl = String.format("%s/%s",AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 异步上传应用封面，避免阻塞用户的部署
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    @Override
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        // 使用虚拟线程
        Thread.startVirtualThread(()-> {
           // 调用截图服务生成截图并上传对象存储
            String screenshotUrl = screenshotService.takeAndUploadScreenshot(appUrl);
            // 更新应用的封面到数据库
            App updateApp = new App();
            updateApp.setId(appId);
            updateApp.setCover(screenshotUrl);
            boolean updated = this.updateById(updateApp);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新应用封面字段失败");
        });
    }

    @Override
    public boolean updateMyApp(AppUpdateRequest appUpdateRequest, Users loginUser) {
        // 参数校验
        ThrowUtils.throwIf(appUpdateRequest == null || appUpdateRequest.getId() == null || appUpdateRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.PARAMS_ERROR);
        // 判断应用是否存在
        Long appId = appUpdateRequest.getId();
        App oldApp = this.getById(appId);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 判断是否本人操作
        ThrowUtils.throwIf(!Objects.equals(oldApp.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        // 更新数据库
        App app = new App();
        app.setId(appId);
        app.setAppName(appUpdateRequest.getAppName());
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = this.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return true;
    }

    @Override
    public boolean deleteMyApp(Long id, Users loginUser) {
        // 参数校验
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.PARAMS_ERROR);
        // 判断应用是否存在
        App oldApp = this.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 判断是否本人操作
        ThrowUtils.throwIf(!Objects.equals(oldApp.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        boolean result = this.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return true;
    }

    @Override
    public AppVO getMyAppById(Long id, Users loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.PARAMS_ERROR);
        // 查询数据库获取App Entity
        App app = this.getById(id);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        ThrowUtils.throwIf(!Objects.equals(app.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR);
        return this.getAppVO(app);
    }

    @Override
    public boolean updateAppByAdmin(AppAdminUpdateRequest appAdminUpdateRequest) {
        ThrowUtils.throwIf(appAdminUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        Long id = appAdminUpdateRequest.getId();
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        // 校验App是否存在
        App oldApp = this.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验参数
        String appName = appAdminUpdateRequest.getAppName();
        if (appName != null) {
            ThrowUtils.throwIf(StrUtil.isBlank(appName), ErrorCode.PARAMS_ERROR, "应用名称不能为空");
        }
        Integer priority = appAdminUpdateRequest.getPriority();
        if (priority != null) {
            ThrowUtils.throwIf(priority < 0, ErrorCode.PARAMS_ERROR, "优先级不能小于 0");
        }
        boolean hasUpdateField = appName != null
                || appAdminUpdateRequest.getCover() != null
                || priority != null;
        ThrowUtils.throwIf(!hasUpdateField, ErrorCode.PARAMS_ERROR, "至少指定一个要更新的字段");

        App app = new App();
        BeanUtils.copyProperties(appAdminUpdateRequest, app);
        // 设置编辑时间
        app.setEditTime(LocalDateTime.now());
        boolean result = this.updateById(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return true;
    }

    @Override
    public AppVO getAppVO(App app) {
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR);
        AppVO appVO = new AppVO();
        BeanUtils.copyProperties(app, appVO);
        // 关联查询应用的用户信息
        Long userId = app.getUserId();
        if(userId != null && userId > 0) {
            Users user = usersService.getById(userId);
            UserVO userVO = usersService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if(CollUtil.isEmpty(appList)) {
            return List.of();
        }
        // 批量获取用户信息，避免 N+1 问题
        Set<Long> userIds = appList.stream().map(App::getUserId).collect(Collectors.toSet());
        List<Users> users = usersService.listByIds(userIds);
        Map<Long, UserVO> userVOMap = users.stream().collect(Collectors.toUnmodifiableMap(Users::getId, usersService::getUserVO));

        return appList.stream().map( app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getAppQueryWrapper(AppQueryRequest appQueryRequest) {
        ThrowUtils.throwIf(appQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long appId = appQueryRequest.getId();
        Long userId = appQueryRequest.getUserId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        if (StrUtil.isBlank(sortField)) {
            sortField = "createTime";
        }
        return QueryWrapper.create()
                .eq("id", appId)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    /**
     * 重写MyBatis Flex中IService的删除应用的方法，需要删除关联对话历史记录
     * @param id 应用id
     * @return 是否删除成功
     */
    @Override
    public boolean removeById(Serializable id) {
        if(id == null) {
            return false;
        }
        Long appId = Long.parseLong(id.toString());
        if(appId <= 0) {
            return false;
        }
        //先删除关联的对话历史
        try{
            chatHistoryService.deleteByAppId(appId);
        }catch (Exception e) {
            //记录日志但不阻止应用的删除
            log.error("删除应用: {} 的对话历史失败, 错误信息是: {}", appId, e.getMessage(), e);
        }
        //再删除应用
        return super.removeById(id);
    }
}

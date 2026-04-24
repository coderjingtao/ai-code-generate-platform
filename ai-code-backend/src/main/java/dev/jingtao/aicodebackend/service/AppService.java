package dev.jingtao.aicodebackend.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.jingtao.aicodebackend.model.dto.app.AppAddRequest;
import dev.jingtao.aicodebackend.model.dto.app.AppAdminUpdateRequest;
import dev.jingtao.aicodebackend.model.dto.app.AppQueryRequest;
import dev.jingtao.aicodebackend.model.dto.app.AppUpdateRequest;
import dev.jingtao.aicodebackend.model.entity.App;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.vo.AppVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author <a href="https://github.com/coderjingtao">Jingtao Liu</a>
 */
public interface AppService extends IService<App> {

    /**
     * 通过对话生成应用代码
     *
     * @param appId     应用 ID
     * @param userPrompt   生成应用的用户提示词
     * @param loginUser 登录用户
     * @return AI返回的流式字符串
     */
    Flux<String> chatToGenCode(Long appId, String userPrompt, Users loginUser);

    /**
     * 创建应用
     *
     * @param appAddRequest 创建请求
     * @param loginUser     当前登录用户
     * @return 新应用 id
     */
    long createApp(AppAddRequest appAddRequest, Users loginUser);

    /**
     * 部署应用
     *
     * @param appId 应用ID
     * @param loginUser     当前登录用户
     * @return 新应用 id
     */
    String deployApp(Long appId, Users loginUser);

    /**
     * 修改自己的应用（仅支持修改名称）
     *
     * @param appUpdateRequest 更新请求
     * @param loginUser          当前登录用户
     * @return 是否更新成功
     */
    boolean updateMyApp(AppUpdateRequest appUpdateRequest, Users loginUser);

    /**
     * 删除自己的应用
     *
     * @param id        应用 id
     * @param loginUser 当前登录用户
     * @return 是否删除成功
     */
    boolean deleteMyApp(Long id, Users loginUser);

    /**
     * 查询自己的应用详情
     *
     * @param id        应用 id
     * @param loginUser 当前登录用户
     * @return 应用
     */
    AppVO getMyAppById(Long id, Users loginUser);

    /**
     * 管理员更新应用
     *
     * @param appAdminUpdateRequest 管理员更新请求
     * @return 是否更新成功
     */
    boolean updateAppByAdmin(AppAdminUpdateRequest appAdminUpdateRequest);

    /**
     * 获取应用封装类
     *
     * @param app 应用实体类
     * @return 应用封装类
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用封装类列表
     *
     * @param appList 应用实体类列表
     * @return 应用封装类列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    QueryWrapper getAppQueryWrapper(AppQueryRequest appQueryRequest);
}

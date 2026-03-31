package dev.jingtao.aicodebackend.service;

import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import dev.jingtao.aicodebackend.model.dto.UserQueryRequest;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.vo.LoginUserVO;
import dev.jingtao.aicodebackend.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 系统用户 服务层。
 *
 * @author <a href="https://github.com/coderjingtao">Jingtao Liu</a>
 */
public interface UsersService extends IService<Users> {

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);

    String getEncryptPassword(String userPassword);

    /**
     * 获取脱敏的已登录用户信息
     *
     * @return 脱敏的已登录用户信息
     */
    LoginUserVO getLoginUserVO(Users user);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request httpRequest
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取当前登录用户
     *
     * @param request httpRequest
     * @return 从session中获取登录的用户
     */
    Users getLoginUser(HttpServletRequest request);

    /**
     * 用户登出
     *
     * @param request httpRequest
     * @return  用户是否登出成功
     */
    boolean userLogout(HttpServletRequest request);

    UserVO getUserVO(Users user);
    List<UserVO> getUserVOList(List<Users> userList);

    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);
}

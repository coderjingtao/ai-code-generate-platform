package dev.jingtao.aicodebackend.controller;

import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.paginate.Page;
import dev.jingtao.aicodebackend.annotation.AuthCheck;
import dev.jingtao.aicodebackend.common.BaseResponse;
import dev.jingtao.aicodebackend.common.DeleteRequest;
import dev.jingtao.aicodebackend.common.ResultUtils;
import dev.jingtao.aicodebackend.constant.UserConstant;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.exception.ThrowUtils;
import dev.jingtao.aicodebackend.model.dto.*;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.vo.LoginUserVO;
import dev.jingtao.aicodebackend.model.vo.UserVO;
import dev.jingtao.aicodebackend.service.UsersService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统用户 控制层。
 *
 * @author <a href="https://github.com/coderjingtao">Jingtao Liu</a>
 */
@RestController
@RequestMapping("/users")
public class UsersController {

    @Resource
    private UsersService usersService;

    @PostMapping("register")
    public BaseResponse<Long> register(@RequestBody UserRegisterRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        String userAccount = request.getUserAccount();
        String userPassword = request.getUserPassword();
        String checkPassword = request.getCheckPassword();
        long userId = usersService.userRegister(userAccount, userPassword, checkPassword);
        return ResultUtils.success(userId);
    }

    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = usersService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        Users loginUser = usersService.getLoginUser(request);
        return ResultUtils.success(usersService.getLoginUserVO(loginUser));
    }

    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = usersService.userLogout(request);
        return ResultUtils.success(result);
    }


    /**
     * 创建用户（仅管理员）
     */
    @PostMapping("/add")
    @AuthCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        Users user = new Users();
        BeanUtil.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = usersService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        boolean result = usersService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Users> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        Users user = usersService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<Users> response = getUserById(id);
        Users user = response.getData();
        return ResultUtils.success(usersService.getUserVO(user));
    }

    /**
     * 删除用户（仅管理员）
     */
    @PostMapping("/delete")
    @AuthCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = usersService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户（仅管理员）
     */
    @PostMapping("/update")
    @AuthCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Users user = new Users();
        BeanUtil.copyProperties(userUpdateRequest, user);
        boolean result = usersService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long pageNum = userQueryRequest.getPageNum();
        long pageSize = userQueryRequest.getPageSize();
        Page<Users> userPage = usersService.page(Page.of(pageNum, pageSize),
                usersService.getQueryWrapper(userQueryRequest));
        // 数据脱敏
        Page<UserVO> userVOPage = new Page<>(pageNum, pageSize, userPage.getTotalRow());
        List<UserVO> userVOList = usersService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

}

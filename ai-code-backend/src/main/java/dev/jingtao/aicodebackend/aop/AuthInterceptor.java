package dev.jingtao.aicodebackend.aop;

import dev.jingtao.aicodebackend.annotation.AuthCheck;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.model.enums.UserRoleEnum;
import dev.jingtao.aicodebackend.service.UsersService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UsersService usersService;

    /**
     * 执行拦截
     *
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustHaveRole = authCheck.mustHaveRole();
        UserRoleEnum mustHaveRoleEnum = UserRoleEnum.getEnumByValue(mustHaveRole);
        // 不需要权限，放行
        if (mustHaveRoleEnum == null) {
            return joinPoint.proceed();
        }
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 获取当前登录用户
        Users loginUser = usersService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 获取当前用户具有的权限
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        // 没有权限，拒绝
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        if (mustHaveRoleEnum == UserRoleEnum.ADMIN && userRoleEnum != UserRoleEnum.ADMIN) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();
    }
}


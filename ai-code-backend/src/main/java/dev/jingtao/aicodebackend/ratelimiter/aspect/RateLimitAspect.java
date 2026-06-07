package dev.jingtao.aicodebackend.ratelimiter.aspect;

import cn.hutool.core.util.StrUtil;
import dev.jingtao.aicodebackend.exception.BusinessException;
import dev.jingtao.aicodebackend.exception.ErrorCode;
import dev.jingtao.aicodebackend.model.entity.Users;
import dev.jingtao.aicodebackend.ratelimiter.annotation.RateLimit;
import dev.jingtao.aicodebackend.ratelimiter.enums.RateLimitType;
import dev.jingtao.aicodebackend.service.UsersService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private UsersService usersService;

    @Before("@annotation(rateLimit)")
    public void doBefore(JoinPoint joinPoint, RateLimit rateLimit){
        // 根据不同的限流策略，生成唯一的Redis限流key
        String key = generateRateLimitKey(joinPoint, rateLimit);
        // 使用Redisson的分布式限流器
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        // 为限流器设置1 小时后过期，否则Redis中的key会用不过期，内存会越来越高
        rateLimiter.expire(Duration.ofHours(1));
        // 设置限流器参数：每个时间窗口允许的请求数和时间窗口
        rateLimiter.trySetRate(RateType.OVERALL, rateLimit.rate(), Duration.ofSeconds(rateLimit.rateInterval()));
        // 尝试获取令牌桶中的令牌，如果获取失败则限流
        if(!rateLimiter.tryAcquire(1)){
            throw new BusinessException(ErrorCode.TOO_MANY_REQUEST, rateLimit.message());
        }
    }

    /**
     * 为不同的限流策略，生成唯一的限流key:
     * API 按照方法名
     * USER 按照用户ID
     * IP 按照客户端IP
     */
    private String generateRateLimitKey(JoinPoint point, RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append("rate_limit:");
        // 添加自定义前缀
        if (StrUtil.isNotBlank(rateLimit.key())) {
            keyBuilder.append(rateLimit.key()).append(":");
        }
        // 根据限流类型生成不同的 key
        if (rateLimit.limitType() == RateLimitType.API) {
            // 接口级别：方法名
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();
            keyBuilder.append("api:").append(method.getDeclaringClass().getSimpleName())
                    .append(".").append(method.getName());
        } else if (rateLimit.limitType() == RateLimitType.USER) {
            // 用户级别：用户ID
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                if (attributes != null) {
                    HttpServletRequest request = attributes.getRequest();
                    Users loginUser = usersService.getLoginUser(request);
                    keyBuilder.append("user:").append(loginUser.getId());
                } else {
                    // 无法获取请求上下文，使用IP限流
                    keyBuilder.append("ip:").append(getClientIP());
                }
            } catch (BusinessException e) {
                // 未登录用户使用IP限流
                keyBuilder.append("ip:").append(getClientIP());
            }
        } else if (rateLimit.limitType() == RateLimitType.IP) {
            // IP级别：客户端IP
            keyBuilder.append("ip:").append(getClientIP());
        } else {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "不支持的限流类型");
        }
        return keyBuilder.toString();
    }

    private String getClientIP() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 处理多级代理的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip != null ? ip : "unknown";
    }
}

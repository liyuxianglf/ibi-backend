package com.yx.manager;

import com.yx.common.ErrorCode;
import com.yx.exception.ThrowUtils;
import io.lettuce.core.RedisClient;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 通用服务：RedisLimiter限流服务
 */
@Service
public class RedisLimiterManager {
    @Resource
    RedissonClient redissonClient;

    /**
     * 限流操作
     *
     * @param key 区分不同的限流器，本项目中这里的key使用的是用户id，一个用户一个限流器
     */
    public void doRateLimit(String key) {
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        //RateType.OVERALL：全局限流
        rateLimiter.trySetRate(RateType.OVERALL, 2, 1, RateIntervalUnit.SECONDS);
        boolean canOp = rateLimiter.tryAcquire(1);
        ThrowUtils.throwIf(!canOp, ErrorCode.TOO_MANY_REQUEST);
    }


}

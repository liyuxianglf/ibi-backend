package com.yx.config;

import io.lettuce.core.RedisClient;
import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Redisson 配置
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "spring.redis")
public class RedissonConfig {

    private Integer database;

    private String host;

    private String password;

    private String port;

    @Bean
    public RedissonClient redissonClient(){
        Config config  = new Config();
        config.useSingleServer()
                .setDatabase(database)
                .setPassword(password)
                .setAddress("redis://"+host+":"+port);
        RedissonClient redissonClient = Redisson.create(config);
        return redissonClient;
    }
}

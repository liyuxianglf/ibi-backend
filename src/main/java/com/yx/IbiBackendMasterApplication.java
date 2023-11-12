package com.yx;

import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 项目启动类
 */
@EnableRabbit
@SpringBootApplication
public class IbiBackendMasterApplication {

    public static void main(String[] args) {
        SpringApplication.run(IbiBackendMasterApplication.class, args);
    }

}

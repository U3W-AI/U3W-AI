package com.wx.fbsir.business;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 面试助手启动类
 * * @author WxFbsir Team
 * @date 2026-01-22
 */
@SpringBootApplication
@MapperScan("com.wx.fbsir.business.mapper")
public class InterviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(InterviewApplication.class, args);
    }

}
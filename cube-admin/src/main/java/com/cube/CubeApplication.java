package com.cube;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启动程序
 *
 * @author ruoyi
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@MapperScan(value ={"com.cube.system.**.mapper", "com.cube.point.**.mapper", "com.cube.wechat.**.**.mapper"})
@EnableScheduling
@EnableAsync
public class CubeApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
//        Start.run();

        SpringApplication.run(CubeApplication.class, args);
        System.out.println(">>>>优立方平台启动成功<<<<<");
    }

}

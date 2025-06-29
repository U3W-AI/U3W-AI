/*
 * This file is part of the zyan/wework-msgaudit.
 *
 * (c) 读心印 <aa24615@qq.com>
 *
 * This source file is subject to the MIT license that is bundled
 * with this source code in the file LICENSE.
 */

package com.cube.wechat.selfapp.wecom;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;


public class DB {

    public static DriverManagerDataSource getInstance(){
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        //地址:端口
        dataSource.setUrl("");
        //用户名
        dataSource.setUsername("");
        //密码
        dataSource.setPassword("");
        return dataSource;
    }

    public static JdbcTemplate getJdbcTemplate(){

        JdbcTemplate jdbcTemplate = null;

        jdbcTemplate = new JdbcTemplate(getInstance());

        return jdbcTemplate;
    }
}

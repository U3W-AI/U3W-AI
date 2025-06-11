package com.cube.wechat.selfapp.datazone.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataZoneMapper {


    /*
    * 查询所有评论
    * */
    String getBotKey(String chatId);
}

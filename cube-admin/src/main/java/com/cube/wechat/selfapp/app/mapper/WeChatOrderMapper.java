package com.cube.wechat.selfapp.app.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.Map;

@Mapper
public interface WeChatOrderMapper {


    int saveOrderDetail(Map map);
}

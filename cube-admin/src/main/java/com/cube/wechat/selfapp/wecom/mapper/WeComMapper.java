package com.cube.wechat.selfapp.wecom.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface WeComMapper {

    //获取所有群聊ID
    List<String> getRoomList();

    int saveRoomData(Map map);

    String getUserId(String creator);

    int delRoomData();


}

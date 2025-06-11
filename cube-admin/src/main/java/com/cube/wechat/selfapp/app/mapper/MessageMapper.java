package com.cube.wechat.selfapp.app.mapper;


import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface MessageMapper {

    List<Map> getMsgData();

    List<Map> getGroupIds();


    int updateMsg(@Param("sendTime") Integer sendTime, @Param("formId") String formId,@Param("msgId") String msgId, @Param("secretKey") String secretKey);


    int updateMsgTag(@Param("msgid") String msgid,@Param("tag") String tag);

    Integer getUserTag(@Param("userId")String userId,@Param("tag")String tag);

    int saveUserTag(@Param("userId")String userId,@Param("tag")String tag);

}

package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmExternalGroupChat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RmExternalGroupChatMapper {
    int deleteByPrimaryKey(String id);

    void deleteGroupChatByUserId(@Param("userId")String userId);

    void insertBatchSelective(List<RmExternalGroupChat> list);

    int insertSelective(RmExternalGroupChat record);

    RmExternalGroupChat selectByPrimaryKey(String id);

    RmExternalGroupChat selectByChatId(@Param("chatId")String chatId);

    int updateByPrimaryKeySelective(RmExternalGroupChat record);

    void updateByChatIdSelective(RmExternalGroupChat record);

    Map queryGroupChatStatisticsData(Map map);

    List<RmExternalGroupChat> queryGroupChatStatisticsList(Map map);

    List<RmExternalGroupChat>  selectAllGroupChatByCorpId(String corpId);

}

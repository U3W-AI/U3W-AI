package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmExternalGroupChatMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RmExternalGroupChatMemberMapper {
    int deleteByPrimaryKey(String id);

    void deleteGroupChatMemberByUserId(@Param("userId")String userId);
    void deleteGroupChatNemberByChatId(@Param("chatId")String chatId);

    void updateGroupChatMemberStatusByChatId(@Param("memberStatus")Integer memberStatus,@Param("chatId")String chatId);
    int insertSelective(RmExternalGroupChatMember record);

    void insertBatchSelective(List<RmExternalGroupChatMember> list);
    RmExternalGroupChatMember selectByPrimaryKey(String id);

    RmExternalGroupChatMember selectChatMemberByMemberUserId(@Param("chatId")String chatId,@Param("memberUserId")String memberUserId);

    List<RmExternalGroupChatMember> selectChatAllMemberByChatId(@Param("chatId")String chatId,@Param("corpId")String corpId);

    int updateByPrimaryKeySelective(RmExternalGroupChatMember record);

    void updateByMemberUserId(RmExternalGroupChatMember record);

    void updateBatchByPrimaryKeySelective(List<RmExternalGroupChatMember> list);

    void updateByRmExternalGroupChatMember(RmExternalGroupChatMember rmExternalGroupChatMember);

    void clearGroupChatLeaderByChatId(RmExternalGroupChatMember record);
    void addGroupChatLeaderByChatIdAndUserId(RmExternalGroupChatMember rmExternalGroupChatMember);

    List<Map> selectGropChatMemberByChatId(Map map);

}

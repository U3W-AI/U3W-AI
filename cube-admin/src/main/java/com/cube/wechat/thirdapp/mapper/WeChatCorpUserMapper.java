package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.WeChatCorpUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WeChatCorpUserMapper {
    int deleteByPrimaryKey(String id);


    int insertSelective(WeChatCorpUser record);

    void insertBatchCorpUser(List<WeChatCorpUser> list);
    WeChatCorpUser selectByPrimaryKey(String id);

    WeChatCorpUser selectCorpUserByOpenUserId(WeChatCorpUser record);

    void updateCorpUserStatus(WeChatCorpUser WeChatCorpUser);

    void deleteCorpUserByUserId(WeChatCorpUser WeChatCorpUser);

    WeChatCorpUser selectCorpUserInfo(WeChatCorpUser WeChatCorpUser);

    WeChatCorpUser selectCorpUserId(WeChatCorpUser WeChatCorpUser);

    List<WeChatCorpUser> selectAllUserByCorpId(WeChatCorpUser WeChatCorpUser);

    List<WeChatCorpUser> selectAllUser(WeChatCorpUser WeChatCorpUser);

    int updateByPrimaryKeySelective(WeChatCorpUser record);

    void updateBatchByPrimaryKey(List<WeChatCorpUser> list);

    void updateCorpUserAvatar(WeChatCorpUser WeChatCorpUser);

    WeChatCorpUser selectUserBasicInformationByUserId(WeChatCorpUser WeChatCorpUser);


    /**
     * 查询该企业下 名字为空的 已激活人员
     * @param corpId 企业id
     * @param suiteId 应用id
     * @return 人员记录集合
     */
    List<WeChatCorpUser> selectUserNameIsNullByCorpId(@Param("corpId") String corpId, @Param("suiteId") String suiteId);




}

package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.WeChatThirdUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface WeChatThirdUserMapper {
    int deleteByPrimaryKey(String id);

    int insertSelective(WeChatThirdUser record);

    void deleteByCorpIdAndAppId(@Param("corpId")String corpID,@Param("suiteId")String suiteId);

    WeChatThirdUser selectByPrimaryKey(String id);

    List<WeChatThirdUser> selectByCorpIdAndAppId(@Param("corpId") String corpId,@Param("suiteId")String suiteId);

    int updateByPrimaryKeySelective(WeChatThirdUser record);

}

package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmExternalInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;


@Mapper
public interface RmExternalInfoMapper {
    int deleteByPrimaryKey(String id);

    void deleteByUserInfo(@Param("userId")String userId,@Param("corpId")String corpId);

    int insert(RmExternalInfo record);
    void insertSelectiveBatch(List<RmExternalInfo> dataList);

    int insertSelective(RmExternalInfo record);

    RmExternalInfo selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(RmExternalInfo record);

    void updateExternalAvatarByExternalUserId(RmExternalInfo rmExternalInfo);

    int updateByPrimaryKey(RmExternalInfo record);

    List<RmExternalInfo> selectExternalInfoByExternalInfo(RmExternalInfo externalInfo);

    RmExternalInfo selectExternalInfoByExternalInfoOne(RmExternalInfo externalInfo);

    List<RmExternalInfo> selectByAvatarIsNull(@Param("corpId") String corpId, @Param("offset") int offset, @Param("limit") int limit);
   // List<RmExternalInfo> selectByAvatarIsNull(@Param("corpId") String corpId);


    List<Map> selectByCorpIdAndInfoId(@Param("infoId") String infoId, @Param("corpId") String corpId);

    List<RmExternalInfo> selectByStatusAndType(@Param("corpId")String corpId);

    void updateExternalAvatarBatch(@Param("list") List<RmExternalInfo> infosToUpdate);
}

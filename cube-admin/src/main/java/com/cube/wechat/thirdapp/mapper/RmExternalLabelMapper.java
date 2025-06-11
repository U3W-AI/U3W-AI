package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmExternalLabel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RmExternalLabelMapper {
    int deleteByPrimaryKey(String id);

    int insert(RmExternalLabel record);

    int insertSelective(RmExternalLabel record);

    RmExternalLabel selectByPrimaryKey(String id);

    List<Map> selectExternalLabelByExternalUserId(@Param("externalUserId")String externalUserId,@Param("corpId")String corpId);

    String selectExternalLabel(@Param("userId")String userId,@Param("externalUserId")String externalUserId,@Param("corpId")String corpId);
    int updateByPrimaryKeySelective(RmExternalLabel record);

    int updateByPrimaryKey(RmExternalLabel record);

    void deleteExternalLabel(RmExternalLabel record);
    void  deleteExternalByUserInfo(@Param("userId")String userId,@Param("corpId")String corpId);

    void deleteExternalLabelByExternalUserId(@Param("externalUserId")String externalUserId,@Param("corpId")String corpId,@Param("userId")String userId);

    List<String> selectExternalLabelUserByExternalUserId(@Param("externalUserId")String externalUserId,@Param("corpId")String corpId);

    List<String>  selectLabelIdsByExternalUserIdAndCropId(@Param("externalUserId")String externalUserId,@Param("corpId")String corpId);

    List<Map> selectLabelListByExternalUserIdAndCropId(@Param("externalUserId")String externalUserId,@Param("corpId")String corpId);
    List<Map> selectExternalLabelNum(@Param("corpId")String corId, @Param("userId")String userId);

    List<Map> selectExternalNotHaveLabelNum(@Param("corpId")String corId, @Param("userId")String userId);

    String selectExternalLabelUserIdById(@Param("id")String id);

    void insertExternalLabelSelectiveBatch(List<RmExternalLabel> rmExternalLabelList);

    List<Map> selectExternalQywxLabelByExternalLabel(RmExternalLabel rmExternalLabel);

    RmExternalLabel selectExternalLabelByAllConditions(@Param("externalUserId") String externalUserId,
                                                       @Param("corpId") String corpId,
                                                       @Param("labelId") String labelId,
                                                       @Param("groupId") String groupId,
                                                       @Param("userId") String userId);

    void deleteByLabelIdAndlabelGroupId(@Param("labelId") String labelId, @Param("corpId") String corpId, @Param("labelGroupId") String labelGroupId);
}

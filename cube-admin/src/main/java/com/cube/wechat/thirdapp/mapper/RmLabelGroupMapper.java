package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmLabelGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RmLabelGroupMapper {
    int deleteByPrimaryKey(String id);

    List<RmLabelGroup> selectLabelGroupByGroupName(RmLabelGroup rmLabelGroup);

    int insert(RmLabelGroup record);

    int insertSelective(RmLabelGroup record);

    RmLabelGroup selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(RmLabelGroup record);

    void insertBatchInsertLabelGroupSelective(List<RmLabelGroup> list);

    void updateBatchRmLabelGroupByPrimaryKeySelective(List<RmLabelGroup> list);
    RmLabelGroup selectLabelGroupByGroupId(RmLabelGroup rmLabelGroup);

    List<RmLabelGroup> selectLabelGroupByCorpId(RmLabelGroup rmLabelGroup);

    String selectLabelGroupNameById(String id);


    RmLabelGroup selectInitializeAntifraudLabel();

    List<RmLabelGroup> selectByQywxGroupIdAndCorpId(@Param("qywxTagGroupId") String qywxTagGroupId, @Param("corpId") String corpId, @Param("groupName") String groupName);

    int updateByGroupIdAndCorpId(RmLabelGroup rmLabelGroup);
}

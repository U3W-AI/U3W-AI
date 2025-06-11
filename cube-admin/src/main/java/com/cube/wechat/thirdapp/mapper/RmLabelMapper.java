package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmLabel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RmLabelMapper {
    int deleteByPrimaryKey(String id);


    int insertSelective(RmLabel record);

    RmLabel selectByPrimaryKey(String id);

    List<RmLabel> selecAllLabelByCorpId(@Param("corpId")String corpI);

    List<RmLabel> selectLabelByLabelGroupByLabelName(RmLabel rmLabel);

    int updateByPrimaryKeySelective(RmLabel record);

    int updateByGroupId(RmLabel rmLabel);

    void insertBatchRmLabel(List<RmLabel> rmLabelList);

    void updateBatchRmLabel(List<RmLabel> rmLabelList);
    RmLabel selectLabelByLabelId(RmLabel rmLabel);


    List<RmLabel> selectLabelByGroupIdAndCorpId(RmLabel rmLabel);

    List<RmLabel> getLabelByName(RmLabel rmLabel);

    List<RmLabel> selectInitializeAntifraudLabel(@Param("groupId") String groupId);

    RmLabel selectAntifraudLabel(@Param("corpId") String corpId, @Param("labelName") String labelName);

    List<RmLabel> selectByQywxLabelIdAndCropId(@Param("tagId") String tagId, @Param("corpId") String corpId, @Param("tagName") String tagName);

    /**
     * 根据标签名称查询标签
     * @return
     */
     List<String> selectByLabelNameAndCorpIdAndGroupName(@Param("labelNameList") List<String>  labelNameList, @Param("corpId") String corpId, @Param("groupName") String groupName);

    List<RmLabel>  selectLabelNames(@Param("labelNames") List<String> labelNames, @Param("corpId") String corpId);
}

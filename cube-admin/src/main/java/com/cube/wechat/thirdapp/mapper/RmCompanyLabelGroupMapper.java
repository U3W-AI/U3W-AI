package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmCompanyLabelGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author 张云龙
 */
@Mapper
public interface RmCompanyLabelGroupMapper {

    int insertSelective(RmCompanyLabelGroup rmCompanyLabelGroup);

    List<RmCompanyLabelGroup> selectLabelGroupByGroupName(RmCompanyLabelGroup rmCompanyLabelGroup);

    RmCompanyLabelGroup selectByPrimaryKey(String groupId);


    int updateByPrimaryKeySelective(RmCompanyLabelGroup rmCompanyLabelGroup);

    List<RmCompanyLabelGroup> selectLabelGroupByCorpId(@Param("corpId") String corpId);
}

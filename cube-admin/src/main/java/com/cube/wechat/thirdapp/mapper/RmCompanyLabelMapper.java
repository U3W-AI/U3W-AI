package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmCompanyLabel;
import com.cube.wechat.thirdapp.entiy.RmLabel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author 张云龙
 */
@Mapper
public interface RmCompanyLabelMapper {


    int insertSelective(RmCompanyLabel rmCompanyLabel);



    int deleteByPrimaryKey(String id);


    RmCompanyLabel selectByPrimaryKey(String id);


    int updateByPrimaryKeySelective(RmCompanyLabel RmCompanyLabel);

    int updateByGroupId(RmCompanyLabel rmCompanyLabel);




    List<RmCompanyLabel> getLabelByName(RmLabel rmLabel);

    List<RmCompanyLabel> selectLabelNames(@Param("labelNames") List<String> labelNames, @Param("corpId") String corpId);

    List<RmCompanyLabel> selectByGroupId(@Param("groupId") String groupId, @Param("corpId") String corpId);

    List<RmCompanyLabel> selectByIds(@Param("labelIds") List<String> labelIds, @Param("corpId") String corpId);

    List<RmCompanyLabel> selectAllLabelByCorpId(@Param("corpId") String corpId);
}

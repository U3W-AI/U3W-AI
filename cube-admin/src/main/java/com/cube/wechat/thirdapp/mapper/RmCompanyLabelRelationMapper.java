package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmCompanyLabelRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author 张云龙
 */
@Mapper
public interface RmCompanyLabelRelationMapper {


    int insertSelective(RmCompanyLabelRelation rmCompanyLabelRelation);

    int updateByPrimaryKeySelective(RmCompanyLabelRelation rmCompanyLabelRelation);

    int deleteByPrimaryKey(String id);

    RmCompanyLabelRelationMapper selectByPrimaryKey(String id);

    void saveBatch(@Param("list") List<RmCompanyLabelRelation> rmCompanyLabelRelations);

    void deleteCompanyLabelRelation(@Param("companyId")String companyId);

    void deleteByCompanyIdAndCorpId(@Param("companyId") String companyId, @Param("corpId") String corpId);

    List<RmCompanyLabelRelation> selectByCompanyIdAndCorpId(@Param("list") List<String> companyIds, @Param("corpId") String corpId);

    List<String> selectCompanyIdsByLabelIds(@Param("list") List<String> labelIds, @Param("corpId") String corpId);

    void deleteByGroupIdAndCorpId(@Param("groupId") String groupId, @Param("corpId") String corpId);

    void deleteByLabelIdAndCorpId(@Param("labelId") String labelId, @Param("corpId") String corpId);
}

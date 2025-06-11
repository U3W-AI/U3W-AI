package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmCompanyManage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author 张云龙
 */
@Mapper
public interface RmCompanyManageMapper {

    int insertSelective(RmCompanyManage rmCompanyManage);

    int updateByPrimaryKeySelective(RmCompanyManage rmCompanyManage);

    int deleteByPrimaryKey(String id);

    RmCompanyManage selectByPrimaryKey(String id);

    void saveBatch(@Param("list") List<RmCompanyManage> rmCompanyManages);

    void deleteByCompanyIdAndCorpId(@Param("companyId") String companyId, @Param("corpId") String corpId);

    List<RmCompanyManage> selectByCompanyIdAndCropId(@Param("companyId") String companyId, @Param("corpId") String corpId);
}

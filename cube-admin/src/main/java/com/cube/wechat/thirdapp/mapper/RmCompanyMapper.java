package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmCompany;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @author 张云龙
 */
@Mapper
public interface RmCompanyMapper {

    int insertSelective(RmCompany rmCompany);

    int updateByPrimaryKeySelective(RmCompany rmCompany);

    int deleteByPrimaryKey(String id);

    RmCompany selectByPrimaryKey(String id);

    List<RmCompany> selectAllByCorpId(@Param("corpId") String corpId);

    List<String> selectAllCompanyIdsByCorpId(@Param("corpId") String corpId);


    List<RmCompany> selectByFullName(@Param("companyFullName") String companyFullName,@Param("corpId") String corpId);
//    RmCompany selectByFullNameAndCompanyId(@Param("companyFullName") String companyFullName, @Param("companyId") String companyId);

    List<RmCompany> selectByShortName(@Param("companyShortName") String companyShortName,@Param("corpId") String corpId);

    List<String> selectLikeName(@Param("name") String name,@Param("corpId") String corpId);
//    RmCompany selectByShortNameAndCompanyId(@Param("companyShortName") String companyShortName,@Param("companyId") String companyId);

    List<Map> selectCompanyExxternalInfoByCorpId(@Param("corpId") String corpId);


    List<Map> selectCompanyTagInfoByCompanyId(@Param("corpId") String corpId,@Param("limit") Integer limit);

    void saveBatch(@Param("companies")List<RmCompany> companies);
}

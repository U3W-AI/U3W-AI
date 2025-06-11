package com.cube.wechat.thirdapp.mapper;

import com.cube.wechat.thirdapp.entiy.RmCompanyExternal;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * @author 张云龙
 */
@Mapper
public interface RmCompanyExternalMapper {


    int insertSelective(RmCompanyExternal record);

    int updateByPrimaryKeySelective(RmCompanyExternal record);


    RmCompanyExternal selectByCorpIdAndCompanyIdAndUserIdAndExternalUserId(@Param("corpId") String corpId, @Param("companyId") String companyId, @Param("userId") String userId, @Param("externalUserId") String externalUserId);
}

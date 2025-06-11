package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmRole;
import com.cube.wechat.thirdapp.entiy.RmUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RmRoleMapper {
    int deleteByPrimaryKey(String id);

    int insert(RmRole record);

    RmRole selectRoleByRoleCode(@Param("roleCode")String roleCode);

    List<RmRole> selectAllRole(RmRole rmRole);

    RmRole selectRoleByRoleName(@Param("roleName")String roleName);
    int insertSelective(RmRole record);

    RmRole selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(RmRole record);

    int updateByPrimaryKey(RmRole record);

    /**
     * 查询企业下的所有管理员
     */
    List<String> selectAllAdminRole(@Param("corpId") String corpId);

    /**
     * 查询企业下某个成员是否为管理员
     */
    RmRole selectByCorpIdAndUserId(@Param("corpId") String corpId, @Param("userId") String userId);

    List<RmRole> selectUserRoleByUser(RmUserRole rmUserRole);
}

package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.RmUserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RmUserRoleMapper {
    int deleteByPrimaryKey(String id);

    int insert(RmUserRole record);

    void deleteUserRoleByCorpIdAndRoleId(@Param("corpId")String corpId,@Param("roleId")String roleId);

    int insertSelective(RmUserRole record);

    RmUserRole selectByPrimaryKey(String id);

    int updateByPrimaryKeySelective(RmUserRole record);

    int updateByPrimaryKey(RmUserRole record);

    List<String> selectUserRoleByRoleId(@Param("roleId")String roleId);

    List<RmUserRole> selectUserRoleByUserId(RmUserRole rmUserRole);

    List<Map> selectUserRoleResources(RmUserRole rmUserRole);

    int synSysRole();
}

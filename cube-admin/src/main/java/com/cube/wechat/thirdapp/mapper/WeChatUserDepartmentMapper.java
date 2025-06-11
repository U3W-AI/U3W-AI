package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.WeChatHierarchicalReturnResult;
import com.cube.wechat.thirdapp.entiy.WeChatUserDepartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface WeChatUserDepartmentMapper {

    int insertSelective(WeChatUserDepartment record);
    void insertBatch(List<WeChatUserDepartment> list);
    List<WeChatUserDepartment> selectUserDepartByUserId(WeChatUserDepartment weChatUserDepartment);

    List<WeChatUserDepartment> selectUserDeptByParam(WeChatUserDepartment weChatUserDepartment);

    void deleteUserDepartment(WeChatUserDepartment weChatUserDepartment);
    List<String> queryPersonnelUnderTheDepartment(WeChatUserDepartment weChatUserDepartment);
    void deleteUserDepartmentByUserId(WeChatUserDepartment weChatUserDepartment);

    void deleteUserDeparementByDepartmentId(WeChatUserDepartment weChatUserDepartment);

    List<WeChatUserDepartment> selectAllUserDepartmentByCorpId(@Param("corpId")String corpId,@Param("suiteId")String suiteId);

    List<WeChatHierarchicalReturnResult> selectUserListByCorpId(WeChatUserDepartment weChatUserDepartment);

    List<Map> selectUserDepartmentListByCorpId(WeChatUserDepartment weChatUserDepartment);

    List<String> selectUserDepartmentByDepartmentId(@Param("corpId")String corpId, @Param("suiteId")String suiteId, @Param("departmentId")String departmentId);
}

package com.cube.wechat.thirdapp.mapper;


import com.cube.wechat.thirdapp.entiy.WeChatCorpDepartment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface WeChatCorpDepartmentMapper {
    int deleteByPrimaryKey(String id);

    void updateStautsByCorpIdAndSuiteId(@Param("departmentStatus")String departmentStatus,@Param("corpId")String corpId,@Param("suiteId")String suiteId);
    int insertSelective(WeChatCorpDepartment record);
    void insertBatch(List<WeChatCorpDepartment> list);

    WeChatCorpDepartment selectDepartmentByIdAndSuiteIdAndCorpId(WeChatCorpDepartment record);

    WeChatCorpDepartment selectByPrimaryKey(String id);

    List<WeChatCorpDepartment> selectAllDepartmentByCorpId(@Param("corpId")String corpId,@Param("suiteId")String suiteId);

    List<Map> selectTreeUserDepartment(WeChatCorpDepartment qywxCorpDepartment);



    int updateByPrimaryKeySelective(WeChatCorpDepartment record);

    void updateBatchByPrimaryKey(List<WeChatCorpDepartment> list);

    void updateDepartmentFullPath(@Param("corpId")String corpId);

    List<Map> selectPrimaryDepartment(Map map);
    List<Map> selectSubordinateDepartments(Map map);
    List<Map> selectSubordinatePersonnel(Map map);
    List<Map> selectPaimaryDepartmentUser(Map map);

    List<String> selectAllDepartmentIdByCorpId(@Param("corpId")String corpId,@Param("suiteId")String suiteId,@Param("departmentParentId")String departmentParentId);





}

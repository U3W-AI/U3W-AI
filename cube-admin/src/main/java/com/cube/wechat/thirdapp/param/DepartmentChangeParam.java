package com.cube.wechat.thirdapp.param;

import lombok.Data;

/**
 @author sjl
  * @Created date 2024/3/8 09:30
 */
@Data
public class DepartmentChangeParam {
    //应用id
    private String suiteId;
    //变更信息的部门id
    private String departmentId;
    //上级部门id
    private String parentId;
    //排序号
    private Integer order;
    //授权企业的CorpID
    private String corpId;
    //变更类型  create_party 新增部门 update_party 更新部门 delete_party 删除部门
    private String changeType;
}

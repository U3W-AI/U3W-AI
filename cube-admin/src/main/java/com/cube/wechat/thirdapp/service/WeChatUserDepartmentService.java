package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatHierarchicalReturnResult;
import com.cube.wechat.thirdapp.entiy.WeChatUserDepartment;

import java.util.List;
import java.util.Map;

/**
 @author sjl
  * @Created date 2024/3/4 11:09
 */
public interface WeChatUserDepartmentService {
    public R deleteUserDepartment(WeChatUserDepartment weChatUserDepartment);

    public R<Map> synchronizeUserDepartment(String corpId,String suiteId);

    public R<List<String>> queryPersonnelUnderTheDepartment(WeChatUserDepartment weChatUserDepartment);

    public R<List<WeChatHierarchicalReturnResult>> selectUserListByCorpId(WeChatUserDepartment weChatUserDepartment);


    public R<List<Map>> selectUserDepartmentListByCorpId(WeChatUserDepartment weChatUserDepartment);

}

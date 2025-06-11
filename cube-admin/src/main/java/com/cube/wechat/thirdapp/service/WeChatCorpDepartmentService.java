package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatCorpDepartment;
import com.cube.wechat.thirdapp.param.WeChatCorpDepartmentParam;

import java.util.List;
import java.util.Map;

/**
 * @author sjl
 * @Created date 2024/3/1 09:58
 */
public interface WeChatCorpDepartmentService {
    public R saveCorpDepartment(List<WeChatCorpDepartmentParam>  qywxCorpDepartmentParamList);

    public R updateCorpDepartment(WeChatCorpDepartment weChatCorpDepartment);

    public R deleteCorpDepartment(WeChatCorpDepartment weChatCorpDepartment);

    public R deleteAllCorpDepartment(WeChatCorpDepartment weChatCorpDepartment);

    public R<List<Map>> selectTreeUserDepartment(WeChatCorpDepartment weChatCorpDepartment);

    public R<List<Map>> selectPrimaryDepartment(Map map);
    public R<List<Map>> selectMainDepartment(Map map);

    public R<List<Map>> selectManagetDepartment(Map map);


    public R selectAllDepartment(String corpId,String suiteId);

}

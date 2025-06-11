package com.cube.wechat.thirdapp.param;

import com.cube.wechat.thirdapp.entiy.WeChatCorpDepartment;
import lombok.Data;

import java.util.List;

/**
 * @author sjl
 * @Created date 2024/3/1 10:05
 */
@Data
public class WeChatCorpDepartmentParam {
    private String corpId;
    private String suiteId;
    private List<WeChatCorpDepartment> qywxCorpDepartmentList;
}

package com.cube.wechat.thirdapp.param;

import com.cube.wechat.thirdapp.entiy.CompanyInfoParam;
import lombok.Data;

import java.util.List;

/**
 * @author 张云龙
 */
@Data
public class CompanyParam {

    /**
     * 企业id
     */
    private String corpId;

    /**
     * 标签id
     */
    private List<String> labelIds;

    /**
     * 公司id
     */
    private String companyId;

    /**
     *
     */
    private List<ManageParam> manageList;

    /**
     * 信息字段值
     */
    private List<CompanyInfoParam> infoList;

    /**
     * 企业全称
     */
    private String companyFullName;

    /**
     * 企业简称
     */
    private String companyShortName;
    /**
     * 企业Logo
     */
    private String companyLogo;


    /**
     * 统一社会信用代码
     */
    private String creditCode;

    private String userId;


}

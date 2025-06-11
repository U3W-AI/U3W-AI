package com.cube.wechat.thirdapp.entiy;

import lombok.Data;

@Data
public class CompanyInfoParam {

    /**
     * rm_company_info_archives  表的主键
     */
    private String id;

    /**
     * rm_company_info_archives  info_id
     */
    private String infoId;

    private String infoValue;
}

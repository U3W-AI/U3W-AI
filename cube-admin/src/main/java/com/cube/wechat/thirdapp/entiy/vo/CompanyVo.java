package com.cube.wechat.thirdapp.entiy.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author 张云龙
 */
@Data
public class CompanyVo implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    /**
     * 企业简称
     */
    private String companyShortName;

    /**
     * 企业全称
     */
    private String companyFullName;
    /**
     * 企业logo
     */
    private String companyLogo;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 创建人
     */
    private String createBy;

    private List<Map> infoMaps;

    /**
     * 简称
     */
    private List<Map> labelNames;





}

package com.cube.wechat.thirdapp.param;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

/**
 * @author 张云龙
 */
@Data
public class CompanyExcel {

    @ExcelProperty("企业全称")
    private String companyFullName;

    @ExcelProperty("企业简称")
    private String companyShortName;

    @ExcelProperty("是否驻场单位")
    private String residentUnit;

    @ExcelProperty("上级单位")
    private String superiorUnit;

    /**
     * 标签  以英文 , 隔开
     */
    @ExcelProperty("单位类型")
    private String unitType;

    @ExcelProperty("驻场详细地址")
    private String fullAddress;

    @ExcelProperty("24小时值班电话")
    private String dutyPhone;

    @ExcelProperty("法人代表")
    private String legalRepresentative;

    @ExcelProperty("法人代表手机号")
    private String legalRepresentativePhone;

    @ExcelProperty("主要负责人")
    private String principalPerson;

    @ExcelProperty("主要负责人手机号")
    private String principalPersonPhone;

    @ExcelProperty("治安联络员")
    private String liaisonOfficer;

    @ExcelProperty("治安联络员手机号")
    private String liaisonOfficerPhone;

    @ExcelProperty("通行证专办员")
    private String passOfficer;

    @ExcelProperty("通行证专办员手机号")
    private String passOfficerPhone;

    @ExcelProperty("消防联络员")
    private String fireOfficer;

    @ExcelProperty("消防联络员手机号")
    private String fireOfficerPhone;

    @ExcelProperty("反恐联络员")
    private String counterTerrorismLiaison;

    @ExcelProperty("反恐联络员手机号")
    private String counterTerrorismLiaisonPhone;

}

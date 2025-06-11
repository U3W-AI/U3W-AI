package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.param.CompanyExcel;
import com.cube.wechat.thirdapp.param.CompanyParam;
import com.cube.wechat.thirdapp.param.GetCompanyParam;

import java.util.List;
import java.util.Map;

/**
 * @author 张云龙
 */
public interface CompanyService {

    /**
     * 保存企业信息
     * @param companyParam 入参
     * @return R对象
     */
    R saveCompany(CompanyParam companyParam);

    R updateCompany(CompanyParam companyParam);
    R getCompanyList(GetCompanyParam companyParam);

    R getCompanyById(String id);

    R getCompanyByExternalUserId(String externalUserId);


    R saveExcelData(List<CompanyExcel> companyExcelList) throws NoSuchFieldException;

    R schedSaveCompanyExternalRation(Map req);
}

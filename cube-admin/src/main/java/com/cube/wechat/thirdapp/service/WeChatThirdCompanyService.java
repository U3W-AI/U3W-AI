package com.cube.wechat.thirdapp.service;

import com.cube.common.core.domain.R;
import com.cube.wechat.thirdapp.entiy.WeChatThirdCompany;

import java.util.List;
import java.util.Map;

public interface WeChatThirdCompanyService {

    public R saveCompanyInfo(WeChatThirdCompany qywxThirdCompany);

    public R updateCompanyStatus(WeChatThirdCompany qywxThirdCompany);

    public R<WeChatThirdCompany> selectCompanyInfo(WeChatThirdCompany qywxThirdCompany);

    public R<String> selectCorpServerName(Map map);

    public R<List<WeChatThirdCompany>> selectAllCompany(WeChatThirdCompany qywxThirdCompany);
}

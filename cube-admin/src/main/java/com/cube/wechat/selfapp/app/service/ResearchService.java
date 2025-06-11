package com.cube.wechat.selfapp.app.service;

import com.cube.wechat.selfapp.app.domain.Research;
import com.cube.wechat.selfapp.wecom.util.ResultBody;

public interface ResearchService {

    /**
     * 查询所有研报列表
     * */
    ResultBody getReportList(Research research);


    /**
     * 查询研报详情
     * */
    ResultBody getReportDetail(String resId);

    /**
     * 上传研报
     * */
    ResultBody addReport(Research research);

    /**
     * 修改研报
     * */
    ResultBody updateReport(Research research);

    /**
    * 审核研报
    * */
    ResultBody changeResportFlowStatus(Research research);

    /**
     * 获取研报运营数据
     * */
    ResultBody getResOpeData(Research research);
}

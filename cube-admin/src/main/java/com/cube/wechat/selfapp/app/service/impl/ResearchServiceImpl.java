package com.cube.wechat.selfapp.app.service.impl;

import com.cube.wechat.selfapp.app.domain.Research;
import com.cube.wechat.selfapp.app.mapper.ResearchMapper;
import com.cube.wechat.selfapp.app.service.ResearchService;
import com.cube.wechat.selfapp.wecom.util.ResultBody;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年10月23日 10:17
 */

@Service
public class ResearchServiceImpl implements ResearchService {

    @Autowired
    private ResearchMapper researchMapper;

    /**
     * 查询所有研报列表
     * */
    @Override
    public ResultBody getReportList(Research research) {
        PageHelper.startPage(research.getPage(),research.getLimit());
        List<Map> list =  researchMapper.getReportList(research);
        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
    }

    /**
     * 查询研报详情
     * */
    @Override
    public ResultBody getReportDetail(String resId) {
        Map map =  researchMapper.getReportDetail(resId);
        if(map.get("tag")!=null){
            map.put("tag",map.get("tag").toString().split(","));
        }
        return ResultBody.success(map);
    }

    /**
     * 上传研报
     * */
    @Override
    public ResultBody addReport(Research research) {
        researchMapper.addReport(research);
        return ResultBody.success("上传成功");
    }

    /**
     * 修改研报
     * */
    @Override
    public ResultBody updateReport(Research research) {
        researchMapper.updateReport(research);
        return ResultBody.success("修改成功");
    }

    /**
     * 审核研报
     * */
    @Override
    public ResultBody changeResportFlowStatus(Research research) {
        researchMapper.changeResportFlowStatus(research);
        return ResultBody.success("上传成功");
    }

    /**
     * 获取研报运营数据
     * */
    @Override
    public ResultBody getResOpeData(Research research) {
        PageHelper.startPage(research.getPage(),research.getLimit());
        List<Map> list = new ArrayList<>();
        if(research.getDataType().equals(1)){
            list = researchMapper.getResDownRecord(research.getId());
        }else if (research.getDataType().equals(2)){
            list = researchMapper.getResCollectionRecord(research.getId());
        }else if (research.getDataType().equals(3)){
            list = researchMapper.getResBrowseRecord(research.getId());
        }

        PageInfo pageInfo = new PageInfo(list);
        return ResultBody.success(pageInfo);
    }
}

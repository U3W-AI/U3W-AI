package com.cube.wechat.selfapp.app.mapper;

import com.cube.wechat.selfapp.app.domain.Research;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

@Mapper
public interface ResearchMapper {

    List<Map> getReportList(Research research);

    Map getReportDetail(String resId);

    int addReport(Research research);

    int updateReport(Research research);

    int changeResportFlowStatus(Research research);

    /*
    * 查询研报下载记录
    * */
    List<Map> getResDownRecord(String resId);

    /*
    * 查询研报收藏记录
    * */
    List<Map> getResCollectionRecord(String resId);

    /*
    * 查询研报浏览记录
    * */
    List<Map> getResBrowseRecord(String resId);

}

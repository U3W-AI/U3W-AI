package com.wx.fbsir.business.service;

import com.wx.fbsir.business.domain.InterviewRecord;
import com.wx.fbsir.business.mapper.InterviewMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 面试记录业务处理层
 * * @author WxFbsir Team
 * @date 2026-01-22
 */
@Service
public class InterviewService {

    @Autowired
    private InterviewMapper interviewMapper;

    /**
     * 获取今日面试摘要
     * * @return 格式化后的文本消息
     */
    public String getTodaySummary() {
        List<InterviewRecord> todayList = interviewMapper.selectTodayRecords();

        if (todayList.isEmpty()) {
            return "今日暂无新增面试记录。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("今日已记录面试：").append(todayList.size()).append(" 人\n\n");
        for (int i = 0; i < todayList.size(); i++) {
            sb.append(i + 1).append(". ").append(todayList.get(i).toSimpleString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取最近 N 条记录
     * * @param limit 查询数量
     * @return 格式化后的详情消息
     */
    public String getRecentRecords(int limit) {
        if (limit > 5) {
            return "一次最多只能查询 5 条记录哦！";
        }
        if (limit <= 0) {
            return "请输入正确的数字（1-5）。";
        }

        List<InterviewRecord> recentList = interviewMapper.selectRecentRecords(limit);

        if (recentList.isEmpty()) {
            return "暂无数据。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("最近 ").append(recentList.size()).append(" 条面试记录：\n");

        for (int i = 0; i < recentList.size(); i++) {
            sb.append("\n");
            sb.append(i + 1).append(". ");
            sb.append(recentList.get(i).toDetailString()).append("\n");
        }
        return sb.toString();
    }
}
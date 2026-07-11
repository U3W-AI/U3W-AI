package com.wx.fbsir.business.interviewbot.mapper;

import com.wx.fbsir.business.interviewbot.domain.InterviewRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 面试记录数据访问层
 * * @author FBSir Team
 * @date 2026-01-22
 */
@Mapper
public interface InterviewMapper {

    /**
     * 查询今日面试记录
     * * @return 今日记录列表
     */
    @Select("SELECT * FROM interview_record WHERE DATE(interview_time) = CURDATE() ORDER BY interview_time DESC")
    @Results({
            @Result(property = "interviewTime", column = "interview_time"),
            @Result(property = "phoneNumber", column = "phone_number"),
            @Result(property = "idCard", column = "id_card"),
            @Result(property = "techStack", column = "tech_stack"),
            @Result(property = "documentId", column = "document_id")
    })
    List<InterviewRecord> selectTodayRecords();

    /**
     * 查询最近 N 条记录
     * * @param limit 限制条数
     * @return 最近记录列表
     */
    @Select("SELECT * FROM interview_record WHERE interview_time <= NOW() ORDER BY interview_time DESC LIMIT #{limit}")
    @Results({
            @Result(property = "interviewTime", column = "interview_time"),
            @Result(property = "phoneNumber", column = "phone_number"),
            @Result(property = "idCard", column = "id_card"),
            @Result(property = "techStack", column = "tech_stack"),
            @Result(property = "documentId", column = "document_id")
    })
    List<InterviewRecord> selectRecentRecords(int limit);
}
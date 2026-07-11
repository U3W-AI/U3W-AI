package com.wx.fbsir.business.resume.service;


import com.wx.fbsir.business.resume.domain.ParseResult;

import java.util.Map;

public interface ResumeManagementService {


    /**
     * 存储简历
     * @param resumeId
     * @param resumeName
     * @param url
     */
    void storageResume(String resumeId, String resumeName, String url,Long userId);

    /**
     * 查询用户是否有简历
     * @param userId
     * @return
     */
    Map<String, Object> hasResume(Long userId);

    /**
     * 更新简历内容
     * @param resumeId
     * @param resumeName
     * @param url
     * @param userId
     */
    void updataResume(String resumeId, String resumeName, String url, Long userId);

    /**
     * 解析简历
     * @param userId
     */
    void parseResume(Long userId);

    /**
     * 获取简历状态，并返回简历内容
     * @param userId
     * @return
     */
    ParseResult getResumeStatus(Long userId);

    /**
     * 回调更新数据库
     * @param userId
     * @param parseContent
     * @param statusCode
     * @return
     */
    int updataResumeParseResult(String userId, ParseResult parseContent,int statusCode);

    /**
     * 更新简历访问码设置
     * @param available
     * @param userId
     */
    void updateAccessCodeAvailable(Integer available, Long userId);
}

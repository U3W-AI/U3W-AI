package com.wx.fbsir.business.resume.service;

import com.wx.fbsir.business.resume.domain.ResumeAccessCode;
import com.wx.fbsir.common.core.page.TableDataInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Date;

public interface ResumeShortLinkService {
    /**
     * 简历路由跳转
     * @param shortlink 链接
     * @param request
     * @param response
     */
    void restoreUrl(String shortlink,String accessCode, HttpServletRequest request, HttpServletResponse response);

    /**
     * 获取访问码列表
     * @param pageNum
     * @param pageSize
     * @return
     */
    TableDataInfo getAllAccessCode(Integer pageNum, Integer pageSize,Long userId);

    /**
     * 新增访问码
     * @param resumeAccessCode
     * @param userId
     */
    void addAccessCode(ResumeAccessCode resumeAccessCode, Long userId);

    /**
     * 获取监控的链接访问数据
     * @param beginTime
     * @param endTime
     * @param userId
     * @return
     */
    TableDataInfo getLinkAccessData(Date beginTime, Date endTime, Long userId);

    /**
     * 获取简历链接访问日志
     * @param pageNum
     * @param pageSize
     * @param userId
     * @return
     */
    TableDataInfo getLinkAccessLog(Integer pageNum, Integer pageSize, Long userId);

    /**
     * 修改链接截止时间
     * @param time
     * @param userId
     */
    void updateDeadLine(Date time, Long userId);
}

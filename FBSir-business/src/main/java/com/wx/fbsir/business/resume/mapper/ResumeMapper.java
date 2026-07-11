package com.wx.fbsir.business.resume.mapper;

import com.wx.fbsir.business.resume.domain.*;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public interface ResumeMapper {
    /**
     * 查询简历是否存在
     * @param userId
     * @return
     */
    public CV ResumeExists(Long userId);

    /**
     * 插入简历数据
     * @param cv
     * @return
     */
    public int insertResume(CV cv);

    /**
     * 更新简历
     * @param cv
     */
    void updataResume(CV cv);

    /**
     * 更新简历状态
     * @param userId
     * @param parseStatus
     */
    void updataParseStatus(Long userId, int parseStatus);

    /**
     * 根据userId，查询简历
     * @param userId
     * @return
     */
    CV selectResumeByUserId(Long userId);

    /**
     * 得到简历解析结果
     * @param userId
     * @return
     */
    ParseResult getParseResult(Long userId);

    /**
     * 回调更新数据库
     * @param userId
     * @param parseContent
     * @param statusCode
     * @return
     */
    int updataParseResult(@Param("userId")String userId, @Param("parseContent")ParseResult parseContent,@Param("statusCode")int statusCode);

    Long findCVcountByShortLink(String shortlink);


    /**
     * 根据短链，查找简历
     * @param shortlink
     * @return
     */
    CV findResumeByShortLink(String shortlink);

    /**
     * 根据userId，获取访问码总数
     * @param userId
     * @return
     */
    Long getTotalAccessCodeByUserId(Long userId);

    /**
     * 根据userId，获取所有访问码
     * @param offset
     * @param pageSize
     * @param userId
     * @return
     */
    List<ResumeAccessCode> getAllAccessCodeByUserId(Integer offset , Integer pageSize, Long userId);

    /**
     * 根据短链，新增访问码
     * @param resumeAccessCode
     * @param short_link
     */
    void addAccessCode(@Param("resumeAccessCode")ResumeAccessCode resumeAccessCode, @Param("short_link")String short_link);

    /**
     * 根据userid，查询链接
     * @param userId
     * @return
     */
    String selectShortLinkByUserId(Long userId);

    /**
     * 查询链接访问码是否有效，并减一
     * @param shortlink
     * @param accessCode
     * @return
     */
    int getAccessibleCountByCode(String shortlink, String accessCode);

    /**
     * 根据参数（联合索引）插入数据，存在则加1
     * @param shortlink
     * @param now
     */
    void addVisitDate(String shortlink, LocalDate now);

    /**
     * 链接访问日志记录
     * @param resumeAccessLog
     */
    void addAccessLog(ResumeAccessLog resumeAccessLog);

    /**
     * 根据参数获取简历访问数据
     * @param beginTime
     * @param endTime
     * @param shortlink
     * @return
     */
    List<ResumeMonitor> getLinkAccessData(Date beginTime, Date endTime, String shortlink);

    /**
     * 根据短链，查询日志总数
     * @param shortlink
     * @return
     */
    Long getTotalAccessLog(String shortlink);

    /**
     * 分页查询日志记录
     * @param offset
     * @param pageSize
     * @param shortlink
     * @return
     */
    List<ResumeAccessLog> getAllAccessLog(Integer offset , Integer pageSize, String shortlink);

    /**
     * 修改链接截止时间
     * @param time
     * @param userId
     */
    void updateDeadLine(Date time, Long userId);
}

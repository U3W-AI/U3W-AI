package com.wx.fbsir.business.gitee.service;

import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Gitee使用统计定时任务
 *
 * @author FBSir
 * @date 2026-01-03
 */
@Component
public class GiteeUsageReportScheduler {
    private static final Logger log = LoggerFactory.getLogger(GiteeUsageReportScheduler.class);

    @Autowired
    private GiteeUsageReportService giteeUsageReportService;

    /**
     * 每日凌晨生成前一天的统计报表
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void generateDailyReport() {
        // 每天凌晨统计前一天数据，避免当天数据不完整
        LocalDate reportDate = LocalDate.now().minusDays(1);
        try {
            giteeUsageReportService.generateDailyReport(reportDate);
        } catch (Exception ex) {
            log.error("Failed to generate gitee usage report for {}", reportDate, ex);
        }
    }
}

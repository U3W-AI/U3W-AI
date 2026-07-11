package com.wx.fbsir.business.gitee.controller;

import com.wx.fbsir.business.gitee.domain.GiteeAnalysisReport;
import com.wx.fbsir.business.gitee.domain.GiteeBind;
import com.wx.fbsir.business.gitee.domain.GiteeUsageReport;
import com.wx.fbsir.business.gitee.domain.GiteeUserSummaryRequest;
import com.wx.fbsir.business.gitee.mapper.GiteeAnalysisReportMapper;
import com.wx.fbsir.business.gitee.mapper.GiteeBindMapper;
import com.wx.fbsir.business.gitee.service.GiteeUsageReportService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.common.utils.poi.ExcelUtil;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;

/**
 * Gitee后台管理Controller
 *
 * @author FBSir
 * @date 2026-01-03
 */
@RestController
@RequestMapping("/business/gitee/admin")
public class GiteeAdminController extends BaseController {

    @Autowired
    private GiteeBindMapper giteeBindMapper;

    @Autowired
    private GiteeAnalysisReportMapper giteeAnalysisReportMapper;

    @Autowired
    private GiteeUsageReportService giteeUsageReportService;

    /**
     * 批量查询用户Gitee绑定与评测摘要
     *
     * @param request 用户ID列表
     * @return 用户摘要集合
     */
    @PostMapping("/user-summary")
    public AjaxResult userSummary(@RequestBody GiteeUserSummaryRequest request) {
        if (request == null || CollectionUtils.isEmpty(request.getUserIds())) {
            return AjaxResult.success(Collections.emptyList());
        }
        List<Long> userIds = request.getUserIds().stream()
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return AjaxResult.success(Collections.emptyList());
        }

        Map<Long, GiteeBind> bindMap = giteeBindMapper.selectByUserIds(userIds).stream()
            .collect(Collectors.toMap(GiteeBind::getUserId, Function.identity(), (left, right) -> left));
        Map<Long, GiteeAnalysisReport> reportMap = giteeAnalysisReportMapper.selectLatestByUserIds(userIds).stream()
            .collect(Collectors.toMap(GiteeAnalysisReport::getUserId, Function.identity(), (left, right) -> left));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Long userId : userIds) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", userId);
            GiteeBind bind = bindMap.get(userId);
            if (bind != null) {
                item.put("giteeBound", true);
                item.put("giteeUsername", bind.getGiteeUsername());
                item.put("giteeUserId", bind.getGiteeUserId());
            } else {
                item.put("giteeBound", false);
            }

            GiteeAnalysisReport report = reportMap.get(userId);
            if (report != null) {
                item.put("totalScore", report.getTotalScore());
                item.put("totalLevel", report.getTotalLevel());
                item.put("reportTime", report.getReportTime());
            }
            result.add(item);
        }

        return AjaxResult.success(result);
    }

    /**
     * 查询Gitee模块使用统计列表
     *
     * @param report 统计筛选条件
     * @return 分页列表
     */
    @GetMapping("/usage-report/list")
    public TableDataInfo listUsageReports(GiteeUsageReport report) {
        startPage();
        List<GiteeUsageReport> list = giteeUsageReportService.selectGiteeUsageReportList(report);
        return getDataTable(list);
    }

    /**
     * 导出Gitee模块使用统计报表
     *
     * @param response 响应
     * @param report 统计筛选条件
     */
    @Log(title = "Gitee模块使用统计", businessType = BusinessType.EXPORT)
    @PostMapping("/usage-report/export")
    public void exportUsageReports(HttpServletResponse response, GiteeUsageReport report) {
        List<GiteeUsageReport> list = giteeUsageReportService.selectGiteeUsageReportList(report);
        ExcelUtil<GiteeUsageReport> util = new ExcelUtil<GiteeUsageReport>(GiteeUsageReport.class);
        util.exportExcel(response, list, "Gitee模块使用统计报表");
    }

    /**
     * 手动生成指定日期的Gitee使用统计
     *
     * @param reportDate 统计日期（默认昨天）
     * @return 操作结果
     */
    @PostMapping("/usage-report/generate")
    public AjaxResult generateUsageReport(@RequestParam(value = "reportDate", required = false)
                                          @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate reportDate) {
        LocalDate targetDate = reportDate != null ? reportDate : LocalDate.now().minusDays(1);
        giteeUsageReportService.generateDailyReport(targetDate);
        return AjaxResult.success();
    }
}

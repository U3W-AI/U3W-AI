package com.cube.web.controller.monitor;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import com.cube.common.utils.poi.ExcelUtil;
import com.cube.system.domain.LogInfo;
import com.cube.system.service.ILogInfoService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.cube.common.annotation.Log;
import com.cube.common.core.controller.BaseController;
import com.cube.common.core.domain.AjaxResult;
import com.cube.common.enums.BusinessType;
import com.cube.common.core.page.TableDataInfo;

/**
 * 日志信息（记录方法执行日志）Controller
 *
 * @author cube
 * @date 2025-08-08
 */
@RestController
@RequestMapping("/monitor/userLog")
public class SysUserLogController extends BaseController
{
    @Autowired
    private ILogInfoService logInfoService;

    /**
     * 查询日志信息（记录方法执行日志）列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:userLog:list')")
    @GetMapping("/list")
    public TableDataInfo list(LogInfo logInfo)
    {
        startPage();
        List<LogInfo> list = logInfoService.selectLogInfoList(logInfo);
        return getDataTable(list);
    }

    /**
     * 导出日志信息（记录方法执行日志）列表
     */
    @PreAuthorize("@ss.hasPermi('monitor:userLog:export')")
    @Log(title = "日志信息（记录方法执行日志）", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, LogInfo logInfo)
    {
        List<LogInfo> list = logInfoService.selectLogInfoList(logInfo);
        ExcelUtil<LogInfo> util = new ExcelUtil<LogInfo>(LogInfo.class);
        util.exportExcel(response, list, "日志信息（记录方法执行日志）数据");
    }

    /**
     * 获取日志信息（记录方法执行日志）详细信息
     */
    @PreAuthorize("@ss.hasPermi('monitor:userLog:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(logInfoService.selectLogInfoById(id));
    }

    /**
     * 新增日志信息（记录方法执行日志）
     */
    @PreAuthorize("@ss.hasPermi('monitor:userLog:add')")
    @Log(title = "日志信息（记录方法执行日志）", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody LogInfo logInfo)
    {
        return toAjax(logInfoService.insertLogInfo(logInfo));
    }

    /**
     * 修改日志信息（记录方法执行日志）
     */
    @PreAuthorize("@ss.hasPermi('monitor:userLog:edit')")
    @Log(title = "日志信息（记录方法执行日志）", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody LogInfo logInfo)
    {
        return toAjax(logInfoService.updateLogInfo(logInfo));
    }

    /**
     * 删除日志信息（记录方法执行日志）
     */
    @PreAuthorize("@ss.hasPermi('monitor:userLog:remove')")
    @Log(title = "日志信息（记录方法执行日志）", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(logInfoService.deleteLogInfoByIds(ids));
    }
}

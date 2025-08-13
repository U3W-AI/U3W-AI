package com.cube.web.controller.monitor;

import com.cube.common.annotation.Log;
import com.cube.common.core.controller.BaseController;
import com.cube.common.core.domain.AjaxResult;
import com.cube.common.core.page.TableDataInfo;
import com.cube.common.enums.BusinessType;
import com.cube.common.utils.poi.ExcelUtil;
import com.cube.system.domain.LogInfo;
import com.cube.system.service.ILogInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

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
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(logInfoService.selectLogInfoById(id));
    }

    /**
     * 新增日志信息（记录方法执行日志）
     */
    @Log(title = "日志信息（记录方法执行日志）", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody LogInfo logInfo)
    {
        return toAjax(logInfoService.insertLogInfo(logInfo));
    }

    /**
     * 修改日志信息（记录方法执行日志）
     */
    @Log(title = "日志信息（记录方法执行日志）", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody LogInfo logInfo)
    {
        return toAjax(logInfoService.updateLogInfo(logInfo));
    }

    /**
     * 删除日志信息（记录方法执行日志）
     */
    @Log(title = "日志信息（记录方法执行日志）", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(logInfoService.deleteLogInfoByIds(ids));
    }

    @Log(title = "日志信息（记录方法执行日志）", businessType = BusinessType.CLEAN)
    @DeleteMapping("/clean")
    public AjaxResult clean() {
        return toAjax(logInfoService.cleanLogInfo());
    }
}

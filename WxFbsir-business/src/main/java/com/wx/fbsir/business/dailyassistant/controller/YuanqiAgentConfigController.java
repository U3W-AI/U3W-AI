package com.wx.fbsir.business.dailyassistant.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.common.utils.poi.ExcelUtil;
import com.wx.fbsir.business.dailyassistant.domain.YuanqiAgentConfig;
import com.wx.fbsir.business.dailyassistant.service.IYuanqiAgentConfigService;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 腾讯元器智能体配置Controller
 *
 * @author wxfbsir
 * @date 2025-12-05
 */
@RestController
@RequestMapping("/system/yuanqi-config")
public class YuanqiAgentConfigController extends BaseController
{
    @Autowired
    private IYuanqiAgentConfigService yuanqiAgentConfigService;

    /**
     * 查询腾讯元器智能体配置列表
     */
    @GetMapping("/list")
    public TableDataInfo list(YuanqiAgentConfig yuanqiAgentConfig)
    {
        startPage();
        List<YuanqiAgentConfig> list = yuanqiAgentConfigService.selectYuanqiAgentConfigList(yuanqiAgentConfig);
        return getDataTable(list);
    }

    /**
     * 获取当前用户的启用配置
     * 
     * @param businessType 业务类型（可选，默认为daily_assistant）
     */
    @GetMapping("/myConfig")
    public AjaxResult myConfig(@RequestParam(value = "businessType", defaultValue = "daily_assistant") String businessType)
    {
        Long userId = getUserId();
        YuanqiAgentConfig config = yuanqiAgentConfigService.selectActiveConfigByUserId(userId, businessType);
        return success(config);
    }

    /**
     * 导出腾讯元器智能体配置列表
     */
    @Log(title = "腾讯元器智能体配置", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, YuanqiAgentConfig yuanqiAgentConfig)
    {
        List<YuanqiAgentConfig> list = yuanqiAgentConfigService.selectYuanqiAgentConfigList(yuanqiAgentConfig);
        ExcelUtil<YuanqiAgentConfig> util = new ExcelUtil<YuanqiAgentConfig>(YuanqiAgentConfig.class);
        util.exportExcel(response, list, "腾讯元器智能体配置数据");
    }

    /**
     * 获取腾讯元器智能体配置详细信息
     */
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(yuanqiAgentConfigService.selectYuanqiAgentConfigById(id));
    }

    /**
     * 新增腾讯元器智能体配置（保存前验证）
     */
    @Log(title = "腾讯元器智能体配置", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody YuanqiAgentConfig yuanqiAgentConfig)
    {
        // 保存前验证配置
        try {
            yuanqiAgentConfigService.verifyAgentConfig(
                yuanqiAgentConfig.getAgentId(),
                yuanqiAgentConfig.getApiKey(),
                yuanqiAgentConfig.getApiEndpoint()
            );
        } catch (Exception e) {
            return error(e.getMessage());
        }
        
        yuanqiAgentConfig.setUserId(getUserId());
        yuanqiAgentConfig.setCreateBy(getUsername());
        return toAjax(yuanqiAgentConfigService.insertYuanqiAgentConfig(yuanqiAgentConfig));
    }

    /**
     * 修改腾讯元器智能体配置（保存前验证）
     */
    @Log(title = "腾讯元器智能体配置", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody YuanqiAgentConfig yuanqiAgentConfig)
    {
        // 如果用户修改了敏感字段（不是脱敏标记），则验证配置
        String MASKED_VALUE = "***已加密***";
        if (!MASKED_VALUE.equals(yuanqiAgentConfig.getAgentId()) || 
            !MASKED_VALUE.equals(yuanqiAgentConfig.getApiKey())) {
            try {
                yuanqiAgentConfigService.verifyAgentConfig(
                    yuanqiAgentConfig.getAgentId(),
                    yuanqiAgentConfig.getApiKey(),
                    yuanqiAgentConfig.getApiEndpoint()
                );
            } catch (Exception e) {
                return error(e.getMessage());
            }
        }
        
        yuanqiAgentConfig.setUpdateBy(getUsername());
        return toAjax(yuanqiAgentConfigService.updateYuanqiAgentConfig(yuanqiAgentConfig));
    }

    /**
     * 删除腾讯元器智能体配置
     */
    @Log(title = "腾讯元器智能体配置", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(yuanqiAgentConfigService.deleteYuanqiAgentConfigByIds(ids));
    }
}

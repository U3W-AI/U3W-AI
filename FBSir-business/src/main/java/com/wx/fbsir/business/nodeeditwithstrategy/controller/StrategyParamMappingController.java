package com.wx.fbsir.business.nodeeditwithstrategy.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import com.wx.fbsir.business.nodeeditwithstrategy.domain.StrategyParamMapping;
import com.wx.fbsir.business.nodeeditwithstrategy.service.IStrategyParamMappingService;

/**
 * 策略参数映射管理控制器
 *
 * <p>
 * 提供策略参数映射的增删改查接口，支持通过策略名称进行查询。
 * 典型用途：根据"成本优先 / 质量优先 / 最大回复Token"等策略，为前端
 * 或 Engine 侧统一下发 {@code modelName}、temperature、topP、maxTokens、prompt 等参数。
 * </p>
 */
@RestController
@RequestMapping("/system/strategy")
public class StrategyParamMappingController extends BaseController {

    @Autowired
    private IStrategyParamMappingService strategyService;

    /**
     * 查询策略参数映射列表。
     *
     * @param query 查询条件（目前主要支持按策略名称模糊查询）
     * @return 分页后的策略参数映射列表
     */
    @GetMapping("/list")
    public TableDataInfo list(StrategyParamMapping query) {
        startPage();
        List<StrategyParamMapping> list = strategyService.selectList(query);
        return getDataTable(list);
    }

    /**
     * 根据主键ID获取策略参数映射详情。
     *
     * @param id 主键ID
     * @return 单条策略参数映射记录
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(strategyService.selectById(id));
    }

    /**
     * 根据策略名称获取策略参数映射详情。
     *
     * @param strategyName 策略名称（唯一）
     * @return 对应的策略参数映射记录
     */
    @GetMapping("/name/{strategyName}")
    public AjaxResult getByName(@PathVariable String strategyName) {
        return success(strategyService.selectByStrategyName(strategyName));
    }

    /**
     * 新增策略参数映射。
     *
     * @param mapping 策略参数映射实体
     * @return 操作结果
     */
    @Log(title = "策略参数映射", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody StrategyParamMapping mapping) {
        mapping.setCreateBy(getUsername());
        return toAjax(strategyService.insert(mapping));
    }

    /**
     * 修改策略参数映射。
     *
     * @param mapping 策略参数映射实体
     * @return 操作结果
     */
    @Log(title = "策略参数映射", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody StrategyParamMapping mapping) {
        mapping.setUpdateBy(getUsername());
        return toAjax(strategyService.update(mapping));
    }

    /**
     * 批量删除策略参数映射。
     *
     * @param ids 需要删除的主键ID数组
     * @return 操作结果
     */
    @Log(title = "策略参数映射", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids) {
        return toAjax(strategyService.deleteByIds(ids));
    }
}


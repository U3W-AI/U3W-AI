package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.service.business.IFbsApiKeyBusinessService;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API Key 运营管理控制器（运营侧，需 JWT 认证）
 *
 * @author wxfbsir
 * @date 2026-04-11
 */
@RestController
@RequestMapping("/fbs/business/api-key")
public class FbsApiKeyBusinessController extends BaseController {

    @Autowired
    private IFbsApiKeyBusinessService apiKeyBusinessService;

    /**
     * 生成 API Key
     * ⚠️ 创建时返回完整 Key（仅此一次），后续查询只返回脱敏值
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:apikey:add')")
    @PostMapping("/generate")
    public AjaxResult generate(@RequestBody Map<String, Object> params) {
        String name = (String) params.get("name");
        String packCode = (String) params.get("packCode");
        Integer rateLimitPerMin = params.get("rateLimitPerMin") != null
                ? Integer.valueOf(params.get("rateLimitPerMin").toString()) : 60;
        String remark = (String) params.get("remark");

        if (name == null || name.trim().isEmpty()) {
            return AjaxResult.error("名称不能为空");
        }

        FbsApiKey result = apiKeyBusinessService.generateApiKey(name, packCode, rateLimitPerMin, remark);
        // 返回完整 Key（仅此一次）
        return AjaxResult.success(result);
    }

    /**
     * 查询 API Key 列表（脱敏）
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:apikey:list')")
    @GetMapping("/list")
    public AjaxResult list(FbsApiKey filter) {
        return AjaxResult.success(apiKeyBusinessService.listApiKeys(filter));
    }

    /**
     * 查询 API Key 详情（脱敏）
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:apikey:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return AjaxResult.success(apiKeyBusinessService.getApiKeyById(id));
    }

    /**
     * 禁用 API Key
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:apikey:edit')")
    @PutMapping("/disable/{id}")
    public AjaxResult disable(@PathVariable Long id) {
        apiKeyBusinessService.disableApiKey(id);
        return AjaxResult.success();
    }

    /**
     * 启用 API Key
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:apikey:edit')")
    @PutMapping("/enable/{id}")
    public AjaxResult enable(@PathVariable Long id) {
        apiKeyBusinessService.enableApiKey(id);
        return AjaxResult.success();
    }

    /**
     * 删除 API Key
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:apikey:remove')")
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        apiKeyBusinessService.deleteApiKey(id);
        return AjaxResult.success();
    }
}

package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.service.business.IFbsApiKeyBusinessService;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户侧 API Key 管理控制器（用户自助管理）
 *
 * @author FBSir
 * @date 2026-04-17
 */
@RestController
@RequestMapping("/fbs/business/my/apikey")
public class FbsMyApiKeyBusinessController extends BaseController {

    @Autowired
    private IFbsApiKeyBusinessService apiKeyBusinessService;

    /**
     * 查询我的 API Key 列表（脱敏）
     */
    @PreAuthorize("@ss.hasPermi('my:apikey:list')")
    @GetMapping("/list")
    public AjaxResult list() {
        List<FbsApiKey> keys = apiKeyBusinessService.listMyKeys();
        return AjaxResult.success(keys);
    }

    /**
     * 创建 API Key
     * 自动绑定当前用户
     * ⚠️ 创建时返回完整 Key（仅此一次），后续查询只返回脱敏值
     */
    @PreAuthorize("@ss.hasPermi('my:apikey:create')")
    @PostMapping("/create")
    public AjaxResult create(@RequestBody Map<String, Object> params) {
        String name = (String) params.get("name");

        if (name == null || name.trim().isEmpty()) {
            return AjaxResult.error("名称不能为空");
        }

        FbsApiKey result = apiKeyBusinessService.createByUser(name);
        // 返回完整 Key（仅此一次）
        return AjaxResult.success("创建成功", result);
    }

    /**
     * 禁用/启用 API Key
     * 只能操作自己的 Key
     */
    @PreAuthorize("@ss.hasPermi('my:apikey:toggle')")
    @PutMapping("/toggle/{id}")
    public AjaxResult toggle(@PathVariable Long id, @RequestParam Integer status) {
        apiKeyBusinessService.toggleStatus(id, status);
        return AjaxResult.success();
    }

    /**
     * 删除 API Key
     * 只能删除自己的 Key
     */
    @PreAuthorize("@ss.hasPermi('my:apikey:delete')")
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        apiKeyBusinessService.deleteById(id);
        return AjaxResult.success();
    }
}

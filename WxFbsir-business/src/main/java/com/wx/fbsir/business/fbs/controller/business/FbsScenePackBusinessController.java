package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackCreateRequest;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackDetailResponse;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackPageRequest;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.ScenePackUpdateRequest;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.service.business.IFbsScenePackBusinessService;
import com.wx.fbsir.business.fbs.service.WecomBusinessSyncService;
import com.wx.fbsir.business.point.domain.PointsRule;
import com.wx.fbsir.business.point.service.IPointsRuleService;
import com.wx.fbsir.common.annotation.Log;
import com.wx.fbsir.common.core.controller.BaseController;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.core.page.TableDataInfo;
import com.wx.fbsir.common.enums.BusinessType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景包运营Controller
 * 路径: /business/fbs/scene-pack/*
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@RestController
@RequestMapping("/business/fbs/scene-pack")
public class FbsScenePackBusinessController extends BaseController {

    private static final Logger log = LoggerFactory.getLogger(FbsScenePackBusinessController.class);

    @Autowired
    private IFbsScenePackBusinessService scenePackBusinessService;

    @Autowired(required = false)
    private WecomBusinessSyncService wecomBusinessSyncService;

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Autowired
    private IPointsRuleService pointsRuleService;

    /**
     * GET /business/fbs/scene-pack/list
     * 场景包分页列表
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:scenePack:list')")
    @GetMapping("/list")
    public TableDataInfo list(ScenePackPageRequest request) {
        startPage();
        List<FbsScenePack> list = scenePackBusinessService.getScenePackPage(request);
        return getDataTable(list);
    }

    /**
     * GET /business/fbs/scene-pack/{id}
     * 场景包详情
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:scenePack:query')")
    @GetMapping("/{id}")
    public AjaxResult getDetail(@PathVariable Long id) {
        ScenePackDetailResponse detail = scenePackBusinessService.getScenePackDetail(id);
        if (detail == null) {
            return AjaxResult.error("场景包不存在");
        }
        return AjaxResult.success(detail);
    }

    /**
     * POST /business/fbs/scene-pack
     * 创建场景包
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:scenePack:add')")
    @Log(title = "FBS场景包", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@RequestBody ScenePackCreateRequest request) {
        if (request.getPackCode() == null || request.getPackCode().isEmpty()) {
            return AjaxResult.error("packCode不能为空");
        }
        if (request.getPackName() == null || request.getPackName().isEmpty()) {
            return AjaxResult.error("packName不能为空");
        }
        Long id = scenePackBusinessService.createScenePack(request, getUsername());

        // 注意：创建时不同步到企微，只有发布时才同步

        return AjaxResult.success("创建成功", id);
    }

    /**
     * PUT /business/fbs/scene-pack
     * 编辑场景包
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:scenePack:edit')")
    @Log(title = "FBS场景包", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult update(@RequestBody ScenePackUpdateRequest request) {
        if (request.getId() == null) {
            return AjaxResult.error("id不能为空");
        }
        boolean ok = scenePackBusinessService.updateScenePack(request.getId(), request, getUsername());
        if (!ok) {
            return AjaxResult.error("编辑失败（场景包不存在或已下架不可编辑）");
        }

        // 注意：编辑时不同步到企微，只有发布时才同步

        return AjaxResult.success("编辑成功");
    }

    /**
     * PUT /business/fbs/scene-pack/publish
     * 发布场景包
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:scenePack:publish')")
    @Log(title = "FBS场景包-发布", businessType = BusinessType.UPDATE)
    @PutMapping("/publish")
    public AjaxResult publish(@RequestBody IdRequest request) {
        if (request.getId() == null) {
            return AjaxResult.error("id不能为空");
        }
        boolean ok = scenePackBusinessService.publishScenePack(request.getId(), getUsername());
        if (!ok) {
            return AjaxResult.error("发布失败（仅草稿状态可发布）");
        }

        // 同步到企微智能表格（entitlement）
        syncEntitlementToWecom(request.getId());

        return AjaxResult.success("发布成功");
    }

    /**
     * PUT /business/fbs/scene-pack/unpublish
     * 下架场景包
     */
    @PreAuthorize("@ss.hasPermi('business:fbs:scenePack:unpublish')")
    @Log(title = "FBS场景包-下架", businessType = BusinessType.UPDATE)
    @PutMapping("/unpublish")
    public AjaxResult unpublish(@RequestBody IdRequest request) {
        if (request.getId() == null) {
            return AjaxResult.error("id不能为空");
        }
        boolean ok = scenePackBusinessService.unpublishScenePack(request.getId(), getUsername());
        if (!ok) {
            return AjaxResult.error("下架失败（仅已发布状态可下架）");
        }
        return AjaxResult.success("下架成功");
    }

    /** 通用ID请求体 */
    public static class IdRequest {
        private Long id;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }

    /**
     * 同步场景包到企微智能表格（entitlement）
     *
     * creditsRequired 从 wx_points_rule 表查询实际积分值
     * - pointsRuleCode=null → creditsRequired=0（免费包）
     * - pointsRuleCode!=null → 从积分规则表查 pointsValue
     */
    private void syncEntitlementToWecom(Long scenePackId) {
        if (wecomBusinessSyncService == null) {
            log.debug("WecomBusinessSyncService 未注入，跳过同步");
            return;
        }
        try {
            FbsScenePack pack = scenePackMapper.selectById(scenePackId);
            if (pack == null) {
                log.warn("同步 entitlement 失败：场景包不存在 id={}", scenePackId);
                return;
            }

            // 从积分规则表查询实际 creditsRequired
            int creditsRequired = 0;
            if (pack.getPointsRuleCode() != null && !pack.getPointsRuleCode().isEmpty()) {
                PointsRule rule = pointsRuleService.getRuleByCode(pack.getPointsRuleCode());
                if (rule != null && rule.getPointsValue() != null) {
                    creditsRequired = rule.getPointsValue();
                } else {
                    log.warn("积分规则不存在或 pointsValue 为空 ruleCode={}, 默认 creditsRequired=0",
                            pack.getPointsRuleCode());
                }
            }

            wecomBusinessSyncService.syncEntitlement(pack, creditsRequired);
        } catch (Exception e) {
            // 同步失败不影响业务
            log.warn("同步场景包到企微失败 id={}", scenePackId, e);
        }
    }
}

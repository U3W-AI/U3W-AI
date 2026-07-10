package com.wx.fbsir.business.fbs.controller.internal;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * 场景包内部接口
 * 对应 design.md §5.1
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@RestController
@RequestMapping("/fbs/internal/scene-pack")
@PreAuthorize("@ss.hasRole('admin')")
public class FbsScenePackController {

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    /**
     * POST /fbs/internal/scene-pack/create
     * 创建场景包
     */
    @PostMapping("/create")
    public com.wx.fbsir.common.core.domain.AjaxResult create(@RequestBody CreateScenePackRequest req) {
        if (req.getPackCode() == null || req.getPackCode().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("packCode不能为空");
        }
        if (req.getPackName() == null || req.getPackName().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("packName不能为空");
        }

        FbsScenePack existing = scenePackMapper.selectByPackCode(req.getPackCode());
        if (existing != null) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("场景包编码已存在: " + req.getPackCode());
        }

        FbsScenePack pack = new FbsScenePack();
        pack.setPackCode(req.getPackCode());
        pack.setPackName(req.getPackName());
        pack.setPackType(req.getPackType() != null ? req.getPackType() : 1);
        pack.setOwnerType(req.getOwnerType() != null ? req.getOwnerType() : 1);
        pack.setOwnerId(req.getOwnerId());
        pack.setDescription(req.getDescription());
        pack.setPointsRuleCode(req.getPointsRuleCode());
        pack.setVisibleScope(req.getVisibleScope() != null ? req.getVisibleScope() : "ALL");
        pack.setStatus(0); // 草稿
        pack.setCurrentVersion("1.0.0");
        pack.setDelFlag("0");
        scenePackMapper.insertScenePack(pack);

        return com.wx.fbsir.common.core.domain.AjaxResult.success("创建成功",
                new CreateScenePackResponse(pack.getId(), pack.getPackCode()));
    }

    /**
     * GET /fbs/internal/scene-pack/{id}
     * 根据ID查询场景包
     */
    @GetMapping("/{id}")
    public com.wx.fbsir.common.core.domain.AjaxResult getById(@PathVariable Long id) {
        FbsScenePack pack = scenePackMapper.selectById(id);
        if (pack == null) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("场景包不存在");
        }
        return com.wx.fbsir.common.core.domain.AjaxResult.success(pack);
    }

    // ====== Request / Response DTOs ======

    public static class CreateScenePackRequest {
        private String packCode;
        private String packName;
        private Integer packType;     // 默认1
        private Integer ownerType;    // 默认1
        private Long ownerId;
        private String description;
        private String pointsRuleCode; // null=免费包
        private String visibleScope;  // 默认ALL

        public String getPackCode()        { return packCode; }
        public void setPackCode(String v)  { this.packCode = v; }
        public String getPackName()        { return packName; }
        public void setPackName(String v)  { this.packName = v; }
        public Integer getPackType()       { return packType; }
        public void setPackType(Integer v) { this.packType = v; }
        public Integer getOwnerType()      { return ownerType; }
        public void setOwnerType(Integer v){ this.ownerType = v; }
        public Long getOwnerId()          { return ownerId; }
        public void setOwnerId(Long v)    { this.ownerId = v; }
        public String getDescription()     { return description; }
        public void setDescription(String v){ this.description = v; }
        public String getPointsRuleCode()  { return pointsRuleCode; }
        public void setPointsRuleCode(String v){ this.pointsRuleCode = v; }
        public String getVisibleScope()    { return visibleScope; }
        public void setVisibleScope(String v){ this.visibleScope = v; }
    }

    public static class CreateScenePackResponse {
        private Long id;
        private String packCode;

        public CreateScenePackResponse(Long id, String packCode) {
            this.id = id;
            this.packCode = packCode;
        }

        public Long getId()       { return id; }
        public String getPackCode(){ return packCode; }
    }
}

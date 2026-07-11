package com.wx.fbsir.business.fbs.service.business.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.enums.PackStatus;
import java.util.Map;
import com.wx.fbsir.business.fbs.dto.business.scene_pack.*;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.service.business.IFbsScenePackBusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 场景包运营BusinessService实现（薄封装：直接调用Mapper，不包装内部领域Service）
 *
 * @author FBSir
 * @date 2026-04-08
 */
@Service
public class FbsScenePackBusinessServiceImpl implements IFbsScenePackBusinessService {

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Override
    public List<FbsScenePack> getScenePackPage(ScenePackPageRequest request) {
        FbsScenePack filter = new FbsScenePack();
        if (request.getStatus() != null) {
            filter.setStatus(request.getStatus());
        }
        if (request.getPackType() != null) {
            filter.setPackType(request.getPackType());
        }
        if (request.getOwnerType() != null) {
            filter.setOwnerType(request.getOwnerType());
        }
        return scenePackMapper.selectScenePackList(filter);
    }

    @Override
    public ScenePackDetailResponse getScenePackDetail(Long id) {
        FbsScenePack pack = scenePackMapper.selectById(id);
        if (pack == null) {
            return null;
        }
        ScenePackDetailResponse resp = new ScenePackDetailResponse();
        resp.setId(pack.getId());
        resp.setPackCode(pack.getPackCode());
        resp.setPackName(pack.getPackName());
        resp.setPackType(pack.getPackType());
        resp.setPackTypeDesc(resolvePackTypeDesc(pack.getPackType()));
        resp.setOwnerType(pack.getOwnerType());
        resp.setOwnerId(pack.getOwnerId());
        resp.setDescription(pack.getDescription());
        resp.setStatus(pack.getStatus());
        resp.setStatusDesc(PackStatus.ofCode(pack.getStatus()) != null ? PackStatus.ofCode(pack.getStatus()).getDesc() : null);
        resp.setVisibleScope(pack.getVisibleScope());
        resp.setPointsRuleCode(pack.getPointsRuleCode());
        resp.setContentSnapshot(pack.getContentSnapshot());
        resp.setCurrentVersion(pack.getCurrentVersion());
        resp.setCreatedBy(pack.getCreatedBy());
        resp.setCreateTime(pack.getCreateTime() != null ? pack.getCreateTime().toString() : null);
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createScenePack(ScenePackCreateRequest request, String createdBy) {
        FbsScenePack pack = new FbsScenePack();
        pack.setPackCode(request.getPackCode());
        pack.setPackName(request.getPackName());
        pack.setPackType(request.getPackType() != null ? request.getPackType() : 1);
        pack.setOwnerType(request.getOwnerType() != null ? request.getOwnerType() : 1);
        pack.setOwnerId(request.getOwnerId());
        pack.setDescription(request.getDescription());
        pack.setStatus(0); // 草稿
        pack.setVisibleScope(request.getVisibleScope() != null ? request.getVisibleScope() : "ALL");
        pack.setPointsRuleCode(request.getPointsRuleCode());
        pack.setContentSnapshot(request.getContentSnapshot());
        pack.setCurrentVersion("1.0.0");
        pack.setDelFlag("0");
        pack.setCreatedBy(createdBy);
        scenePackMapper.insertScenePack(pack);
        return pack.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateScenePack(Long id, ScenePackUpdateRequest request, String updatedBy) {
        FbsScenePack existing = scenePackMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        // Fail-Closed：已下架不可编辑
        if (existing.getStatus() != null && existing.getStatus() == 2) {
            return false;
        }
        FbsScenePack pack = new FbsScenePack();
        pack.setId(id);
        if (StringUtils.hasText(request.getPackName())) {
            pack.setPackName(request.getPackName());
        }
        if (request.getDescription() != null) {
            pack.setDescription(request.getDescription());
        }
        if (request.getVisibleScope() != null) {
            pack.setVisibleScope(request.getVisibleScope());
        }
        if (request.getPointsRuleCode() != null) {
            pack.setPointsRuleCode(request.getPointsRuleCode());
        }
        if (request.getContentSnapshot() != null) {
            pack.setContentSnapshot(request.getContentSnapshot());
        }
        pack.setUpdatedBy(updatedBy);
        return scenePackMapper.updateScenePack(pack) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishScenePack(Long id, String updatedBy) {
        FbsScenePack existing = scenePackMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        // Fail-Closed：仅草稿可发布
        if (existing.getStatus() == null || existing.getStatus() != 0) {
            return false;
        }
        FbsScenePack pack = new FbsScenePack();
        pack.setId(id);
        pack.setStatus(1); // 已发布
        pack.setUpdatedBy(updatedBy);
        return scenePackMapper.updateScenePack(pack) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean unpublishScenePack(Long id, String updatedBy) {
        FbsScenePack existing = scenePackMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        // Fail-Closed：仅已发布可下架
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            return false;
        }
        FbsScenePack pack = new FbsScenePack();
        pack.setId(id);
        pack.setStatus(2); // 已下架（终态）
        pack.setUpdatedBy(updatedBy);
        return scenePackMapper.updateScenePack(pack) > 0;
    }

    // ---- 私有辅助 ----

    /**
     * packType 枚举描述：1=平台包, 2=企业包, 3=自定义包
     * 注：当前无对应枚举类，直接映射（与 fbs_scene_pack 表字段注释保持一致）
     */
    private static final Map<Integer, String> PACK_TYPE_DESC_MAP = Map.of(
            1, "平台包",
            2, "企业包",
            3, "自定义包"
    );

    private String resolvePackTypeDesc(Integer packType) {
        if (packType == null) return null;
        return PACK_TYPE_DESC_MAP.getOrDefault(packType, packType.toString());
    }
}


package com.wx.fbsir.business.fbs.service.business.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.dto.self.*;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.AuthCodeService;
import com.wx.fbsir.business.fbs.service.business.IFbsUserSelfServiceBusinessService;
import com.wx.fbsir.common.exception.ServiceException;
import com.wx.fbsir.common.utils.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户自助服务BusinessService实现
 * <p>
 * 路径前缀：/my/*
 * </p>
 *
 * @author wxfbsir
 * @date 2026-04-10
 */
@Service
public class FbsUserSelfServiceBusinessServiceImpl implements IFbsUserSelfServiceBusinessService {

    @Autowired
    private FbsUserPackMapper userPackMapper;

    @Autowired
    private FbsAuthCodeMapper authCodeMapper;

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Autowired
    private AuthCodeService authCodeService;

    // ========== 5.2 getMyPacks ==========

    @Override
    public List<MyPackItemDTO> getMyPacks(MyPacksQueryDTO query) {
        // 5.2.1 获取 userId
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException("SESSION_REQUIRED", 401);
        }

        // 5.2.2 构造 filter
        FbsUserPack filter = new FbsUserPack();
        filter.setUserId(userId);
        if (query.getStatus() != null) {
            filter.setStatus(query.getStatus());
        }
        if (query.getPackId() != null) {
            filter.setPackId(query.getPackId());
        }
        if (query.getSourceType() != null) {
            filter.setSourceType(query.getSourceType());
        }

        // 5.2.3 分页查询（由 Controller 层 startPage() 触发 PageHelper 拦截）
        List<FbsUserPack> list = userPackMapper.selectMyPacks(filter);

        // 5.2.4 组装 DTO
        return list.stream().map(pack -> {
            MyPackItemDTO dto = new MyPackItemDTO();
            dto.setId(pack.getId());
            dto.setPackId(pack.getPackId());
            dto.setPackName(pack.getPackName());
            dto.setPackCode(pack.getPackCode());
            dto.setPackVersion(pack.getPackVersion());
            dto.setAuthCodeId(pack.getAuthCodeId());
            dto.setActivatedAt(pack.getActivatedAt());
            dto.setExpiresAt(pack.getExpiresAt());
            dto.setSourceType(pack.getSourceType());
            dto.setStatus(pack.getStatus());
            dto.setPackStatus(pack.getPackStatus());
            dto.setCreateTime(pack.getCreateTime());
            return dto;
        }).collect(Collectors.toList());
    }

    // ========== 5.3 activateAuthCode ==========

    @Override
    public AuthCodeActivateResponseDTO activateAuthCode(AuthCodeActivateRequestDTO req) {
        // 5.3.1 获取 userId
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException("SESSION_REQUIRED", 401);
        }

        String authCode = req.getAuthCode();

        // 5.3.2a authCode 为空
        if (authCode == null || authCode.trim().isEmpty()) {
            throw new ServiceException("授权码不能为空", 400);
        }

        // 5.3.2b 查授权码获取 targetId
        FbsAuthCode code = authCodeMapper.selectByAuthCode(authCode.trim());

        // 5.3.2c 授权码不存在
        if (code == null) {
            throw new ServiceException("授权码不存在", 400);
        }

        // 5.3.2d targetType != SCENE_PACK 或 targetId == null
        if (!"SCENE_PACK".equals(code.getTargetType()) || code.getTargetId() == null) {
            throw new ServiceException("授权码类型或目标无效", 400);
        }

        Long targetPackId = code.getTargetId();

        // 5.3.2e available/status 校验（在幂等检查之前，禁用/撤销码不能返回"成功"）
        if (code.getAvailable() == null || code.getAvailable() != 1) {
            throw new ServiceException("授权码已禁用", 400);
        }
        int codeStatus = code.getStatus() == null ? -1 : code.getStatus();
        if (codeStatus == 4) {
            throw new ServiceException("授权码已撤销", 400);
        }
        if (codeStatus == 3) {
            throw new ServiceException("授权码已过期", 400);
        }
        if (codeStatus == 2) {
            throw new ServiceException("授权码激活次数已用尽", 400);
        }

        // 5.3.2e2 已绑定用户校验：status=1 且 activatedCount>0 说明该码已被激活
        int activatedCount = code.getActivatedCount() == null ? 0 : code.getActivatedCount();
        if (codeStatus == 1 && activatedCount > 0) {
            throw new ServiceException("该授权码已被其他用户绑定", 400);
        }

        // 5.3.2e3 实时过期校验：deadline 可能已过但 status 尚未被更新为 3
        if (code.getDeadline() != null && new Date().after(code.getDeadline())) {
            throw new ServiceException("授权码已过期", 400);
        }

        // 5.3.2e4 场景包状态校验：已下架/草稿的包，其授权码不可激活
        FbsScenePack targetPack = scenePackMapper.selectById(targetPackId);
        if (targetPack == null) {
            throw new ServiceException("授权码关联的场景包不存在", 400);
        }
        if (targetPack.getStatus() == null || targetPack.getStatus() != 1) {
            throw new ServiceException("场景包已下架，授权码无法激活", 400);
        }

        // 5.3.2f 重复激活检查：用户是否已有该包权益（已有则拒绝，避免授权码不被消耗）
        int exists = userPackMapper.selectExistsByUserIdAndPackId(userId, targetPackId);
        if (exists > 0) {
            throw new ServiceException("您已拥有该场景包权益，无法使用其他授权码重复激活", 409);
        }

        // 5.3.3 调用 authCodeService.activateAuthCode
        AuthCodeService.ActivateResult result = authCodeService.activateAuthCode(authCode, userId);

        if (!result.isSuccess()) {
            // 5.3.6 map failReason → ServiceException
            throw mapFailReasonToException(result.getFailReason());
        }

        // 5.3.4 成功路径：从 ActivateResult 直接取 expiresAt
        Long packId = result.getPackId();
        Date expiresAt = result.getExpiresAt();

        // 5.3.5 补取 packName
        FbsScenePack pack = scenePackMapper.selectById(packId);
        String packName = (pack != null) ? pack.getPackName() : null;

        // 5.3.7 审计字段已由 authCodeService.activateAuthCode 内部处理（插入时写）

        AuthCodeActivateResponseDTO resp = new AuthCodeActivateResponseDTO();
        resp.setPackId(packId);
        resp.setPackName(packName);
        resp.setExpiresAt(expiresAt != null
                ? expiresAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                : null);
        resp.setMsg("激活成功");
        return resp;
    }

    // ========== 5.4 getClaimableScenePacks ==========

    @Override
    public List<MyScenePackItemDTO> getClaimableScenePacks(MyScenePacksQueryDTO query) {
        // 5.4.1 获取 userId
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException("SESSION_REQUIRED", 401);
        }

        // 5.4.2 查询可领取场景包（由 Controller 层 startPage() 触发 PageHelper 拦截）
        List<FbsScenePack> list = scenePackMapper.selectClaimablePacks(userId, query.getKeyword());

        // 5.4.2b 批量查询用户已领取的包ID集合，用于标记 claimed
        Set<Long> allPackIds = list.stream()
                .map(FbsScenePack::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> claimedPackIds = allPackIds.isEmpty()
                ? Collections.emptySet()
                : userPackMapper.selectClaimedPackIds(userId, allPackIds);

        // 5.4.3 组装 DTO
        return list.stream().map(pack -> {
            MyScenePackItemDTO dto = new MyScenePackItemDTO();
            dto.setId(pack.getId());
            dto.setPackCode(pack.getPackCode());
            dto.setPackName(pack.getPackName());
            dto.setCurrentVersion(pack.getCurrentVersion());
            dto.setDescription(pack.getDescription());
            dto.setPointsRuleCode(pack.getPointsRuleCode());
            dto.setCreateTime(pack.getCreateTime());
            dto.setClaimed(claimedPackIds.contains(pack.getId()));
            return dto;
        }).collect(Collectors.toList());
    }

    // ========== 5.5 claimScenePack ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScenePackClaimResponseDTO claimScenePack(ScenePackClaimRequestDTO req) {
        // 5.5.1 获取 userId
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new ServiceException("SESSION_REQUIRED", 401);
        }

        Long packId = req.getPackId();

        // 5.5.2 Fail-Closed 校验链
        FbsScenePack pack = scenePackMapper.selectById(packId);
        if (pack == null) {
            throw new ServiceException("PACK_NOT_FOUND", 404);
        }
        if (pack.getStatus() == null || pack.getStatus() != 1) {
            throw new ServiceException("PACK_OFFLINE", 400);
        }
        if (pack.getOwnerType() == null || pack.getOwnerType() != 1) {
            throw new ServiceException("PACK_PRIVATE", 400);
        }
        if (pack.getPointsRuleCode() != null) {
            throw new ServiceException("PACK_NOT_FREE", 400);
        }

        // 5.5.3 幂等检查
        int exists = userPackMapper.selectExistsByUserIdAndPackId(userId, packId);
        if (exists > 0) {
            ScenePackClaimResponseDTO resp = new ScenePackClaimResponseDTO();
            resp.setPackId(packId);
            resp.setPackName(pack.getPackName());
            resp.setExpiresAt(null); // MVP 不设过期时间
            resp.setMsg("已领取该场景包");
            return resp; // HTTP 200
        }

        // 5.5.4 事务写入
        try {
            FbsUserPack userPack = new FbsUserPack();
            userPack.setUserId(userId);
            userPack.setPackId(packId);
            userPack.setPackVersion(pack.getCurrentVersion());
            userPack.setStatus(1);
            userPack.setSourceType(1); // 平台分发
            userPack.setActivatedAt(new Date());
            // 审计字段
            userPack.setOperator(userId);
            userPack.setRequestId(UUID.randomUUID().toString());
            userPack.setOperateTime(new Date());

            userPackMapper.insertUserPack(userPack);
        } catch (DuplicateKeyException e) {
            // 5.5.6 并发幂等兜底
            ScenePackClaimResponseDTO resp = new ScenePackClaimResponseDTO();
            resp.setPackId(packId);
            resp.setPackName(pack.getPackName());
            resp.setExpiresAt(null);
            resp.setMsg("已领取该场景包");
            return resp;
        }

        // 5.5.5 组装成功响应
        ScenePackClaimResponseDTO resp = new ScenePackClaimResponseDTO();
        resp.setPackId(packId);
        resp.setPackName(pack.getPackName());
        resp.setExpiresAt(null); // MVP 不设过期时间
        resp.setMsg("领取成功");
        return resp;
    }

    // ========== 辅助方法 ==========

    /**
     * 将 failReason 映射为 ServiceException
     */
    private ServiceException mapFailReasonToException(String failReason) {
        if (failReason == null) {
            return new ServiceException("激活失败(未知原因)", 400);
        }
        if (failReason.contains("不存在") || failReason.contains("不可激活")) {
            return new ServiceException("授权码无效", 400);
        }
        if (failReason.contains("已禁用")) {
            return new ServiceException("授权码已禁用", 400);
        }
        if (failReason.contains("已撤销")) {
            return new ServiceException("授权码已撤销", 400);
        }
        if (failReason.contains("已过期")) {
            return new ServiceException("授权码已过期", 400);
        }
        if (failReason.contains("激活次数上限") || failReason.contains("用尽")) {
            return new ServiceException("授权码激活次数已用尽", 400);
        }
        // 兜底
        return new ServiceException("授权码激活失败：" + failReason, 400);
    }
}

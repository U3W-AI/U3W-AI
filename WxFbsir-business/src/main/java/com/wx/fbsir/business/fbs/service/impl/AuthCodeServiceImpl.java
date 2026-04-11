package com.wx.fbsir.business.fbs.service.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.AuthCodeService;
import com.wx.fbsir.business.fbs.service.RightsCheckService;
import com.wx.fbsir.business.fbs.dto.RightsCheckResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 授权码激活服务实现
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@Service
public class AuthCodeServiceImpl implements AuthCodeService {

    @Autowired
    private RightsCheckService rightsCheckService;

    @Autowired
    private FbsAuthCodeMapper authCodeMapper;

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Autowired
    private FbsUserPackMapper userPackMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ActivateResult activateAuthCode(String authCode, Long userId) {
        // 0. 参数校验
        if (!StringUtils.hasText(authCode) || userId == null) {
            return ActivateResult.fail("参数不能为空");
        }

        // 1. 预校验（只读，快速失败）
        RightsCheckResult preCheck = rightsCheckService.checkAuthCode(authCode);
        if (!preCheck.isAllowed()) {
            return ActivateResult.fail(preCheck.getReason());
        }

        // 2. FOR UPDATE 行锁查询，防并发重复激活
        FbsAuthCode code = authCodeMapper.selectByAuthCodeForUpdate(authCode);
        if (code == null) {
            return ActivateResult.fail("授权码不存在");
        }

        // 3. 双重校验（持锁后再检查，防止并发窗口）
        if (code.getAvailable() == null || code.getAvailable() != 1) {
            return ActivateResult.fail("授权码已禁用");
        }
        int status = code.getStatus() == null ? -1 : code.getStatus();
        if (status != 0 && status != 1) {
            return ActivateResult.fail("授权码不可激活（当前状态: " + status + "）");
        }
        if (code.getDeadline() != null && new Date().after(code.getDeadline())) {
            return ActivateResult.fail("授权码已过期");
        }
        int activatedCount  = code.getActivatedCount() == null ? 0 : code.getActivatedCount();
        int maxActivations  = code.getMaxActivations() == null ? 1 : code.getMaxActivations();
        if (activatedCount >= maxActivations) {
            return ActivateResult.fail("授权码已达激活次数上限");
        }

        // 4. 计算新状态
        int newActivatedCount = activatedCount + 1;
        int newStatus;
        if (newActivatedCount >= maxActivations) {
            newStatus = 2; // 已用尽
        } else if (status == 0) {
            newStatus = 1; // 首次激活：未激活 → 已激活
        } else {
            newStatus = status; // 已激活且未用尽，保持 status=1
        }

        // 5. 先查并校验场景包，再更新授权码激活次数
        //    避免场景包不存在时激活次数已被消耗（不可回滚）
        FbsScenePack pack = null;
        if ("SCENE_PACK".equals(code.getTargetType()) && code.getTargetId() != null) {
            pack = scenePackMapper.selectById(code.getTargetId());
        }
        if (pack == null) {
            return ActivateResult.fail("授权码关联的场景包不存在或类型不支持");
        }

        // 6. 更新授权码激活次数（在确认场景包存在之后）
        authCodeMapper.updateActivated(code.getId(), newStatus, newActivatedCount);

        // 7. 创建 fbs_user_pack 权益记录（source_type=3，用户激活）
        FbsUserPack userPack = new FbsUserPack();
        userPack.setUserId(userId);
        userPack.setPackId(pack.getId());
        userPack.setPackVersion(pack.getCurrentVersion());
        userPack.setAuthCodeId(code.getId());
        userPack.setActivatedAt(new Date());
        userPack.setStatus(1); // 有效
        userPack.setSourceType(3); // 用户激活
        // 审计字段（与 claimScenePack 路径保持一致）
        userPack.setOperator(userId);
        userPack.setRequestId(java.util.UUID.randomUUID().toString());
        userPack.setOperateTime(new Date());
        userPackMapper.insertUserPack(userPack);

        return ActivateResult.success(userPack.getId(), pack.getId(), pack.getPackCode(), userPack.getExpiresAt());
    }
}

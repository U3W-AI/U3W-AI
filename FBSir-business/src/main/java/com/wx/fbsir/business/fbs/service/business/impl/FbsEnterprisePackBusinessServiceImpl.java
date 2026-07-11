package com.wx.fbsir.business.fbs.service.business.impl;

import com.wx.fbsir.business.fbs.domain.entity.*;
import com.wx.fbsir.business.fbs.dto.business.enterprise.*;
import com.wx.fbsir.business.fbs.mapper.*;
import com.wx.fbsir.business.fbs.service.business.IFbsEnterpriseBusinessService;
import com.wx.fbsir.business.fbs.service.business.IFbsEnterprisePackBusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 企业场景包分发BusinessService实现
 *
 * @author FBSir
 * @date 2026-04-09
 */
@Service
public class FbsEnterprisePackBusinessServiceImpl implements IFbsEnterprisePackBusinessService {

    @Autowired
    private FbsEnterprisePackMapper enterprisePackMapper;

    @Autowired
    private FbsEnterpriseMapper enterpriseMapper;

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Autowired
    private FbsEnterpriseMemberMapper memberMapper;

    @Autowired
    private FbsMemberPackMapper memberPackMapper;

    /** 简单复用，避免循环依赖，只注入自身 */
    @Autowired(required = false)
    private IFbsEnterpriseBusinessService enterpriseBusinessService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long grantPackToEnterprise(EnterprisePackGrantRequest request, String createdBy) {
        // 1. 检查企业存在且正常
        FbsEnterprise enterprise = enterpriseMapper.selectById(request.getEnterpriseId());
        if (enterprise == null) {
            throw new IllegalArgumentException("企业不存在");
        }
        if (enterprise.getStatus() != 1) {
            throw new IllegalArgumentException("企业状态异常，无法分发");
        }

        // 2. 检查场景包存在且已发布
        FbsScenePack pack = scenePackMapper.selectByPackCode(request.getPackCode());
        if (pack == null) {
            throw new IllegalArgumentException("场景包不存在");
        }
        if (pack.getStatus() == null || pack.getStatus() != 1) {
            throw new IllegalArgumentException("场景包未发布，无法分发");
        }

        // 3. 检查是否已有有效分发记录（幂等）
        FbsEnterprisePack existing = enterprisePackMapper.selectByEnterpriseAndPack(
            request.getEnterpriseId(), pack.getId());
        if (existing != null && existing.getStatus() != null && existing.getStatus() == 1) {
            // 已授权，直接返回（幂等）
            return existing.getId();
        }

        // 4. 解析过期时间
        Date expiryTime = parseExpiryTime(request.getExpiryTime());

        if (existing != null && existing.getStatus() != null && existing.getStatus() == 3) {
            // 已撤销 → 重新授权：更新记录，usedQuota 归零
            existing.setPackId(pack.getId());
            existing.setPackQuota(request.getPackQuota());
            existing.setUsedQuota(0);
            existing.setGrantTime(new Date());
            existing.setExpiryTime(expiryTime);
            existing.setStatus(1); // 重新授权
            existing.setUpdatedBy(createdBy);
            enterprisePackMapper.updateEnterprisePack(existing);

            // 重新生成成员授权记录（先撤销旧记录，再批量新增）
            grantMemberPacks(existing, pack, createdBy);

            return existing.getId();
        }

        // 5. 新增分发记录
        FbsEnterprisePack enterprisePack = new FbsEnterprisePack();
        enterprisePack.setEnterpriseId(request.getEnterpriseId());
        enterprisePack.setPackId(pack.getId());
        enterprisePack.setPackQuota(request.getPackQuota());
        enterprisePack.setUsedQuota(0);
        enterprisePack.setGrantTime(new Date());
        enterprisePack.setExpiryTime(expiryTime);
        enterprisePack.setStatus(1); // 已授权
        enterprisePack.setCreatedBy(createdBy);
        enterprisePackMapper.insertEnterprisePack(enterprisePack);

        // 6. 为企业所有正常成员批量创建授权记录
        grantMemberPacks(enterprisePack, pack, createdBy);

        return enterprisePack.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokePackFromEnterprise(Long enterprisePackId, String updatedBy) {
        FbsEnterprisePack existing = enterprisePackMapper.selectById(enterprisePackId);
        if (existing == null) {
            return false;
        }
        // Fail-Closed：仅 status=1（已授权）可撤销
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            return false;
        }

        // 1. 撤销企业包
        existing.setStatus(3); // 已撤销
        existing.setUpdatedBy(updatedBy);
        enterprisePackMapper.updateEnterprisePack(existing);

        // 2. 级联撤销所有成员授权记录（不删除，只改状态）
        memberPackMapper.updateStatusByEnterprisePackId(enterprisePackId, 4, updatedBy);

        return true;
    }

    @Override
    public List<FbsEnterprisePack> getByEnterprise(Long enterpriseId) {
        return enterprisePackMapper.selectByEnterpriseId(enterpriseId);
    }

    @Override
    public List<FbsEnterprisePack> getEnterprisePackPage(EnterprisePackPageRequest request) {
        if (request.getEnterpriseId() == null) {
            return List.of();
        }
        FbsEnterprisePack filter = new FbsEnterprisePack();
        filter.setEnterpriseId(request.getEnterpriseId());
        if (request.getStatus() != null) {
            filter.setStatus(request.getStatus());
        }
        return enterprisePackMapper.selectEnterprisePackList(filter);
    }

    @Override
    public EnterprisePackDetailResponse getDetail(Long id) {
        FbsEnterprisePack epack = enterprisePackMapper.selectById(id);
        if (epack == null) {
            return null;
        }

        FbsEnterprise enterprise = enterpriseMapper.selectById(epack.getEnterpriseId());
        FbsScenePack pack = scenePackMapper.selectById(epack.getPackId());

        int remainQuota = 0;
        if (epack.getPackQuota() != null && epack.getUsedQuota() != null) {
            remainQuota = epack.getPackQuota() - epack.getUsedQuota();
            if (remainQuota < 0) remainQuota = 0;
        }

        EnterprisePackDetailResponse resp = new EnterprisePackDetailResponse();
        resp.setId(epack.getId());
        resp.setEnterpriseId(epack.getEnterpriseId());
        resp.setEnterpriseName(enterprise != null ? enterprise.getEnterpriseName() : null);
        resp.setPackId(epack.getPackId());
        resp.setPackCode(pack != null ? pack.getPackCode() : null);
        resp.setPackName(pack != null ? pack.getPackName() : null);
        resp.setPackQuota(epack.getPackQuota());
        resp.setUsedQuota(epack.getUsedQuota());
        resp.setRemainQuota(remainQuota); // 实时计算
        resp.setGrantTime(epack.getGrantTime() != null ? epack.getGrantTime().toString() : null);
        resp.setExpiryTime(epack.getExpiryTime() != null ? epack.getExpiryTime().toString() : null);
        resp.setStatus(epack.getStatus());
        resp.setStatusDesc(resolveStatusDesc(epack.getStatus()));
        return resp;
    }

    // ---- 私有辅助 ----

    /**
     * 为企业所有正常成员批量创建授权记录
     */
    private void grantMemberPacks(FbsEnterprisePack enterprisePack, FbsScenePack pack, String createdBy) {
        // 先将已有成员授权记录全部标记为已撤销（简化处理，不删记录）
        memberPackMapper.updateStatusByEnterprisePackId(enterprisePack.getId(), 4, createdBy);

        List<FbsEnterpriseMember> members = memberMapper.selectActiveByEnterpriseId(enterprisePack.getEnterpriseId());
        if (members == null || members.isEmpty()) {
            return;
        }

        List<FbsMemberPack> memberPacks = members.stream().map(member -> {
            FbsMemberPack mp = new FbsMemberPack();
            mp.setMemberId(member.getId());
            mp.setEnterprisePackId(enterprisePack.getId());
            mp.setPackId(pack.getId());
            mp.setGrantTime(new Date());
            mp.setExpiryTime(enterprisePack.getExpiryTime());
            mp.setStatus(1); // 正常
            mp.setCreatedBy(createdBy);
            return mp;
        }).collect(Collectors.toList());

        memberPackMapper.insertMemberPackBatch(memberPacks);
    }

    private Date parseExpiryTime(String expiryTime) {
        if (!StringUtils.hasText(expiryTime)) {
            return null;
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            return sdf.parse(expiryTime);
        } catch (ParseException e) {
            return null;
        }
    }

    private static String resolveStatusDesc(Integer status) {
        if (status == null) return null;
        return switch (status) {
            case 1 -> "已授权";
            case 2 -> "已用尽";
            case 3 -> "已撤销";
            default -> status.toString();
        };
    }
}

package com.wx.fbsir.business.fbs.service.business.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprisePack;
import com.wx.fbsir.business.fbs.dto.business.enterprise.*;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterprisePackMapper;
import com.wx.fbsir.business.fbs.service.business.IFbsEnterpriseBusinessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * 企业组织管理BusinessService实现
 *
 * @author FBSir
 * @date 2026-04-09
 */
@Service
public class FbsEnterpriseBusinessServiceImpl implements IFbsEnterpriseBusinessService {

    @Autowired
    private FbsEnterpriseMapper enterpriseMapper;

    @Autowired
    private FbsEnterpriseMemberMapper memberMapper;

    @Autowired
    private FbsEnterprisePackMapper packMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createEnterprise(EnterpriseCreateRequest request, String createdBy) {
        // 重名检查
        if (enterpriseMapper.selectByName(request.getEnterpriseName()) != null) {
            throw new IllegalArgumentException("企业名称已存在");
        }

        FbsEnterprise enterprise = new FbsEnterprise();
        enterprise.setEnterpriseCode(generateEnterpriseCode());
        enterprise.setEnterpriseName(request.getEnterpriseName());
        enterprise.setContactName(request.getContactName());
        enterprise.setContactPhone(request.getContactPhone());
        enterprise.setContactEmail(request.getContactEmail());
        enterprise.setRemark(request.getRemark());
        enterprise.setStatus(1); // 正常
        enterprise.setDelFlag("0");
        enterprise.setCreatedBy(createdBy);

        enterpriseMapper.insertEnterprise(enterprise);
        return enterprise.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateEnterprise(Long id, EnterpriseUpdateRequest request, String updatedBy) {
        FbsEnterprise existing = enterpriseMapper.selectById(id);
        if (existing == null) {
            return false;
        }

        // 重名检查（排除自身）
        if (StringUtils.hasText(request.getEnterpriseName())
                && !request.getEnterpriseName().equals(existing.getEnterpriseName())) {
            FbsEnterprise conflict = enterpriseMapper.selectByName(request.getEnterpriseName());
            if (conflict != null && !conflict.getId().equals(id)) {
                throw new IllegalArgumentException("企业名称已存在");
            }
            existing.setEnterpriseName(request.getEnterpriseName());
        }
        if (request.getContactName() != null) {
            existing.setContactName(request.getContactName());
        }
        if (request.getContactPhone() != null) {
            existing.setContactPhone(request.getContactPhone());
        }
        if (request.getContactEmail() != null) {
            existing.setContactEmail(request.getContactEmail());
        }
        if (request.getRemark() != null) {
            existing.setRemark(request.getRemark());
        }
        existing.setUpdatedBy(updatedBy);

        return enterpriseMapper.updateEnterprise(existing) > 0;
    }

    @Override
    public EnterpriseDetailResponse getEnterpriseDetail(Long id) {
        FbsEnterprise enterprise = enterpriseMapper.selectById(id);
        if (enterprise == null) {
            return null;
        }

        // 查询成员数量（仅正常状态）
        List<FbsEnterpriseMember> members = memberMapper.selectActiveByEnterpriseId(id);

        // 查询包数量（仅正常状态）
        List<FbsEnterprisePack> packs = packMapper.selectByEnterpriseId(id);
        long activePackCount = packs.stream().filter(p -> p.getStatus() != null && p.getStatus() == 1).count();

        EnterpriseDetailResponse resp = new EnterpriseDetailResponse();
        resp.setId(enterprise.getId());
        resp.setEnterpriseCode(enterprise.getEnterpriseCode());
        resp.setEnterpriseName(enterprise.getEnterpriseName());
        resp.setContactName(enterprise.getContactName());
        resp.setContactPhone(enterprise.getContactPhone());
        resp.setContactEmail(enterprise.getContactEmail());
        resp.setRemark(enterprise.getRemark());
        resp.setStatus(enterprise.getStatus());
        resp.setStatusDesc(resolveStatusDesc(enterprise.getStatus()));
        resp.setMemberCount(members.size());
        resp.setPackCount((int) activePackCount);
        resp.setCreatedBy(enterprise.getCreatedBy());
        resp.setCreateTime(enterprise.getCreateTime() != null ? enterprise.getCreateTime().toString() : null);
        return resp;
    }

    @Override
    public List<FbsEnterprise> getEnterprisePage(EnterprisePageRequest request) {
        FbsEnterprise filter = new FbsEnterprise();
        if (request.getEnterpriseName() != null && !request.getEnterpriseName().isEmpty()) {
            filter.setEnterpriseName(request.getEnterpriseName());
        }
        if (request.getStatus() != null) {
            filter.setStatus(request.getStatus());
        }
        return enterpriseMapper.selectEnterpriseList(filter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableEnterprise(Long id, String updatedBy) {
        FbsEnterprise existing = enterpriseMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        // Fail-Closed：仅正常状态（1）可禁用
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            return false;
        }

        FbsEnterprise enterprise = new FbsEnterprise();
        enterprise.setId(id);
        enterprise.setStatus(2); // 已禁用
        enterprise.setUpdatedBy(updatedBy);

        return enterpriseMapper.updateEnterprise(enterprise) > 0;
    }

    // ---- 私有辅助 ----

    private String generateEnterpriseCode() {
        return "ENT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private static String resolveStatusDesc(Integer status) {
        if (status == null) return null;
        return switch (status) {
            case 1 -> "正常";
            case 2 -> "已禁用";
            default -> status.toString();
        };
    }
}

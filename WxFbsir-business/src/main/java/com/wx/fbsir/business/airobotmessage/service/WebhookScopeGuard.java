package com.wx.fbsir.business.airobotmessage.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import org.springframework.stereotype.Component;

import com.wx.fbsir.business.airobotmessage.dto.WebhookEnterpriseOption;
import java.util.List;

/** Prevents request-supplied enterprise IDs from becoming trusted tenant context. */
@Component
public class WebhookScopeGuard {
    private final FbsEnterpriseMemberMapper memberMapper;
    private final FbsEnterpriseMapper enterpriseMapper;

    public WebhookScopeGuard(FbsEnterpriseMemberMapper memberMapper, FbsEnterpriseMapper enterpriseMapper) {
        this.memberMapper = memberMapper;
        this.enterpriseMapper = enterpriseMapper;
    }

    public void requireActiveMember(Long enterpriseId, Long userId) {
        if (enterpriseId == null || userId == null) {
            throw new SecurityException("企业范围无效");
        }
        FbsEnterpriseMember member = memberMapper.selectByEnterpriseAndUser(enterpriseId, userId);
        if (member == null || !Integer.valueOf(1).equals(member.getStatus())
                || !"ADMIN".equals(member.getRole())) {
            throw new SecurityException("当前用户不是目标企业的有效管理员");
        }
        FbsEnterprise enterprise = enterpriseMapper.selectById(enterpriseId);
        if (enterprise == null || !Integer.valueOf(1).equals(enterprise.getStatus())) {
            throw new SecurityException("目标企业不可用");
        }
    }

    public List<WebhookEnterpriseOption> listAdminEnterprises(Long userId) {
        if (userId == null) throw new SecurityException("用户身份无效");
        return memberMapper.selectActiveByUserId(userId).stream()
                .filter(member -> Integer.valueOf(1).equals(member.getStatus()))
                .filter(member -> "ADMIN".equals(member.getRole()))
                .map(member -> enterpriseMapper.selectById(member.getEnterpriseId()))
                .filter(enterprise -> enterprise != null && Integer.valueOf(1).equals(enterprise.getStatus()))
                .map(enterprise -> new WebhookEnterpriseOption(enterprise.getId(), enterprise.getEnterpriseName()))
                .toList();
    }
}

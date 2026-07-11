package com.wx.fbsir.business.airobotmessage.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsEnterprise;
import com.wx.fbsir.business.fbs.domain.entity.FbsEnterpriseMember;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMapper;
import com.wx.fbsir.business.fbs.mapper.FbsEnterpriseMemberMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookScopeGuardTest {
    private final FbsEnterpriseMemberMapper memberMapper = mock(FbsEnterpriseMemberMapper.class);
    private final FbsEnterpriseMapper enterpriseMapper = mock(FbsEnterpriseMapper.class);
    private final WebhookScopeGuard guard = new WebhookScopeGuard(memberMapper, enterpriseMapper);

    @Test
    void rejectsOrdinaryMemberEvenWhenActive() {
        FbsEnterpriseMember member = member("MEMBER", 1);
        when(memberMapper.selectByEnterpriseAndUser(10L, 7L)).thenReturn(member);
        assertThrows(SecurityException.class, () -> guard.requireActiveMember(10L, 7L));
    }

    @Test
    void rejectsDisabledEnterprise() {
        when(memberMapper.selectByEnterpriseAndUser(10L, 7L)).thenReturn(member("ADMIN", 1));
        FbsEnterprise enterprise = new FbsEnterprise();
        enterprise.setStatus(2);
        when(enterpriseMapper.selectById(10L)).thenReturn(enterprise);
        assertThrows(SecurityException.class, () -> guard.requireActiveMember(10L, 7L));
    }

    @Test
    void acceptsActiveEnterpriseAdmin() {
        when(memberMapper.selectByEnterpriseAndUser(10L, 7L)).thenReturn(member("ADMIN", 1));
        FbsEnterprise enterprise = new FbsEnterprise();
        enterprise.setStatus(1);
        when(enterpriseMapper.selectById(10L)).thenReturn(enterprise);
        assertDoesNotThrow(() -> guard.requireActiveMember(10L, 7L));
    }

    private FbsEnterpriseMember member(String role, int status) {
        FbsEnterpriseMember member = new FbsEnterpriseMember();
        member.setEnterpriseId(10L);
        member.setUserId(7L);
        member.setRole(role);
        member.setStatus(status);
        return member;
    }
}

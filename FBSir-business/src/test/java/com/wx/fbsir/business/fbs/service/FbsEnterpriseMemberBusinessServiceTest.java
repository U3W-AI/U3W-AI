package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.*;
import com.wx.fbsir.business.fbs.dto.business.enterprise.*;
import com.wx.fbsir.business.fbs.mapper.*;
import com.wx.fbsir.business.fbs.service.business.impl.FbsEnterpriseMemberBusinessServiceImpl;
import com.wx.fbsir.common.core.domain.entity.SysUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FbsEnterpriseMemberBusinessService 单元测试
 *
 * @author FBSir
 * @date 2026-04-09
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("企业成员管理服务测试")
class FbsEnterpriseMemberBusinessServiceTest {

    @Mock private FbsEnterpriseMemberMapper memberMapper;
    @Mock private FbsEnterpriseMapper enterpriseMapper;
    @Mock private FbsEnterprisePackMapper enterprisePackMapper;
    @Mock private FbsMemberPackMapper memberPackMapper;
    @Mock private com.wx.fbsir.system.mapper.SysUserMapper sysUserMapper;

    @InjectMocks
    private FbsEnterpriseMemberBusinessServiceImpl service;

    // ---- 常量 ----
    private static final Long ENT_ID = 1L;
    private static final Long USER_ID = 10L;
    private static final Long MEMBER_ID = 3001L;
    private static final Long EPACK_ID = 5001L;
    private static final Long PACK_ID = 2001L;
    private static final String CREATED_BY = "admin";

    // ---- Fixture ----
    private FbsEnterprise buildEnterprise() {
        FbsEnterprise e = new FbsEnterprise();
        e.setId(ENT_ID);
        e.setEnterpriseName("悟空科技");
        e.setStatus(1);
        return e;
    }

    private FbsEnterpriseMember buildMember(int status) {
        FbsEnterpriseMember m = new FbsEnterpriseMember();
        m.setId(MEMBER_ID);
        m.setEnterpriseId(ENT_ID);
        m.setUserId(USER_ID);
        m.setRole("MEMBER");
        m.setStatus(status);
        return m;
    }

    private FbsEnterprisePack buildEnterprisePack() {
        FbsEnterprisePack ep = new FbsEnterprisePack();
        ep.setId(EPACK_ID);
        ep.setEnterpriseId(ENT_ID);
        ep.setPackId(PACK_ID);
        ep.setStatus(1);
        return ep;
    }

    private SysUser buildSysUser() {
        SysUser u = new SysUser();
        u.setUserId(USER_ID);
        u.setUserName("zhangsan");
        return u;
    }

    // ========================================================================
    // §5.1 添加成员测试
    // ========================================================================

    @Nested
    @DisplayName("§5.1 添加成员")
    class AddMemberTests {

        @Test
        @DisplayName("§5.1.1 addMember — 新增成员成功，自动继承企业包")
        void addMemberSuccess() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(sysUserMapper.selectUserById(USER_ID)).thenReturn(buildSysUser());
            when(memberMapper.selectByEnterpriseAndUser(ENT_ID, USER_ID)).thenReturn(null);
            when(enterprisePackMapper.selectByEnterpriseId(ENT_ID)).thenReturn(
                Arrays.asList(buildEnterprisePack()));
            doAnswer(invocation -> {
                FbsEnterpriseMember m = invocation.getArgument(0);
                m.setId(MEMBER_ID);
                return 1;
            }).when(memberMapper).insertMember(any(FbsEnterpriseMember.class));

            var request = new EnterpriseMemberAddRequest();
            request.setEnterpriseId(ENT_ID);
            request.setUserId(USER_ID);

            Long id = service.addMember(request, CREATED_BY);

            assertNotNull(id);
            ArgumentCaptor<FbsEnterpriseMember> captor = ArgumentCaptor.forClass(FbsEnterpriseMember.class);
            verify(memberMapper).insertMember(captor.capture());
            assertEquals(ENT_ID, captor.getValue().getEnterpriseId());
            assertEquals(USER_ID, captor.getValue().getUserId());
            assertEquals(1, captor.getValue().getStatus());

            // 验证自动继承企业包（memberPackBatch 被调用）
            verify(memberPackMapper).insertMemberPackBatch(anyList());
        }

        @Test
        @DisplayName("§5.1.2 addMember — 用户已是企业成员，抛异常")
        void addMemberDuplicate() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(sysUserMapper.selectUserById(USER_ID)).thenReturn(buildSysUser());
            when(memberMapper.selectByEnterpriseAndUser(ENT_ID, USER_ID)).thenReturn(buildMember(1));

            var request = new EnterpriseMemberAddRequest();
            request.setEnterpriseId(ENT_ID);
            request.setUserId(USER_ID);

            assertThrows(IllegalArgumentException.class,
                () -> service.addMember(request, CREATED_BY));
        }

        @Test
        @DisplayName("§5.1.3 addMember — 企业不存在，抛异常")
        void addMemberEnterpriseNotFound() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(null);

            var request = new EnterpriseMemberAddRequest();
            request.setEnterpriseId(ENT_ID);
            request.setUserId(USER_ID);

            assertThrows(IllegalArgumentException.class,
                () -> service.addMember(request, CREATED_BY));
        }

        @Test
        @DisplayName("§5.1.4 addMember — 重新激活已移除成员")
        void addMemberReactivate() {
            FbsEnterpriseMember removed = buildMember(2);
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(sysUserMapper.selectUserById(USER_ID)).thenReturn(buildSysUser());
            when(memberMapper.selectByEnterpriseAndUser(ENT_ID, USER_ID)).thenReturn(removed);
            when(enterprisePackMapper.selectByEnterpriseId(ENT_ID)).thenReturn(Collections.emptyList());

            var request = new EnterpriseMemberAddRequest();
            request.setEnterpriseId(ENT_ID);
            request.setUserId(USER_ID);

            Long id = service.addMember(request, CREATED_BY);

            assertEquals(MEMBER_ID, id);
            ArgumentCaptor<FbsEnterpriseMember> captor = ArgumentCaptor.forClass(FbsEnterpriseMember.class);
            verify(memberMapper).updateMember(captor.capture());
            assertEquals(1, captor.getValue().getStatus()); // 重新激活
        }

        // ---- 新增 §E3 ----
        @Test
        @DisplayName("§E3.1 addMember — 企业已禁用（status=2），抛出 IllegalArgumentException")
        void addMemberEnterpriseDisabled() {
            FbsEnterprise disabled = buildEnterprise();
            disabled.setStatus(2);
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(disabled);

            var request = new EnterpriseMemberAddRequest();
            request.setEnterpriseId(ENT_ID);
            request.setUserId(USER_ID);

            assertThrows(IllegalArgumentException.class,
                () -> service.addMember(request, CREATED_BY));
            verify(memberMapper, never()).insertMember(any());
        }

        @Test
        @DisplayName("§E3.2 addMember — 重新激活（status=2→1），调用 updateMember，不调用 insert")
        void addMemberReactivateUpdate() {
            FbsEnterpriseMember removed = buildMember(2);
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(sysUserMapper.selectUserById(USER_ID)).thenReturn(buildSysUser());
            when(memberMapper.selectByEnterpriseAndUser(ENT_ID, USER_ID)).thenReturn(removed);
            when(enterprisePackMapper.selectByEnterpriseId(ENT_ID)).thenReturn(Collections.emptyList());
            when(memberMapper.updateMember(any(FbsEnterpriseMember.class))).thenReturn(1);

            var request = new EnterpriseMemberAddRequest();
            request.setEnterpriseId(ENT_ID);
            request.setUserId(USER_ID);

            Long id = service.addMember(request, CREATED_BY);

            assertEquals(MEMBER_ID, id);
            ArgumentCaptor<FbsEnterpriseMember> captor = ArgumentCaptor.forClass(FbsEnterpriseMember.class);
            verify(memberMapper).updateMember(captor.capture());
            assertEquals(1, captor.getValue().getStatus());    // 重新激活
            assertEquals(MEMBER_ID, captor.getValue().getId()); // 更新同一记录
            verify(memberMapper, never()).insertMember(any());  // 不调用 insert
        }

        @Test
        @DisplayName("§E3.5 addMember — 自动继承企业所有 status=1 的包（ep2 已撤销不继承）")
        void addMemberInheritsOnlyActivePacks() {
            FbsEnterprisePack ep1 = buildEnterprisePack(); ep1.setId(1L);
            FbsEnterprisePack ep2 = buildEnterprisePack(); ep2.setId(2L);
            ep2.setStatus(3); // 已撤销，不应继承
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(sysUserMapper.selectUserById(USER_ID)).thenReturn(buildSysUser());
            when(memberMapper.selectByEnterpriseAndUser(ENT_ID, USER_ID)).thenReturn(null);
            when(enterprisePackMapper.selectByEnterpriseId(ENT_ID))
                .thenReturn(Arrays.asList(ep1, ep2));
            doAnswer(inv -> {
                FbsEnterpriseMember m = inv.getArgument(0);
                m.setId(MEMBER_ID);
                return 1;
            }).when(memberMapper).insertMember(any());

            var request = new EnterpriseMemberAddRequest();
            request.setEnterpriseId(ENT_ID);
            request.setUserId(USER_ID);

            service.addMember(request, CREATED_BY);

            ArgumentCaptor<List<FbsMemberPack>> captor = ArgumentCaptor.forClass(List.class);
            verify(memberPackMapper).insertMemberPackBatch(captor.capture());
            List<FbsMemberPack> batch = captor.getValue();
            // 只继承 status=1 的包（ep1），ep2（已撤销）不应在其中
            assertEquals(1, batch.size());
            assertEquals(1L, batch.get(0).getEnterprisePackId());
        }
    }

    // ========================================================================
    // §5.2 移除成员测试
    // ========================================================================

    @Nested
    @DisplayName("§5.2 移除成员")
    class RemoveMemberTests {

        @Test
        @DisplayName("§5.2.1 removeMember — 正常成员移除成功")
        void removeMemberSuccess() {
            when(memberMapper.selectById(MEMBER_ID)).thenReturn(buildMember(1));
            when(memberMapper.updateMember(any(FbsEnterpriseMember.class))).thenReturn(1);

            boolean result = service.removeMember(MEMBER_ID, CREATED_BY);

            assertTrue(result);
            ArgumentCaptor<FbsEnterpriseMember> captor = ArgumentCaptor.forClass(FbsEnterpriseMember.class);
            verify(memberMapper).updateMember(captor.capture());
            assertEquals(2, captor.getValue().getStatus()); // 已移除
        }

        @Test
        @DisplayName("§5.2.2 removeMember — 已移除成员重复移除失败（Fail-Closed）")
        void removeMemberAlreadyRemoved() {
            when(memberMapper.selectById(MEMBER_ID)).thenReturn(buildMember(2));

            boolean result = service.removeMember(MEMBER_ID, CREATED_BY);

            assertFalse(result);
            verify(memberMapper, never()).updateMember(any());
        }

        // ---- 新增 §E3 ----
        @Test
        @DisplayName("§E3.3 removeMember — 成员不存在，返回 false")
        void removeMemberNotFound() {
            when(memberMapper.selectById(999L)).thenReturn(null);

            boolean result = service.removeMember(999L, CREATED_BY);

            assertFalse(result);
            verify(memberMapper, never()).updateMember(any());
        }
    }
}

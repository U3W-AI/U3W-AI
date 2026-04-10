package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.*;
import com.wx.fbsir.business.fbs.dto.business.enterprise.*;
import com.wx.fbsir.business.fbs.mapper.*;
import com.wx.fbsir.business.fbs.service.business.impl.FbsEnterprisePackBusinessServiceImpl;
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
 * FbsEnterprisePackBusinessService 单元测试
 *
 * @author wxfbsir
 * @date 2026-04-09
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("企业场景包分发服务测试")
class FbsEnterprisePackBusinessServiceTest {

    @Mock private FbsEnterprisePackMapper enterprisePackMapper;
    @Mock private FbsEnterpriseMapper enterpriseMapper;
    @Mock private FbsScenePackMapper scenePackMapper;
    @Mock private FbsEnterpriseMemberMapper memberMapper;
    @Mock private FbsMemberPackMapper memberPackMapper;

    @InjectMocks
    private FbsEnterprisePackBusinessServiceImpl service;

    // ---- 常量 ----
    private static final Long ENT_ID = 1L;
    private static final Long PACK_ID = 2001L;
    private static final Long EPACK_ID = 5001L;
    private static final Long MEMBER_ID = 3001L;
    private static final String CREATED_BY = "admin";

    // ---- Fixture ----
    private FbsEnterprise buildEnterprise() {
        FbsEnterprise e = new FbsEnterprise();
        e.setId(ENT_ID);
        e.setEnterpriseName("悟空科技");
        e.setStatus(1);
        return e;
    }

    private FbsScenePack buildScenePack() {
        FbsScenePack p = new FbsScenePack();
        p.setId(PACK_ID);
        p.setPackCode("PACK_BOOK_WRITER");
        p.setPackName("写书助手专业版");
        p.setStatus(1);
        return p;
    }

    private FbsEnterprisePack buildEnterprisePack(int status) {
        FbsEnterprisePack ep = new FbsEnterprisePack();
        ep.setId(EPACK_ID);
        ep.setEnterpriseId(ENT_ID);
        ep.setPackId(PACK_ID);
        ep.setPackQuota(100);
        ep.setUsedQuota(0);
        ep.setStatus(status);
        return ep;
    }

    private FbsEnterpriseMember buildMember() {
        FbsEnterpriseMember m = new FbsEnterpriseMember();
        m.setId(MEMBER_ID);
        m.setEnterpriseId(ENT_ID);
        m.setUserId(10L);
        m.setRole("MEMBER");
        m.setStatus(1);
        return m;
    }

    // ========================================================================
    // §4.1 分发测试
    // ========================================================================

    @Nested
    @DisplayName("§4.1 分发")
    class GrantTests {

        @Test
        @DisplayName("§4.1.1 grantPackToEnterprise — 新增分发成功")
        void grantNewSuccess() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(scenePackMapper.selectByPackCode("PACK_BOOK_WRITER")).thenReturn(buildScenePack());
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(null);
            when(memberMapper.selectActiveByEnterpriseId(ENT_ID)).thenReturn(
                Arrays.asList(buildMember()));
            doAnswer(invocation -> {
                FbsEnterprisePack ep = invocation.getArgument(0);
                ep.setId(EPACK_ID);
                return 1;
            }).when(enterprisePackMapper).insertEnterprisePack(any(FbsEnterprisePack.class));

            var request = new EnterprisePackGrantRequest();
            request.setEnterpriseId(ENT_ID);
            request.setPackCode("PACK_BOOK_WRITER");
            request.setPackQuota(100);

            Long id = service.grantPackToEnterprise(request, CREATED_BY);

            assertNotNull(id);
            ArgumentCaptor<FbsEnterprisePack> epCaptor = ArgumentCaptor.forClass(FbsEnterprisePack.class);
            verify(enterprisePackMapper).insertEnterprisePack(epCaptor.capture());
            assertEquals(100, epCaptor.getValue().getPackQuota());
            assertEquals(0, epCaptor.getValue().getUsedQuota());
            assertEquals(1, epCaptor.getValue().getStatus());

            // 验证成员授权记录批量创建
            verify(memberPackMapper).insertMemberPackBatch(anyList());
        }

        @Test
        @DisplayName("§4.1.2 grantPackToEnterprise — 幂等分发（已授权返回已有）")
        void grantIdempotent() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(scenePackMapper.selectByPackCode("PACK_BOOK_WRITER")).thenReturn(buildScenePack());
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(buildEnterprisePack(1));

            var request = new EnterprisePackGrantRequest();
            request.setEnterpriseId(ENT_ID);
            request.setPackCode("PACK_BOOK_WRITER");
            request.setPackQuota(100);

            Long id = service.grantPackToEnterprise(request, CREATED_BY);

            assertEquals(EPACK_ID, id);
            verify(enterprisePackMapper, never()).insertEnterprisePack(any());
        }

        @Test
        @DisplayName("§4.1.3 grantPackToEnterprise — 重新授权（已撤销→重新授权，usedQuota归零）")
        void grantReauthorize() {
            FbsEnterprisePack revoked = buildEnterprisePack(3);
            revoked.setUsedQuota(50);
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(scenePackMapper.selectByPackCode("PACK_BOOK_WRITER")).thenReturn(buildScenePack());
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(revoked);
            when(memberMapper.selectActiveByEnterpriseId(ENT_ID)).thenReturn(Collections.emptyList());

            var request = new EnterprisePackGrantRequest();
            request.setEnterpriseId(ENT_ID);
            request.setPackCode("PACK_BOOK_WRITER");
            request.setPackQuota(200);

            Long id = service.grantPackToEnterprise(request, CREATED_BY);

            assertEquals(EPACK_ID, id);
            ArgumentCaptor<FbsEnterprisePack> captor = ArgumentCaptor.forClass(FbsEnterprisePack.class);
            verify(enterprisePackMapper).updateEnterprisePack(captor.capture());
            assertEquals(200, captor.getValue().getPackQuota());
            assertEquals(0, captor.getValue().getUsedQuota()); // 归零
            assertEquals(1, captor.getValue().getStatus());    // 重新授权
        }

        @Test
        @DisplayName("§4.1.4 grantPackToEnterprise — 企业不存在，抛异常")
        void grantEnterpriseNotFound() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(null);

            var request = new EnterprisePackGrantRequest();
            request.setEnterpriseId(ENT_ID);
            request.setPackCode("PACK_BOOK_WRITER");
            request.setPackQuota(100);

            assertThrows(IllegalArgumentException.class,
                () -> service.grantPackToEnterprise(request, CREATED_BY));
        }

        @Test
        @DisplayName("§4.1.5 grantPackToEnterprise — 场景包未发布，抛异常")
        void grantPackNotPublished() {
            FbsScenePack draft = buildScenePack();
            draft.setStatus(0); // 草稿
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(scenePackMapper.selectByPackCode("PACK_BOOK_WRITER")).thenReturn(draft);

            var request = new EnterprisePackGrantRequest();
            request.setEnterpriseId(ENT_ID);
            request.setPackCode("PACK_BOOK_WRITER");
            request.setPackQuota(100);

            assertThrows(IllegalArgumentException.class,
                () -> service.grantPackToEnterprise(request, CREATED_BY));
        }

        // ---- 新增 §E2 ----
        @Test
        @DisplayName("§E2.1 grantPackToEnterprise — status=1 时幂等，不调用 update/insert")
        void grantIdempotentDoesNotUpdate() {
            // GIVEN 已授权记录 status=1，packQuota=100，usedQuota=30
            FbsEnterprisePack existing = buildEnterprisePack(1);
            existing.setPackQuota(100);
            existing.setUsedQuota(30);
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(scenePackMapper.selectByPackCode("PACK_BOOK_WRITER")).thenReturn(buildScenePack());
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(existing);

            var request = new EnterprisePackGrantRequest();
            request.setEnterpriseId(ENT_ID);
            request.setPackCode("PACK_BOOK_WRITER");
            request.setPackQuota(200); // 故意传新配额，不应更新

            Long id = service.grantPackToEnterprise(request, CREATED_BY);

            assertEquals(EPACK_ID, id);
            // 关键：幂等路径不调用 update/insert
            verify(enterprisePackMapper, never()).updateEnterprisePack(any());
            verify(enterprisePackMapper, never()).insertEnterprisePack(any());
        }

        @Test
        @DisplayName("§E2.2 grantPackToEnterprise — 重新授权断言完整：更新 + 重新批量授权，不新建")
        void grantReauthorizeCompleteAssertion() {
            FbsEnterprisePack revoked = buildEnterprisePack(3);
            revoked.setUsedQuota(50);
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(scenePackMapper.selectByPackCode("PACK_BOOK_WRITER")).thenReturn(buildScenePack());
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(revoked);
            when(memberMapper.selectActiveByEnterpriseId(ENT_ID)).thenReturn(Collections.emptyList());

            var request = new EnterprisePackGrantRequest();
            request.setEnterpriseId(ENT_ID);
            request.setPackCode("PACK_BOOK_WRITER");
            request.setPackQuota(200);

            service.grantPackToEnterprise(request, CREATED_BY);

            // 不新建（复用已有记录）
            verify(enterprisePackMapper, never()).insertEnterprisePack(any());
            // 确认更新了已有记录
            verify(enterprisePackMapper).updateEnterprisePack(any());
        }

        @Test
        @DisplayName("§E2.3 grantPackToEnterprise — 场景包不存在，抛出 IllegalArgumentException")
        void grantPackNotFound() {
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(scenePackMapper.selectByPackCode("INVALID_PACK")).thenReturn(null);

            var request = new EnterprisePackGrantRequest();
            request.setEnterpriseId(ENT_ID);
            request.setPackCode("INVALID_PACK");
            request.setPackQuota(100);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.grantPackToEnterprise(request, CREATED_BY));
            assertTrue(ex.getMessage().contains("场景包不存在"));
        }

        @Test
        @DisplayName("§E2.4 grantPackToEnterprise — 新增分发批量写 memberPack，字段正确")
        void grantNewMemberPackFields() {
            FbsEnterpriseMember m1 = buildMember(); m1.setId(1L);
            FbsEnterpriseMember m2 = buildMember(); m2.setId(2L);
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(scenePackMapper.selectByPackCode("PACK_BOOK_WRITER")).thenReturn(buildScenePack());
            when(enterprisePackMapper.selectByEnterpriseAndPack(ENT_ID, PACK_ID)).thenReturn(null);
            when(memberMapper.selectActiveByEnterpriseId(ENT_ID)).thenReturn(Arrays.asList(m1, m2));
            doAnswer(inv -> {
                FbsEnterprisePack ep = inv.getArgument(0);
                ep.setId(EPACK_ID);
                return 1;
            }).when(enterprisePackMapper).insertEnterprisePack(any());

            var request = new EnterprisePackGrantRequest();
            request.setEnterpriseId(ENT_ID);
            request.setPackCode("PACK_BOOK_WRITER");
            request.setPackQuota(100);

            service.grantPackToEnterprise(request, CREATED_BY);

            ArgumentCaptor<List<FbsMemberPack>> captor = ArgumentCaptor.forClass(List.class);
            verify(memberPackMapper).insertMemberPackBatch(captor.capture());
            List<FbsMemberPack> batch = captor.getValue();
            assertEquals(2, batch.size());
            for (FbsMemberPack mp : batch) {
                assertEquals(EPACK_ID, mp.getEnterprisePackId()); // 企业包 ID
                assertEquals(PACK_ID, mp.getPackId());              // 场景包 ID
                assertEquals(1, mp.getStatus());                    // 授权状态
                assertNotNull(mp.getMemberId());
            }
        }
    }

    // ========================================================================
    // §4.2 撤销测试
    // ========================================================================

    @Nested
    @DisplayName("§4.2 撤销")
    class RevokeTests {

        @Test
        @DisplayName("§4.2.1 revokePackFromEnterprise — 撤销成功")
        void revokeSuccess() {
            when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(buildEnterprisePack(1));
            when(enterprisePackMapper.updateEnterprisePack(any(FbsEnterprisePack.class))).thenReturn(1);
            when(memberPackMapper.updateStatusByEnterprisePackId(eq(EPACK_ID), eq(4), anyString())).thenReturn(1);

            boolean result = service.revokePackFromEnterprise(EPACK_ID, CREATED_BY);

            assertTrue(result);
            ArgumentCaptor<FbsEnterprisePack> captor = ArgumentCaptor.forClass(FbsEnterprisePack.class);
            verify(enterprisePackMapper).updateEnterprisePack(captor.capture());
            assertEquals(3, captor.getValue().getStatus()); // 已撤销（status=3）
            verify(memberPackMapper).updateStatusByEnterprisePackId(EPACK_ID, 4, CREATED_BY);
        }

        @Test
        @DisplayName("§4.2.2 revokePackFromEnterprise — 已撤销重复撤销失败（Fail-Closed）")
        void revokeAlreadyRevoked() {
            when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(buildEnterprisePack(3));

            boolean result = service.revokePackFromEnterprise(EPACK_ID, CREATED_BY);

            assertFalse(result);
            verify(enterprisePackMapper, never()).updateEnterprisePack(any());
        }

        // ---- 新增 §E2 ----
        @Test
        @DisplayName("§E2.5 revokePackFromEnterprise — 企业包不存在，返回 false")
        void revokeNotFound() {
            when(enterprisePackMapper.selectById(999L)).thenReturn(null);

            boolean result = service.revokePackFromEnterprise(999L, CREATED_BY);

            assertFalse(result);
            verify(enterprisePackMapper, never()).updateEnterprisePack(any());
        }

        @Test
        @DisplayName("§E2.7 revokePackFromEnterprise — status=3（已撤销）重复撤销返回 false")
        void revokeAlreadyRevokedStatus3() {
            // Review#3 变更：Fail-Closed 条件为 status != 1，status=3 同样被拒绝
            when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(buildEnterprisePack(3));

            boolean result = service.revokePackFromEnterprise(EPACK_ID, CREATED_BY);

            assertFalse(result);
            verify(enterprisePackMapper, never()).updateEnterprisePack(any());
            verify(memberPackMapper, never()).updateStatusByEnterprisePackId(anyLong(), anyInt(), anyString());
        }
    }

    // ========================================================================
    // §4.3 查询测试
    // ========================================================================

    @Nested
    @DisplayName("§4.3 查询")
    class QueryTests {

        @Test
        @DisplayName("§4.3.1 getDetail — remainQuota实时计算正确")
        void getDetailRemainQuota() {
            FbsEnterprisePack epack = buildEnterprisePack(1);
            epack.setPackQuota(100);
            epack.setUsedQuota(30);
            when(enterprisePackMapper.selectById(EPACK_ID)).thenReturn(epack);
            when(enterpriseMapper.selectById(ENT_ID)).thenReturn(buildEnterprise());
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(buildScenePack());

            EnterprisePackDetailResponse detail = service.getDetail(EPACK_ID);

            assertNotNull(detail);
            assertEquals(70, detail.getRemainQuota()); // 100 - 30
            assertEquals("已授权", detail.getStatusDesc());
        }

        // ---- 新增 §E2 ----
        @Test
        @DisplayName("§E2.8 getEnterprisePackPage — status 筛选透传正确")
        void getEnterprisePackPageWithStatus() {
            FbsEnterprisePack ep = buildEnterprisePack(1);
            when(enterprisePackMapper.selectEnterprisePackList(any(FbsEnterprisePack.class)))
                .thenReturn(Arrays.asList(ep));

            EnterprisePackPageRequest request = new EnterprisePackPageRequest();
            request.setEnterpriseId(ENT_ID);
            request.setStatus(1);

            List<FbsEnterprisePack> result = service.getEnterprisePackPage(request);

            assertEquals(1, result.size());
            ArgumentCaptor<FbsEnterprisePack> captor = ArgumentCaptor.forClass(FbsEnterprisePack.class);
            verify(enterprisePackMapper).selectEnterprisePackList(captor.capture());
            assertEquals(ENT_ID, captor.getValue().getEnterpriseId());
            assertEquals(1, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("§E2.9 getEnterprisePackPage — enterpriseId 为空返回空列表")
        void getEnterprisePackPageNullEnterpriseId() {
            EnterprisePackPageRequest request = new EnterprisePackPageRequest();
            // enterpriseId = null

            List<FbsEnterprisePack> result = service.getEnterprisePackPage(request);

            assertTrue(result.isEmpty());
            verify(enterprisePackMapper, never()).selectEnterprisePackList(any());
        }
    }
}

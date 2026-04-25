package com.wx.fbsir.business.fbs.service.business;

import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.dto.self.ScenePackClaimRequestDTO;
import com.wx.fbsir.business.fbs.dto.self.ScenePackClaimResponseDTO;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.service.business.impl.FbsUserSelfServiceBusinessServiceImpl;
import com.wx.fbsir.common.core.domain.model.LoginUser;
import com.wx.fbsir.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FbsUserSelfService — claimScenePack 完整单元测试
 *
 * 覆盖：tasks.md §7.4 + 补充边界用例
 * - §7.4.1 正常领取成功返回 HTTP 200，fbs_user_pack 记录写入
 * - §7.4.2 重复领取返回 HTTP 200（幂等），msg=已领取该场景包
 * - §7.4.3 场景包不存在返回 PACK_NOT_FOUND（404）
 * - §7.4.4 场景包已下架返回 PACK_OFFLINE（400）
 * - §7.4.5 企业包（owner_type!=1）返回 PACK_PRIVATE（400）
 * - §7.4.6 付费包（pointsRuleCode!=null）返回 PACK_NOT_FREE（400）
 * - §7.4.7 未登录抛 ServiceException(401)
 * - §7.4.8 DuplicateKeyException 捕获后返回 ALREADY_CLAIMED（并发幂等兜底）
 * - 补充1: claim 写入时审计字段验证（operator/requestId/operateTime）
 * - 补充2: claim 写入时 sourceType=1 和 status=1 验证
 * - 补充3: packId 为 null 时走 selectById(null) → 视 Mapper 实现返回 null → PACK_NOT_FOUND
 * - 补充4: Fail-Closed 校验链短路——已下架包不继续检查 ownerType
 *
 * @author wxfbsir
 * @date 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户自助 — claimScenePack")
class FbsUserSelfServiceClaimTest {

    @Mock
    private FbsUserPackMapper userPackMapper;
    @Mock
    private FbsScenePackMapper scenePackMapper;

    @InjectMocks
    private FbsUserSelfServiceBusinessServiceImpl service;

    private static final Long TEST_USER_ID = 1001L;
    private static final Long PACK_ID = 2001L;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(Long userId) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(userId);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(loginUser);
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(context);
    }

    private FbsScenePack buildPack(Long packId, int ownerType, int status, String pointsRuleCode) {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(packId);
        pack.setPackName("标准版");
        pack.setOwnerType(ownerType);
        pack.setStatus(status);
        pack.setPointsRuleCode(pointsRuleCode);
        pack.setCurrentVersion("v1.0");
        return pack;
    }

    // ========================================================================
    // §7.4 claimScenePack
    // ========================================================================

    @Nested
    @DisplayName("§7.4 claimScenePack")
    class ClaimTests {

        @Test
        @DisplayName("§7.4.1 正常领取成功返回 HTTP 200，fbs_user_pack 记录写入")
        void claimSuccess() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(PACK_ID, 1, 1, null));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(0);
            when(userPackMapper.insertUserPack(any())).thenReturn(1);

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            ScenePackClaimResponseDTO resp = service.claimScenePack(req);

            assertEquals("领取成功", resp.getMsg());
            assertEquals(PACK_ID, resp.getPackId());
            assertEquals("标准版", resp.getPackName());
            assertNull(resp.getExpiresAt()); // MVP 不设过期
            verify(userPackMapper).insertUserPack(any());
        }

        @Test
        @DisplayName("§7.4.2 重复领取返回 HTTP 200（幂等），msg=已领取该场景包")
        void idempotentClaim() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(PACK_ID, 1, 1, null));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(1);

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            ScenePackClaimResponseDTO resp = service.claimScenePack(req);

            assertEquals("已领取该场景包", resp.getMsg());
            assertEquals(PACK_ID, resp.getPackId());
            assertEquals("标准版", resp.getPackName());
            verify(userPackMapper, never()).insertUserPack(any());
        }

        @Test
        @DisplayName("§7.4.3 场景包不存在返回 PACK_NOT_FOUND（404）")
        void packNotFound() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(PACK_ID)).thenReturn(null);

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.claimScenePack(req));
            assertEquals(404, ex.getCode());
            assertEquals("PACK_NOT_FOUND", ex.getMessage());
        }

        @Test
        @DisplayName("§7.4.4 场景包已下架返回 PACK_OFFLINE（400）")
        void packOffline() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(PACK_ID, 1, 2, null)); // status=2

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.claimScenePack(req));
            assertEquals(400, ex.getCode());
            assertEquals("PACK_OFFLINE", ex.getMessage());
        }

        @Test
        @DisplayName("§7.4.5 企业包（owner_type!=1）返回 PACK_PRIVATE（400）")
        void packPrivate() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(PACK_ID, 2, 1, null)); // owner_type=2（企业包）

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.claimScenePack(req));
            assertEquals(400, ex.getCode());
            assertEquals("PACK_PRIVATE", ex.getMessage());
        }

        @Test
        @DisplayName("§7.4.6 付费包（pointsRuleCode!=null）返回 PACK_NOT_FREE（400）")
        void packNotFree() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(PACK_ID, 1, 1, "RULE001")); // 需付费

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.claimScenePack(req));
            assertEquals(400, ex.getCode());
            assertEquals("PACK_NOT_FREE", ex.getMessage());
        }

        @Test
        @DisplayName("§7.4.7 未登录抛 ServiceException(401)")
        void notLoggedIn() {
            // SecurityUtils.getUserId() 无 SecurityContext 时抛 401
            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);

            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.claimScenePack(req));
            assertEquals(401, ex.getCode());
        }

        @Test
        @DisplayName("§7.4.8 DuplicateKeyException 捕获后返回 ALREADY_CLAIMED（并发幂等兜底）")
        void duplicateKeyException() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(PACK_ID, 1, 1, null));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(0);
            when(userPackMapper.insertUserPack(any()))
                    .thenThrow(new DuplicateKeyException("Duplicate entry"));

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            ScenePackClaimResponseDTO resp = service.claimScenePack(req);

            assertEquals("已领取该场景包", resp.getMsg());
            assertEquals(PACK_ID, resp.getPackId());
        }

        @Test
        @DisplayName("补充: claim 写入时审计字段验证（operator/requestId/operateTime）")
        void claimSuccess_auditFields() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(PACK_ID, 1, 1, null));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(0);
            when(userPackMapper.insertUserPack(any())).thenReturn(1);

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            service.claimScenePack(req);

            // 验证 insertUserPack 传入的 userPack 包含审计字段
            verify(userPackMapper).insertUserPack(argThat(userPack -> {
                // operator = 当前用户ID
                assertEquals(TEST_USER_ID, userPack.getOperator());
                // requestId 不为空
                assertNotNull(userPack.getRequestId());
                assertFalse(userPack.getRequestId().isEmpty());
                // operateTime 不为空
                assertNotNull(userPack.getOperateTime());
                return true;
            }));
        }

        @Test
        @DisplayName("补充: claim 写入时 sourceType=1 和 status=1 验证")
        void claimSuccess_sourceTypeAndStatus() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(PACK_ID, 1, 1, null));
            when(userPackMapper.selectExistsByUserIdAndPackId(TEST_USER_ID, PACK_ID)).thenReturn(0);
            when(userPackMapper.insertUserPack(any())).thenReturn(1);

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            service.claimScenePack(req);

            verify(userPackMapper).insertUserPack(argThat(userPack -> {
                assertEquals(1, userPack.getSourceType());  // 平台分发
                assertEquals(1, userPack.getStatus());       // 有效
                assertEquals(PACK_ID, userPack.getPackId());
                assertEquals("v1.0", userPack.getPackVersion()); // 版本快照
                assertNotNull(userPack.getActivatedAt());
                return true;
            }));
        }

        @Test
        @DisplayName("补充: packId 为 null 时 selectById(null) 返回 null → PACK_NOT_FOUND")
        void packIdNull() {
            mockSecurityContext(TEST_USER_ID);
            when(scenePackMapper.selectById(null)).thenReturn(null);

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(null);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.claimScenePack(req));
            assertEquals(404, ex.getCode());
            assertEquals("PACK_NOT_FOUND", ex.getMessage());
        }

        @Test
        @DisplayName("补充: Fail-Closed 校验链短路——已下架包(status=2)不继续检查 ownerType")
        void failClosed_shortCircuit() {
            mockSecurityContext(TEST_USER_ID);
            // status=2 (已下架) + ownerType=2 (企业包) — 应先命中 PACK_OFFLINE
            when(scenePackMapper.selectById(PACK_ID))
                    .thenReturn(buildPack(PACK_ID, 2, 2, "RULE001"));

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(PACK_ID);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.claimScenePack(req));
            // 应该是 PACK_OFFLINE（status 检查在前），而不是 PACK_PRIVATE 或 PACK_NOT_FREE
            assertEquals("PACK_OFFLINE", ex.getMessage());
        }
    }
}

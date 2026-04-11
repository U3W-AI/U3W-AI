package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.dto.business.auth_code.AuthCodeGenerateRequest;
import com.wx.fbsir.business.fbs.dto.business.auth_code.AuthCodePageRequest;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.service.business.impl.FbsAuthCodeBusinessServiceImpl;
import com.wx.fbsir.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.stubbing.Answer;

/**
 * FbsAuthCodeBusinessService 单元测试
 *
 * 覆盖 tasks.md §9.2 授权码运营测试（共6个）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("授权码运营服务测试")
class FbsAuthCodeBusinessServiceTest {

    @Mock
    private FbsAuthCodeMapper authCodeMapper;

    @Mock
    private FbsScenePackMapper scenePackMapper;

    @InjectMocks
    private FbsAuthCodeBusinessServiceImpl service;

    // ---- 常量 ----
    private static final Long CODE_ID = 3001L;
    private static final Long TARGET_ID = 2001L;

    // ---- Fixture ----
    private FbsAuthCode buildCode(int available, int status) {
        FbsAuthCode code = new FbsAuthCode();
        code.setId(CODE_ID);
        code.setTargetId(TARGET_ID);
        code.setAvailable(available);
        code.setStatus(status);
        code.setMaxActivations(5);
        code.setActivatedCount(0);
        code.setDelFlag("0");
        return code;
    }

    // ========================================================================
    // §9.2 授权码运营测试
    // ========================================================================

    @Nested
    @DisplayName("§9.2.1-9.2.6 授权码运营")
    class AuthCodeOperationTests {

        @Test
        @DisplayName("§9.2.1 generateAuthCodeBatch — 批量生成N个码")
        void generateBatch() {
            var request = new AuthCodeGenerateRequest();
            request.setCount(3);
            request.setTargetId(TARGET_ID);
            request.setTargetType("SCENE_PACK");
            request.setIssuerType(1);
            request.setMaxActivations(1);
            request.setIssuerId(1L); // 显式设置 issuerId，避免走到 SecurityUtils.getUserId()

            // mock 场景包存在且上架
            FbsScenePack pack = new FbsScenePack();
            pack.setId(TARGET_ID);
            pack.setStatus(1);
            when(scenePackMapper.selectById(TARGET_ID)).thenReturn(pack);

            // 模拟 MyBatis useGeneratedKeys 回填 id（每次 insert 赋递增主键）
            AtomicLong idGen = new AtomicLong(5001L);
            doAnswer((Answer<Integer>) invocation -> {
                FbsAuthCode inserted = invocation.getArgument(0);
                inserted.setId(idGen.getAndIncrement());
                return 1;
            }).when(authCodeMapper).insertAuthCode(any(FbsAuthCode.class));

            Map<Long, String> result = service.generateAuthCodeBatch(request, "admin");

            assertEquals(3, result.size());
            verify(authCodeMapper, times(3)).insertAuthCode(any(FbsAuthCode.class));
        }

        @Test
        @DisplayName("§9.2.2 disableAuthCode — 禁用成功")
        void disableSuccess() {
            when(authCodeMapper.selectById(CODE_ID)).thenReturn(buildCode(1, 0));
            when(authCodeMapper.updateAvailable(CODE_ID, 0)).thenReturn(1);

            boolean result = service.disableAuthCode(CODE_ID);

            assertTrue(result);
            verify(authCodeMapper).updateAvailable(CODE_ID, 0);
        }

        @Test
        @DisplayName("§9.2.3 enableAuthCode — 启用成功")
        void enableSuccess() {
            when(authCodeMapper.selectById(CODE_ID)).thenReturn(buildCode(0, 0));
            when(authCodeMapper.updateAvailable(CODE_ID, 1)).thenReturn(1);

            boolean result = service.enableAuthCode(CODE_ID);

            assertTrue(result);
            verify(authCodeMapper).updateAvailable(CODE_ID, 1);
        }

        @Test
        @DisplayName("§9.2.4 enableAuthCode — 已过期/已用尽/已撤销状态启用失败（Fail-Closed）")
        void enableFailClosed() {
            // status=3（已过期）
            when(authCodeMapper.selectById(CODE_ID)).thenReturn(buildCode(0, 3));

            boolean result = service.enableAuthCode(CODE_ID);

            assertFalse(result);
            verify(authCodeMapper, never()).updateAvailable(anyLong(), anyInt());
        }

        @Test
        @DisplayName("§9.2.5 revokeAuthCode — 撤销成功")
        void revokeSuccess() {
            when(authCodeMapper.selectById(CODE_ID)).thenReturn(buildCode(1, 0));
            when(authCodeMapper.updateStatus(CODE_ID, 4)).thenReturn(1);

            boolean result = service.revokeAuthCode(CODE_ID);

            assertTrue(result);
            verify(authCodeMapper).updateStatus(CODE_ID, 4);
        }

        @Test
        @DisplayName("§9.2.6 revokeAuthCode — 重复撤销失败（Fail-Closed）")
        void revokeAlreadyRevoked() {
            // status=4（已撤销）
            when(authCodeMapper.selectById(CODE_ID)).thenReturn(buildCode(1, 4));

            boolean result = service.revokeAuthCode(CODE_ID);

            assertFalse(result);
            verify(authCodeMapper, never()).updateStatus(anyLong(), anyInt());
        }

        @Test
        @DisplayName("generateAuthCodeBatch — 场景包不存在时抛异常")
        void generate_packNotFound() {
            var request = new AuthCodeGenerateRequest();
            request.setCount(1);
            request.setTargetId(9999L);
            request.setTargetType("SCENE_PACK");
            request.setIssuerId(1L);

            when(scenePackMapper.selectById(9999L)).thenReturn(null);

            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.generateAuthCodeBatch(request, "admin"));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("场景包不存在"));
        }

        @Test
        @DisplayName("generateAuthCodeBatch — 场景包已下架时抛异常")
        void generate_packOffline() {
            var request = new AuthCodeGenerateRequest();
            request.setCount(1);
            request.setTargetId(TARGET_ID);
            request.setTargetType("SCENE_PACK");
            request.setIssuerId(1L);

            FbsScenePack pack = new FbsScenePack();
            pack.setId(TARGET_ID);
            pack.setStatus(2); // 已下架
            when(scenePackMapper.selectById(TARGET_ID)).thenReturn(pack);

            ServiceException ex = assertThrows(ServiceException.class,
                    () -> service.generateAuthCodeBatch(request, "admin"));
            assertEquals(400, ex.getCode());
            assertTrue(ex.getMessage().contains("场景包已下架"));
        }
    }
}

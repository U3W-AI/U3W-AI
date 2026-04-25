package com.wx.fbsir.business.fbs.controller.business;

import com.wx.fbsir.business.fbs.dto.self.*;
import com.wx.fbsir.business.fbs.service.business.IFbsUserSelfServiceBusinessService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import com.wx.fbsir.common.exception.ServiceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * FbsUserSelfServiceController — Controller 层单元测试
 *
 * 覆盖重点：异常处理和错误码透传
 * - 1: ServiceException(400, "AUTH_CODE_INVALID") → AjaxResult.error(400, "AUTH_CODE_INVALID")
 * - 2: ServiceException(404, "PACK_NOT_FOUND") → AjaxResult.error(404, "PACK_NOT_FOUND")
 * - 3: ServiceException(401, "SESSION_REQUIRED") → AjaxResult.error(401, "SESSION_REQUIRED")
 * - 4: ServiceException(null code, "msg") → AjaxResult.error("msg")（兜底）
 * - 5: RuntimeException 兜底 → AjaxResult.error(500, msg)
 * - 6: GET /my/packs 正常返回（startPage 由父类处理，不在此测试）
 * - 7: POST /my/auth-code/activate 正常返回
 * - 8: POST /my/scene-pack/claim 正常返回
 *
 * 注意：startPage()/getDataTable() 依赖 PageHelper 和 Servlet 上下文，
 * 在纯单元测试中无法直接调用。Controller 测试侧重异常处理透传逻辑。
 *
 * @author wxfbsir
 * @date 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户自助 — FbsUserSelfServiceController")
class FbsUserSelfServiceControllerTest {

    @Mock
    private IFbsUserSelfServiceBusinessService userSelfService;

    @InjectMocks
    private FbsUserSelfServiceController controller;

    // ========================================================================
    // 异常处理测试
    // ========================================================================

    @Nested
    @DisplayName("异常处理 — 错误码透传")
    class ExceptionHandlerTests {

        @Test
        @DisplayName("1: ServiceException(400, 'AUTH_CODE_INVALID') → AjaxResult(400, 'AUTH_CODE_INVALID')")
        void serviceException_400() {
            ServiceException ex = new ServiceException("AUTH_CODE_INVALID", 400);
            AjaxResult result = controller.handleServiceException(ex);

            assertEquals(400, result.get(AjaxResult.CODE_TAG));
            assertEquals("AUTH_CODE_INVALID", result.get(AjaxResult.MSG_TAG));
        }

        @Test
        @DisplayName("2: ServiceException(404, 'PACK_NOT_FOUND') → AjaxResult(404, 'PACK_NOT_FOUND')")
        void serviceException_404() {
            ServiceException ex = new ServiceException("PACK_NOT_FOUND", 404);
            AjaxResult result = controller.handleServiceException(ex);

            assertEquals(404, result.get(AjaxResult.CODE_TAG));
            assertEquals("PACK_NOT_FOUND", result.get(AjaxResult.MSG_TAG));
        }

        @Test
        @DisplayName("3: ServiceException(401, 'SESSION_REQUIRED') → AjaxResult(401, 'SESSION_REQUIRED')")
        void serviceException_401() {
            ServiceException ex = new ServiceException("SESSION_REQUIRED", 401);
            AjaxResult result = controller.handleServiceException(ex);

            assertEquals(401, result.get(AjaxResult.CODE_TAG));
            assertEquals("SESSION_REQUIRED", result.get(AjaxResult.MSG_TAG));
        }

        @Test
        @DisplayName("4: ServiceException(null code, 'msg') → AjaxResult.error('msg')（兜底）")
        void serviceException_nullCode() {
            ServiceException ex = new ServiceException("某业务错误"); // code=null
            AjaxResult result = controller.handleServiceException(ex);

            // AjaxResult.error(msg) 使用框架默认错误码（通常是 500）
            assertNotNull(result.get(AjaxResult.CODE_TAG));
            assertEquals("某业务错误", result.get(AjaxResult.MSG_TAG));
        }

        @Test
        @DisplayName("5: RuntimeException 兜底 → AjaxResult(500, msg)")
        void runtimeException_fallback() {
            RuntimeException ex = new RuntimeException("服务器内部错误");
            AjaxResult result = controller.handleRuntimeException(ex);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            assertEquals("服务器内部错误", result.get(AjaxResult.MSG_TAG));
        }
    }

    // ========================================================================
    // 正常调用链测试
    // ========================================================================

    @Nested
    @DisplayName("正常调用链 — 验证 BusinessService 调用")
    class NormalFlowTests {

        @Test
        @DisplayName("7: POST /my/auth-code/activate 正常返回 AjaxResult.success")
        void activateAuthCode_success() {
            AuthCodeActivateResponseDTO resp = new AuthCodeActivateResponseDTO();
            resp.setPackId(1L);
            resp.setPackName("标准版");
            resp.setMsg("激活成功");

            AuthCodeActivateRequestDTO req = new AuthCodeActivateRequestDTO();
            req.setAuthCode("AUTH123");
            when(userSelfService.activateAuthCode(any())).thenReturn(resp);

            AjaxResult result = controller.activateAuthCode(req);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            verify(userSelfService).activateAuthCode(req);
        }

        @Test
        @DisplayName("8: POST /my/scene-pack/claim 正常返回 AjaxResult.success")
        void claimScenePack_success() {
            ScenePackClaimResponseDTO resp = new ScenePackClaimResponseDTO();
            resp.setPackId(1L);
            resp.setPackName("标准版");
            resp.setMsg("领取成功");

            ScenePackClaimRequestDTO req = new ScenePackClaimRequestDTO();
            req.setPackId(1L);
            when(userSelfService.claimScenePack(any())).thenReturn(resp);

            AjaxResult result = controller.claimScenePack(req);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            verify(userSelfService).claimScenePack(req);
        }
    }
}

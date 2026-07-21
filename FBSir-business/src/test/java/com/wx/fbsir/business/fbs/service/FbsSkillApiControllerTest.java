package com.wx.fbsir.business.fbs.service;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.entity.FbsSkillUsageRecord;
import com.wx.fbsir.business.fbs.domain.entity.FbsUserPack;
import com.wx.fbsir.business.fbs.domain.enums.UsageStatus;
import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.dto.skillapi.*;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.mapper.FbsSkillUsageRecordMapper;
import com.wx.fbsir.business.fbs.mapper.FbsUserPackMapper;
import com.wx.fbsir.business.fbs.controller.skillapi.FbsSkillApiController;
import com.wx.fbsir.business.point.service.IPointsService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FbsSkillApiController 单元测试
 *
 * @author FBSir
 * @date 2026-04-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Skill API 网关控制器测试")
class FbsSkillApiControllerTest {

    @Mock
    private RightsCheckService rightsCheckService;

    @Mock
    private SkillConsumeService skillConsumeService;

    @Mock
    private FbsSkillUsageRecordMapper usageRecordMapper;

    @Mock
    private FbsScenePackMapper scenePackMapper;

    @Mock
    private FbsUserPackMapper userPackMapper;

    @Mock
    private IPointsService pointsService;

    @InjectMocks
    private FbsSkillApiController controller;

    // ---- 常量 ----
    private static final Long USER_ID = 1L;
    private static final String PACK_CODE = "pack_bookwriter_v2";
    private static final String SKILL_CODE = "bookwriter";
    private static final String USAGE_RECORD_ID = "wb-task-uuid-001";
    private static final String HOST_SESSION_ID = "host-session-001";
    private static final String AUTH_CODE = "ABCD1234EFGH5678";

    // ---- Fixture ----
    private FbsApiKey buildApiKey() {
        FbsApiKey key = new FbsApiKey();
        key.setId(1L);
        key.setApiKey("fbs_testkey123");
        key.setUserId(USER_ID);
        key.setName("测试Key");
        key.setPackCode(PACK_CODE);
        key.setRateLimitPerMin(60);
        key.setStatus(1);
        return key;
    }

    private FbsScenePack buildScenePack() {
        FbsScenePack pack = new FbsScenePack();
        pack.setId(1L);
        pack.setPackCode(PACK_CODE);
        pack.setPackName("写书助手 v2.0");
        pack.setCurrentVersion("1.0.0");
        pack.setStatus(1);
        pack.setPointsRuleCode("rule_bookwriter");
        pack.setContentSnapshot("{\"skills\":[\"write\",\"edit\"]}");
        return pack;
    }

    private FbsSkillUsageRecord buildUsageRecord(int status) {
        FbsSkillUsageRecord record = new FbsSkillUsageRecord();
        record.setId(1L);
        record.setUsageRecordId(USAGE_RECORD_ID);
        record.setUserId(USER_ID);
        record.setSkillCode(SKILL_CODE);
        record.setPackId(1L);
        record.setStatus(status);
        return record;
    }

    private void setupSecurityContext() {
        FbsApiKey apiKey = buildApiKey();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                apiKey, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_API")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ========================================================================
    // §6.1.2.1 rights/check 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.2.1 rights/check 权益校验")
    class RightsCheckTests {

        @Test
        @DisplayName("§6.1.2.1.1 权益校验通过")
        void rightsCheckPass() {
            setupSecurityContext();
            ComprehensiveRightsResult mockResult = ComprehensiveRightsResult.pass(1L, "rule_bookwriter", 10);
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE), isNull(), eq("WORKBUDDY"), isNull()))
                    .thenReturn(mockResult);

            SkillApiCheckRequest request = new SkillApiCheckRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);

            AjaxResult result = controller.rightsCheck(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
            assertEquals(true, data.get("pass"));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.1.2 权益校验失败 — 余额不足")
        void rightsCheckFail() {
            setupSecurityContext();
            ComprehensiveRightsResult mockResult = ComprehensiveRightsResult.fail("积分不足");
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE), isNull(), eq("WORKBUDDY"), isNull()))
                    .thenReturn(mockResult);

            SkillApiCheckRequest request = new SkillApiCheckRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);

            AjaxResult result = controller.rightsCheck(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
            assertEquals(false, data.get("pass"));
            assertEquals("积分不足", data.get("failReason"));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.1.3 userId 为空时使用 API Key 绑定用户")
        void rightsCheckUsesBoundUserWhenRequestUserIdIsMissing() {
            setupSecurityContext();
            when(rightsCheckService.comprehensiveCheck(eq(USER_ID), eq(PACK_CODE), isNull(),
                    eq("WORKBUDDY"), isNull()))
                    .thenReturn(ComprehensiveRightsResult.pass(1L, "rule_bookwriter", 10));
            SkillApiCheckRequest request = new SkillApiCheckRequest();
            request.setUserId(null);
            request.setPackCode(PACK_CODE);

            AjaxResult result = controller.rightsCheck(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.1.5 请求 userId 与 API Key 绑定用户冲突时拒绝")
        void rightsCheckRejectsUserMismatch() {
            setupSecurityContext();
            SkillApiCheckRequest request = new SkillApiCheckRequest();
            request.setUserId(999L);
            request.setPackCode(PACK_CODE);

            AjaxResult result = controller.rightsCheck(request);

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            assertEquals("SKILL_API_KEY_USER_MISMATCH", result.get(AjaxResult.MSG_TAG));
            verifyNoInteractions(rightsCheckService);
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.1.4 参数校验 — packCode 为空")
        void rightsCheckMissingPackCode() {
            setupSecurityContext();
            SkillApiCheckRequest request = new SkillApiCheckRequest();
            request.setUserId(USER_ID);
            request.setPackCode("");

            AjaxResult result = controller.rightsCheck(request);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }
    }

    // ========================================================================
    // §6.1.2.2 usage/consume 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.2.2 usage/consume 一次性消费")
    class UsageConsumeTests {

        @Test
        @DisplayName("§6.1.2.2.1 消费成功")
        void consumeSuccess() {
            setupSecurityContext();
            ConsumeResult mockResult = ConsumeResult.success(USAGE_RECORD_ID, 990);
            when(skillConsumeService.consume(eq(USER_ID), eq(PACK_CODE), eq(SKILL_CODE),
                    eq(USAGE_RECORD_ID), eq("WORKBUDDY"), eq(HOST_SESSION_ID), isNull()))
                    .thenReturn(mockResult);

            SkillApiConsumeRequest request = new SkillApiConsumeRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);
            request.setHostSessionId(HOST_SESSION_ID);

            AjaxResult result = controller.usageConsume(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.2.2 消费失败 — 余额不足")
        void consumeFailInsufficientPoints() {
            setupSecurityContext();
            ConsumeResult mockResult = ConsumeResult.fail(USAGE_RECORD_ID, "积分不足");
            when(skillConsumeService.consume(eq(USER_ID), eq(PACK_CODE), eq(SKILL_CODE),
                    eq(USAGE_RECORD_ID), eq("WORKBUDDY"), isNull(), isNull()))
                    .thenReturn(mockResult);

            SkillApiConsumeRequest request = new SkillApiConsumeRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageConsume(request);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.2.3 参数校验 — 缺少必填字段")
        void consumeMissingParams() {
            setupSecurityContext();
            SkillApiConsumeRequest request = new SkillApiConsumeRequest();
            request.setUserId(USER_ID);
            // 缺少 packCode, skillCode, usageRecordId

            AjaxResult result = controller.usageConsume(request);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }
    }

    // ========================================================================
    // §6.1.2.3 usage/start 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.2.3 usage/start 两阶段开始")
    class UsageStartTests {

        @Test
        @DisplayName("§6.1.2.3.1 首次 start — 创建新记录")
        void startNewRecord() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildScenePack());
            when(usageRecordMapper.insertUsageRecord(any(FbsSkillUsageRecord.class))).thenReturn(1);

            SkillApiStartRequest request = new SkillApiStartRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageStart(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            verify(usageRecordMapper).insertUsageRecord(any(FbsSkillUsageRecord.class));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.3.2 幂等 — status=0 返回已有记录")
        void startIdempotentInProgress() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildUsageRecord(UsageStatus.IN_PROGRESS.getCode()));
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildScenePack());

            SkillApiStartRequest request = new SkillApiStartRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageStart(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            verify(usageRecordMapper, never()).insertUsageRecord(any());
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.3.3 冲突 — status=1 返回 409")
        void startConflictSuccess() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildUsageRecord(UsageStatus.SUCCESS.getCode()));
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildScenePack());

            SkillApiStartRequest request = new SkillApiStartRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageStart(request);

            assertEquals(409, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.3.4 冲突 — status=2 返回 409")
        void startConflictFailed() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildUsageRecord(UsageStatus.FAILED.getCode()));
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildScenePack());

            SkillApiStartRequest request = new SkillApiStartRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageStart(request);

            assertEquals(409, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.3.5 场景包不存在 — 返回错误")
        void startPackNotFound() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(null);

            SkillApiStartRequest request = new SkillApiStartRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageStart(request);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.3.6 请求 userId 与 API Key 绑定用户冲突时零写入")
        void startRejectsUserMismatchBeforeReadOrWrite() {
            setupSecurityContext();
            SkillApiStartRequest request = new SkillApiStartRequest();
            request.setUserId(999L);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageStart(request);

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            assertEquals("SKILL_API_KEY_USER_MISMATCH", result.get(AjaxResult.MSG_TAG));
            verifyNoInteractions(usageRecordMapper, scenePackMapper);
            clearSecurityContext();
        }

        @Test
        @DisplayName("P0：已有记录属于其他用户时拒绝幂等重放")
        void startRejectsExistingRecordOwnedByOtherUser() {
            setupSecurityContext();
            FbsSkillUsageRecord otherUserRecord = buildUsageRecord(UsageStatus.IN_PROGRESS.getCode());
            otherUserRecord.setUserId(USER_ID + 1);
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(otherUserRecord);

            SkillApiStartRequest request = new SkillApiStartRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageStart(request);

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            assertEquals("SKILL_API_KEY_USER_MISMATCH", result.get(AjaxResult.MSG_TAG));
            verify(scenePackMapper, never()).selectByPackCode(anyString());
            verify(usageRecordMapper, never()).insertUsageRecord(any());
            clearSecurityContext();
        }
    }

    // ========================================================================
    // §6.1.2.4 usage/end 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.2.4 usage/end 两阶段结束")
    class UsageEndTests {

        @Test
        @DisplayName("§6.1.2.4.1 成功结束 — status 0→1")
        void endSuccess() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildUsageRecord(UsageStatus.IN_PROGRESS.getCode()));
            when(usageRecordMapper.updateStatusByRecordId(eq(USAGE_RECORD_ID), eq(1), isNull())).thenReturn(1);

            SkillApiEndRequest request = new SkillApiEndRequest();
            request.setStatus(1);

            AjaxResult result = controller.usageEnd(USAGE_RECORD_ID, request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            verify(usageRecordMapper).updateStatusByRecordId(USAGE_RECORD_ID, 1, null);
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.4.2 失败结束 — status 0→2")
        void endFailed() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildUsageRecord(UsageStatus.IN_PROGRESS.getCode()));
            when(usageRecordMapper.updateStatusByRecordId(eq(USAGE_RECORD_ID), eq(2), eq("超时"))).thenReturn(1);

            SkillApiEndRequest request = new SkillApiEndRequest();
            request.setStatus(2);
            request.setErrorMessage("超时");

            AjaxResult result = controller.usageEnd(USAGE_RECORD_ID, request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            verify(usageRecordMapper).updateStatusByRecordId(USAGE_RECORD_ID, 2, "超时");
            clearSecurityContext();
        }

        @Test
        @DisplayName("P1：并发 end 已先完成时 CAS 返回 0 并报告 409")
        void endRejectsConcurrentTerminalTransition() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildUsageRecord(UsageStatus.IN_PROGRESS.getCode()));
            when(usageRecordMapper.updateStatusByRecordId(
                    eq(USAGE_RECORD_ID), eq(1), isNull())).thenReturn(0);

            SkillApiEndRequest request = new SkillApiEndRequest();
            request.setStatus(1);

            AjaxResult result = controller.usageEnd(USAGE_RECORD_ID, request);

            assertEquals(409, result.get(AjaxResult.CODE_TAG));
            assertEquals("SKILL_USAGE_RECORD_STATE_CONFLICT", result.get(AjaxResult.MSG_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.4.3 记录不存在 — 返回 404")
        void endRecordNotFound() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID)).thenReturn(null);

            SkillApiEndRequest request = new SkillApiEndRequest();
            request.setStatus(1);

            AjaxResult result = controller.usageEnd(USAGE_RECORD_ID, request);

            assertEquals(404, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.4.4 幂等 — status=1 返回 409")
        void endConflictAlreadySuccess() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildUsageRecord(UsageStatus.SUCCESS.getCode()));

            SkillApiEndRequest request = new SkillApiEndRequest();
            request.setStatus(1);

            AjaxResult result = controller.usageEnd(USAGE_RECORD_ID, request);

            assertEquals(409, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.4.5 幂等 — status=2 返回 409")
        void endConflictAlreadyFailed() {
            setupSecurityContext();
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(buildUsageRecord(UsageStatus.FAILED.getCode()));

            SkillApiEndRequest request = new SkillApiEndRequest();
            request.setStatus(1);

            AjaxResult result = controller.usageEnd(USAGE_RECORD_ID, request);

            assertEquals(409, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.4.6 非法 status 参数 — 返回错误")
        void endInvalidStatus() {
            setupSecurityContext();
            SkillApiEndRequest request = new SkillApiEndRequest();
            request.setStatus(3); // 不合法

            AjaxResult result = controller.usageEnd(USAGE_RECORD_ID, request);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.4.7 API Key 不能结束其他用户的记录")
        void endRejectsRecordOwnedByAnotherUser() {
            setupSecurityContext();
            FbsSkillUsageRecord otherUserRecord = buildUsageRecord(
                    UsageStatus.IN_PROGRESS.getCode());
            otherUserRecord.setUserId(999L);
            when(usageRecordMapper.selectByRecordId(USAGE_RECORD_ID))
                    .thenReturn(otherUserRecord);
            SkillApiEndRequest request = new SkillApiEndRequest();
            request.setStatus(1);

            AjaxResult result = controller.usageEnd(USAGE_RECORD_ID, request);

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            assertEquals("SKILL_API_KEY_USER_MISMATCH", result.get(AjaxResult.MSG_TAG));
            verify(usageRecordMapper, never()).updateStatusByRecordId(any(), any(), any());
            clearSecurityContext();
        }
    }

    // ========================================================================
    // §6.1.2.5 scene-pack/query 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.2.5 scene-pack/query 场景包查询")
    class ScenePackQueryTests {

        @Test
        @DisplayName("§6.1.2.5.1 查询成功 — 返回 contentSnapshot 原始 JSON")
        void querySuccess() {
            setupSecurityContext();
            when(scenePackMapper.selectByPackCode(PACK_CODE)).thenReturn(buildScenePack());

            SkillApiScenePackQueryRequest request = new SkillApiScenePackQueryRequest();
            request.setPackCode(PACK_CODE);

            AjaxResult result = controller.scenePackQuery(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
            assertEquals(PACK_CODE, data.get("packCode"));
            assertEquals("写书助手 v2.0", data.get("packName"));
            assertEquals("{\"skills\":[\"write\",\"edit\"]}", data.get("contentSnapshot"));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.5.2 场景包不存在 — 返回错误")
        void queryPackNotFound() {
            setupSecurityContext();
            when(scenePackMapper.selectByPackCode("nonexistent")).thenReturn(null);

            SkillApiScenePackQueryRequest request = new SkillApiScenePackQueryRequest();
            request.setPackCode("nonexistent");

            AjaxResult result = controller.scenePackQuery(request);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.5.3 packCode 为空 — 返回错误")
        void queryMissingPackCode() {
            setupSecurityContext();
            SkillApiScenePackQueryRequest request = new SkillApiScenePackQueryRequest();
            request.setPackCode("");

            AjaxResult result = controller.scenePackQuery(request);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }
    }

    // ========================================================================
    // §6.1.2.6 user/info 测试
    // ========================================================================

    @Nested
    @DisplayName("§6.1.2.6 user/info 用户信息查询")
    class UserInfoTests {

        @Test
        @DisplayName("§6.1.2.6.1 查询成功 — 返回积分余额+场景包列表")
        void querySuccess() {
            setupSecurityContext();
            when(pointsService.getUserPoints(USER_ID)).thenReturn(990);

            FbsUserPack userPack = new FbsUserPack();
            userPack.setId(1L);
            userPack.setPackId(1L);
            userPack.setStatus(1);
            when(userPackMapper.selectActiveByUserId(USER_ID)).thenReturn(Collections.singletonList(userPack));
            when(scenePackMapper.selectById(1L)).thenReturn(buildScenePack());

            SkillApiUserInfoRequest request = new SkillApiUserInfoRequest();
            request.setUserId(USER_ID);

            AjaxResult result = controller.userInfo(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
            assertEquals(USER_ID, data.get("userId"));
            assertEquals(990, data.get("pointsBalance"));
            assertNotNull(data.get("activatedPacks"));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.6.2 积分为 null — 默认返回 0")
        void queryNullPoints() {
            setupSecurityContext();
            when(pointsService.getUserPoints(USER_ID)).thenReturn(null);
            when(userPackMapper.selectActiveByUserId(USER_ID)).thenReturn(Collections.emptyList());

            SkillApiUserInfoRequest request = new SkillApiUserInfoRequest();
            request.setUserId(USER_ID);

            AjaxResult result = controller.userInfo(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
            assertEquals(0, data.get("pointsBalance"));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§6.1.2.6.3 userId 为空时使用 API Key 绑定用户")
        void queryMissingUserIdUsesBinding() {
            setupSecurityContext();
            when(pointsService.getUserPoints(USER_ID)).thenReturn(990);
            when(userPackMapper.selectActiveByUserId(USER_ID)).thenReturn(Collections.emptyList());
            SkillApiUserInfoRequest request = new SkillApiUserInfoRequest();
            request.setUserId(null);

            AjaxResult result = controller.userInfo(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }
        
        // ===== OpenSpec #12 新增测试：API Key 反查 userId =====
        
        @Test
        @DisplayName("§12.1 user/info：不传 userId，从 API Key 反查成功")
        void queryFromApiKey() {
            // 设置 API Key 绑定用户
            FbsApiKey apiKey = buildApiKey();
            apiKey.setUserId(USER_ID);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    apiKey, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            when(pointsService.getUserPoints(USER_ID)).thenReturn(990);
            when(userPackMapper.selectActiveByUserId(USER_ID)).thenReturn(Collections.emptyList());

            SkillApiUserInfoRequest request = new SkillApiUserInfoRequest();
            // 不传 userId，身份只来自 API Key 绑定

            AjaxResult result = controller.userInfo(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
            assertEquals(USER_ID, data.get("userId"));
            assertEquals(990, data.get("pointsBalance"));
            clearSecurityContext();
        }
        
        @Test
        @DisplayName("§12.2 user/info：API Key 未绑定时不能回退信任请求 userId")
        void queryApiKeyNotBound() {
            // 设置 API Key 未绑定用户
            FbsApiKey apiKey = buildApiKey();
            apiKey.setUserId(null);  // 未绑定用户
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    apiKey, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            SkillApiUserInfoRequest request = new SkillApiUserInfoRequest();
            request.setUserId(USER_ID);

            AjaxResult result = controller.userInfo(request);

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }
        
        @Test
        @DisplayName("§12.3 user/info：请求 userId 与 API Key 绑定冲突时拒绝")
        void queryRejectsUserIdMismatch() {
            // 设置 API Key 绑定用户
            FbsApiKey apiKey = buildApiKey();
            apiKey.setUserId(USER_ID);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    apiKey, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            SkillApiUserInfoRequest request = new SkillApiUserInfoRequest();
            request.setUserId(999L);  // 传了不同的 userId

            AjaxResult result = controller.userInfo(request);

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            assertEquals("SKILL_API_KEY_USER_MISMATCH", result.get(AjaxResult.MSG_TAG));
            verifyNoInteractions(pointsService, userPackMapper);
            clearSecurityContext();
        }
    }
    
    // ========================================================================
    // OpenSpec #12：usage/consume API Key 反查测试
    // ========================================================================

    @Nested
    @DisplayName("§12 usage/consume API Key 反查")
    class UsageConsumeApiKeyTests {

        @Test
        @DisplayName("§12.4 usage/consume：不传 userId，从 API Key 反查成功")
        void consumeFromApiKey() {
            // 设置 API Key 绑定用户
            FbsApiKey apiKey = buildApiKey();
            apiKey.setUserId(USER_ID);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    apiKey, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            ConsumeResult mockResult = ConsumeResult.success(USAGE_RECORD_ID, 990);
            when(skillConsumeService.consume(eq(USER_ID), eq(PACK_CODE), eq(SKILL_CODE),
                    eq(USAGE_RECORD_ID), eq("WORKBUDDY"), isNull(), isNull()))
                    .thenReturn(mockResult);

            SkillApiConsumeRequest request = new SkillApiConsumeRequest();
            // 不传 userId，身份只来自 API Key 绑定
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageConsume(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }
        
        @Test
        @DisplayName("§12.5 usage/consume：API Key 未绑定时不能回退信任请求 userId")
        void consumeApiKeyNotBound() {
            // 设置 API Key 未绑定用户
            FbsApiKey apiKey = buildApiKey();
            apiKey.setUserId(null);  // 未绑定用户
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    apiKey, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            SkillApiConsumeRequest request = new SkillApiConsumeRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageConsume(request);

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }
        
        @Test
        @DisplayName("§12.6 usage/consume：请求 userId 与 API Key 绑定冲突时拒绝")
        void consumeRejectsUserIdMismatch() {
            // 设置 API Key 绑定用户
            FbsApiKey apiKey = buildApiKey();
            apiKey.setUserId(USER_ID);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    apiKey, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            SkillApiConsumeRequest request = new SkillApiConsumeRequest();
            request.setUserId(999L);  // 传了不同的 userId
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

            AjaxResult result = controller.usageConsume(request);

            assertEquals(403, result.get(AjaxResult.CODE_TAG));
            assertEquals("SKILL_API_KEY_USER_MISMATCH", result.get(AjaxResult.MSG_TAG));
            verifyNoInteractions(skillConsumeService);
            clearSecurityContext();
        }
    }

    // ========================================================================
    // Security closure: legacy points/earn is no longer a writable contract
    // ========================================================================

    @Nested
    @DisplayName("§14.1 points/earn 已关闭")
    class PointsEarnTests {

        @Test
        @DisplayName("§14.1.1 调用方自选身份、来源和金额的旧入口返回 410")
        void earnIsGoneWithoutCallingPointsService() throws Exception {
            MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

            mockMvc.perform(post("/fbs/skill-api/points/earn")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not-json"))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(410))
                    .andExpect(jsonPath("$.msg").value("SKILL_POINTS_EARN_DISABLED"));

            verifyNoInteractions(pointsService);
        }

        @Test
        @DisplayName("§14.1.2 未绑定 API Key 也不能回退信任请求 userId")
        void unboundApiKeyCannotUseRequestIdentity() {
            FbsApiKey apiKey = buildApiKey();
            apiKey.setUserId(null);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    apiKey, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_API")));
            SecurityContextHolder.getContext().setAuthentication(auth);

            SkillApiPointsEarnRequest request = new SkillApiPointsEarnRequest();
            request.setUserId(999L);
            request.setSource("first_install");
            request.setAmount(100);
            request.setUsageRecordId("FI-999");

            ResponseEntity<AjaxResult> response = controller.pointsEarn();

            assertEquals(HttpStatus.GONE, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(410, response.getBody().get(AjaxResult.CODE_TAG));
            assertEquals("SKILL_POINTS_EARN_DISABLED",
                    response.getBody().get(AjaxResult.MSG_TAG));
            verifyNoInteractions(pointsService);
            clearSecurityContext();
        }
    }

    // ========================================================================
    // OpenSpec #14：user/info null 占位测试
    // ========================================================================

    @Nested
    @DisplayName("§14.2 user/info activatedPacks null 占位")
    class UserInfoNullPlaceholderTests {

        @Test
        @DisplayName("§14.2.1 scenePackMapper.selectById 返回 null → packCode/packName/packStatus 为 null")
        void userInfoPackNull() {
            setupSecurityContext();
            when(pointsService.getUserPoints(USER_ID)).thenReturn(100);

            FbsUserPack userPack = new FbsUserPack();
            userPack.setId(1L);
            userPack.setPackId(999L);  // packId 存在但场景包被删除
            userPack.setStatus(1);
            when(userPackMapper.selectActiveByUserId(USER_ID)).thenReturn(Collections.singletonList(userPack));
            when(scenePackMapper.selectById(999L)).thenReturn(null);  // 场景包不存在

            SkillApiUserInfoRequest request = new SkillApiUserInfoRequest();
            request.setUserId(USER_ID);

            AjaxResult result = controller.userInfo(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> packs = (List<Map<String, Object>>) data.get("activatedPacks");
            assertEquals(1, packs.size());
            // null 占位：key 存在但值为 null
            assertEquals(999L, packs.get(0).get("packId"));
            assertNull(packs.get(0).get("packCode"));
            assertNull(packs.get(0).get("packName"));
            assertNull(packs.get(0).get("packStatus"));
            assertEquals(1, packs.get(0).get("status"));
            clearSecurityContext();
        }

        @Test
        @DisplayName("§14.2.2 scenePackMapper.selectById 正常返回 → packCode/packName/packStatus 有值")
        void userInfoPackNotNull() {
            setupSecurityContext();
            when(pointsService.getUserPoints(USER_ID)).thenReturn(100);

            FbsUserPack userPack = new FbsUserPack();
            userPack.setId(1L);
            userPack.setPackId(1L);
            userPack.setStatus(1);
            when(userPackMapper.selectActiveByUserId(USER_ID)).thenReturn(Collections.singletonList(userPack));
            when(scenePackMapper.selectById(1L)).thenReturn(buildScenePack());

            SkillApiUserInfoRequest request = new SkillApiUserInfoRequest();
            request.setUserId(USER_ID);

            AjaxResult result = controller.userInfo(request);

            assertEquals(200, result.get(AjaxResult.CODE_TAG));
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) result.get(AjaxResult.DATA_TAG);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> packs = (List<Map<String, Object>>) data.get("activatedPacks");
            assertEquals(1, packs.size());
            assertNotNull(packs.get(0).get("packCode"));
            assertNotNull(packs.get(0).get("packName"));
            assertNotNull(packs.get(0).get("packStatus"));
            clearSecurityContext();
        }
    }
}

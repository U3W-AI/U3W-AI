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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FbsSkillApiController 单元测试
 *
 * @author wxfbsir
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
    private static final String AUTH_CODE = "ABCD1234EFGH5678";

    // ---- Fixture ----
    private FbsApiKey buildApiKey() {
        FbsApiKey key = new FbsApiKey();
        key.setId(1L);
        key.setApiKey("fbs_testkey123");
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
        @DisplayName("§6.1.2.1.3 参数校验 — userId 为空")
        void rightsCheckMissingUserId() {
            setupSecurityContext();
            SkillApiCheckRequest request = new SkillApiCheckRequest();
            request.setUserId(null);
            request.setPackCode(PACK_CODE);

            AjaxResult result = controller.rightsCheck(request);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
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
                    eq(USAGE_RECORD_ID), eq("WORKBUDDY"), isNull(), isNull()))
                    .thenReturn(mockResult);

            SkillApiConsumeRequest request = new SkillApiConsumeRequest();
            request.setUserId(USER_ID);
            request.setPackCode(PACK_CODE);
            request.setSkillCode(SKILL_CODE);
            request.setUsageRecordId(USAGE_RECORD_ID);

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
        @DisplayName("§6.1.2.6.3 userId 为空 — 返回错误")
        void queryMissingUserId() {
            setupSecurityContext();
            SkillApiUserInfoRequest request = new SkillApiUserInfoRequest();
            request.setUserId(null);

            AjaxResult result = controller.userInfo(request);

            assertEquals(500, result.get(AjaxResult.CODE_TAG));
            clearSecurityContext();
        }
    }
}

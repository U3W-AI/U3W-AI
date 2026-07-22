package com.wx.fbsir.business.fbs.controller.skillapi;

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
import com.wx.fbsir.business.fbs.service.RightsCheckService;
import com.wx.fbsir.business.fbs.service.SkillConsumeService;
import com.wx.fbsir.business.point.service.IPointsService;
import com.wx.fbsir.common.core.domain.AjaxResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill API 网关控制器
 *
 * 面向 Skill 脚本的 REST API，使用 API Key 认证（而非 JWT）。
 * 所有路径前缀 /fbs/skill-api/，由 FbsApiKeyAuthFilter 校验 API Key。
 *
 * @author FBSir
 * @date 2026-04-11
 */
@RestController
@RequestMapping("/fbs/skill-api")
public class FbsSkillApiController {

    private static final String PACK_SCOPE_MISMATCH =
            "SKILL_API_KEY_PACK_SCOPE_MISMATCH";
    private static final String PACK_SCOPE_UNVERIFIABLE =
            "SKILL_API_KEY_PACK_SCOPE_UNVERIFIABLE";

    @Autowired
    private RightsCheckService rightsCheckService;

    @Autowired
    private SkillConsumeService skillConsumeService;

    @Autowired
    private FbsSkillUsageRecordMapper usageRecordMapper;

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Autowired
    private FbsUserPackMapper userPackMapper;

    @Autowired
    private IPointsService pointsService;

    // =====================================================================
    // 1. POST /fbs/skill-api/rights/check — 权益校验
    // =====================================================================

    @PostMapping("/rights/check")
    public AjaxResult rightsCheck(@RequestBody SkillApiCheckRequest request) {
        FbsApiKey apiKey = getCurrentApiKey();
        AjaxResult bindingError = validateBoundUser(apiKey, request.getUserId());
        if (bindingError != null) {
            return bindingError;
        }
        if (!StringUtils.hasText(request.getPackCode())) {
            return AjaxResult.error("参数不能为空");
        }
        AjaxResult packScopeError = validateRequestedPackScope(apiKey, request.getPackCode());
        if (packScopeError != null) {
            return packScopeError;
        }

        String hostType = StringUtils.hasText(request.getHostType()) ? request.getHostType() : "WORKBUDDY";
        ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                apiKey.getUserId(), request.getPackCode(), request.getAuthCode(),
                hostType, null);

        Map<String, Object> data = new HashMap<>();
        data.put("pass", result.isPass());
        data.put("failReason", result.getFailReason());
        data.put("packId", result.getPackId());
        data.put("pointsRuleCode", result.getPointsRuleCode());
        data.put("pointsAmount", result.getPointsAmount());

        return AjaxResult.success(data);
    }

    // =====================================================================
    // 2. POST /fbs/skill-api/usage/consume — 一次性消费
    // 
    // userId is derived from a bound API Key. A request userId is assertion-only.
    // =====================================================================

    @PostMapping("/usage/consume")
    public AjaxResult usageConsume(@RequestBody SkillApiConsumeRequest request) {
        FbsApiKey apiKey = getCurrentApiKey();
        AjaxResult bindingError = validateBoundUser(apiKey, request.getUserId());
        if (bindingError != null) {
            return bindingError;
        }
        Long userId = apiKey.getUserId();
        
        // 2. 校验其他参数
        if (!StringUtils.hasText(request.getPackCode())
                || !StringUtils.hasText(request.getUsageRecordId()) 
                || !StringUtils.hasText(request.getSkillCode())) {
            return AjaxResult.error("参数不能为空");
        }
        AjaxResult packScopeError = validateRequestedPackScope(apiKey, request.getPackCode());
        if (packScopeError != null) {
            return packScopeError;
        }

        String hostType = StringUtils.hasText(request.getHostType()) ? request.getHostType() : "WORKBUDDY";
        ConsumeResult result = skillConsumeService.consume(
                userId, request.getPackCode(), request.getSkillCode(),
                request.getUsageRecordId(), hostType,
                request.getHostSessionId(), request.getAuthCode());

        Map<String, Object> data = new HashMap<>();
        data.put("success", result.isSuccess());
        data.put("usageRecordId", result.getUsageRecordId());
        data.put("remainPoints", result.getRemainPoints());
        data.put("failReason", result.getFailReason());

        if (result.isSuccess()) {
            return AjaxResult.success(data);
        } else {
            return AjaxResult.error(result.getFailReason());
        }
    }

    // =====================================================================
    // 3. POST /fbs/skill-api/usage/start — 两阶段模式：开始
    // ⚠️ 与 consume 互斥：同一 usageRecordId 只能走一种模式
    // =====================================================================

    @PostMapping("/usage/start")
    public AjaxResult usageStart(@RequestBody SkillApiStartRequest request) {
        FbsApiKey apiKey = getCurrentApiKey();
        AjaxResult bindingError = validateBoundUser(apiKey, request.getUserId());
        if (bindingError != null) {
            return bindingError;
        }
        if (!StringUtils.hasText(request.getPackCode())
                || !StringUtils.hasText(request.getUsageRecordId()) || !StringUtils.hasText(request.getSkillCode())) {
            return AjaxResult.error("参数不能为空");
        }
        AjaxResult packScopeError = validateRequestedPackScope(apiKey, request.getPackCode());
        if (packScopeError != null) {
            return packScopeError;
        }

        String hostType = StringUtils.hasText(request.getHostType()) ? request.getHostType() : "WORKBUDDY";

        // 幂等语义：先查是否已存在
        FbsSkillUsageRecord existing = usageRecordMapper.selectByRecordId(request.getUsageRecordId());
        if (existing != null) {
            bindingError = validateBoundUser(apiKey, existing.getUserId());
            if (bindingError != null) {
                return bindingError;
            }
            FbsScenePack existingPack = scenePackMapper.selectByPackCode(request.getPackCode());
            if (existingPack == null) {
                return AjaxResult.error("场景包不存在: " + request.getPackCode());
            }
            if (!isSameUsageScope(existing, apiKey.getUserId(), existingPack.getId(),
                    request.getSkillCode(), hostType)) {
                return AjaxResult.error(409, "SKILL_USAGE_RECORD_SCOPE_MISMATCH");
            }
            if (existing.getStatus() != null && existing.getStatus() == UsageStatus.IN_PROGRESS.getCode()) {
                // 已存在且 status=0：返回已有记录（幂等）
                Map<String, Object> data = new HashMap<>();
                data.put("usageRecordId", existing.getUsageRecordId());
                data.put("status", existing.getStatus());
                return AjaxResult.success(data);
            }
            if (existing.getStatus() != null && existing.getStatus() == UsageStatus.SUCCESS.getCode()) {
                return AjaxResult.error(409, "使用记录已成功结束");
            }
            if (existing.getStatus() != null && existing.getStatus() == UsageStatus.FAILED.getCode()) {
                return AjaxResult.error(409, "使用记录已失败结束");
            }
        }

        // 查场景包（需要 packId 和 packVersion）
        FbsScenePack pack = scenePackMapper.selectByPackCode(request.getPackCode());
        if (pack == null) {
            return AjaxResult.error("场景包不存在: " + request.getPackCode());
        }

        // 创建使用记录（status=0，不扣减积分/配额）
        FbsSkillUsageRecord record = new FbsSkillUsageRecord();
        record.setUsageRecordId(request.getUsageRecordId());
        record.setUserId(apiKey.getUserId());
        record.setHostType(hostType);
        record.setHostSessionId(null);
        record.setSkillCode(request.getSkillCode());
        record.setPackId(pack.getId());
        record.setPackVersion(pack.getCurrentVersion());
        record.setPointsAmount(0); // start/end 模式不扣积分
        record.setStatus(UsageStatus.IN_PROGRESS.getCode());
        record.setStartTime(new Date());
        usageRecordMapper.insertUsageRecord(record);

        Map<String, Object> data = new HashMap<>();
        data.put("usageRecordId", record.getUsageRecordId());
        data.put("status", record.getStatus());
        return AjaxResult.success(data);
    }

    // =====================================================================
    // 4. PUT /fbs/skill-api/usage/end/{usageRecordId} — 两阶段模式：结束
    // ⚠️ 与 consume 互斥：end 只负责更新记录状态，不扣减积分/配额
    // =====================================================================

    @PutMapping("/usage/end/{usageRecordId}")
    public AjaxResult usageEnd(@PathVariable String usageRecordId,
                               @RequestBody SkillApiEndRequest request) {
        FbsApiKey apiKey = getCurrentApiKey();
        AjaxResult bindingError = validateBoundUser(apiKey, null);
        if (bindingError != null) {
            return bindingError;
        }
        if (request.getStatus() == null || (request.getStatus() != 1 && request.getStatus() != 2)) {
            return AjaxResult.error("status 必须为 1（成功）或 2（失败）");
        }
        AjaxResult packScopeError = validateApiKeyPackScope(apiKey);
        if (packScopeError != null) {
            return packScopeError;
        }

        // 查记录
        FbsSkillUsageRecord existing = usageRecordMapper.selectByRecordId(usageRecordId);
        if (existing == null) {
            return AjaxResult.error(404, "使用记录不存在");
        }
        bindingError = validateBoundUser(apiKey, existing.getUserId());
        if (bindingError != null) {
            return bindingError;
        }
        packScopeError = validateUsageRecordPackScope(apiKey, existing);
        if (packScopeError != null) {
            return packScopeError;
        }

        // 幂等语义
        if (existing.getStatus() != null && existing.getStatus() == UsageStatus.SUCCESS.getCode()) {
            return AjaxResult.error(409, "使用记录已成功结束");
        }
        if (existing.getStatus() != null && existing.getStatus() == UsageStatus.FAILED.getCode()) {
            return AjaxResult.error(409, "使用记录已失败结束");
        }

        // status=0 → 更新为 1 或 2（只允许一次状态转换）
        if (existing.getStatus() != null && existing.getStatus() == UsageStatus.IN_PROGRESS.getCode()) {
            int updated = usageRecordMapper.updateStatusByRecordId(
                    usageRecordId, request.getStatus(), request.getErrorMessage());
            if (updated != 1) {
                return AjaxResult.error(409, "SKILL_USAGE_RECORD_STATE_CONFLICT");
            }
            return AjaxResult.success("更新成功");
        }

        return AjaxResult.error("使用记录状态异常");
    }

    // =====================================================================
    // 5. POST /fbs/skill-api/scene-pack/query — 场景包规则查询
    // 返回 contentSnapshot（原始 JSON 字符串，不做二次结构化转换）
    // =====================================================================

    @PostMapping("/scene-pack/query")
    public AjaxResult scenePackQuery(@RequestBody SkillApiScenePackQueryRequest request) {
        if (!StringUtils.hasText(request.getPackCode())) {
            return AjaxResult.error("场景包编码不能为空");
        }
        FbsApiKey apiKey = getCurrentApiKey();
        AjaxResult packScopeError = validateRequestedPackScope(apiKey, request.getPackCode());
        if (packScopeError != null) {
            return packScopeError;
        }

        FbsScenePack pack = scenePackMapper.selectByPackCode(request.getPackCode());
        if (pack == null) {
            return AjaxResult.error("场景包不存在: " + request.getPackCode());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("packCode", pack.getPackCode());
        data.put("packName", pack.getPackName());
        data.put("currentVersion", pack.getCurrentVersion());
        data.put("status", pack.getStatus());
        data.put("pointsRuleCode", pack.getPointsRuleCode());
        data.put("contentSnapshot", pack.getContentSnapshot()); // 原始 JSON 字符串，不做结构化转换

        return AjaxResult.success(data);
    }

    // =====================================================================
    // 6. POST /fbs/skill-api/user/info — 用户信息查询
    // 不返回 T0-T3（当前仓库无此模型）
    // 
    // userId is derived from a bound API Key. A request userId is assertion-only.
    // =====================================================================

    @PostMapping("/user/info")
    public AjaxResult userInfo(@RequestBody SkillApiUserInfoRequest request) {
        FbsApiKey apiKey = getCurrentApiKey();
        AjaxResult bindingError = validateBoundUser(apiKey, request.getUserId());
        if (bindingError != null) {
            return bindingError;
        }
        Long userId = apiKey.getUserId();

        FbsScenePack scopedPack = null;
        AjaxResult storedScopeError = validateStoredPackScope(apiKey);
        if (storedScopeError != null) {
            return storedScopeError;
        }
        if (apiKey.getPackCode() != null) {
            FbsScenePack scopedIdentity = resolveExactPackIdentity(apiKey.getPackCode());
            if (scopedIdentity == null) {
                return packScopeUnverifiable();
            }
            scopedPack = scenePackMapper.selectById(scopedIdentity.getId());
            if (scopedPack == null
                    || !apiKey.getPackCode().equals(scopedPack.getPackCode())) {
                return packScopeUnverifiable();
            }
        }

        // 2. 积分余额
        Integer pointsBalance = pointsService.getUserPoints(userId);
        if (pointsBalance == null) {
            pointsBalance = 0;
        }

        // 3. 已激活场景包列表（status=1 且未过期）
        List<FbsUserPack> activePacks = userPackMapper.selectActiveByUserId(userId);
        List<Map<String, Object>> activatedPacks = new ArrayList<>();
        if (activePacks != null) {
            for (FbsUserPack up : activePacks) {
                if (scopedPack != null && !Objects.equals(up.getPackId(), scopedPack.getId())) {
                    continue;
                }
                // 查场景包编码和名称
                FbsScenePack pack = scopedPack != null
                        ? scopedPack
                        : scenePackMapper.selectById(up.getPackId());
                Map<String, Object> packInfo = new HashMap<>();
                packInfo.put("packId", up.getPackId());
                // null 占位：scenePackMapper.selectById() 返回 null 时补 null，避免 Skill 端 key 不存在
                packInfo.put("packCode", pack != null ? pack.getPackCode() : null);
                packInfo.put("packName", pack != null ? pack.getPackName() : null);
                packInfo.put("packStatus", pack != null ? pack.getStatus() : null);
                packInfo.put("status", up.getStatus());
                packInfo.put("expiresAt", up.getExpiresAt());
                activatedPacks.add(packInfo);
            }
        }

        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("pointsBalance", pointsBalance);
        data.put("activatedPacks", activatedPacks);

        return AjaxResult.success(data);
    }

    // =====================================================================
    // 7. POST /fbs/skill-api/points/earn — 行为积分上报
    //
    // The legacy contract trusted caller-selected identity, source, and amount.
    // It stays closed until a server-priced, user-bound, digest-idempotent ledger adapter exists.
    // =====================================================================

    @PostMapping("/points/earn")
    public ResponseEntity<AjaxResult> pointsEarn() {
        return ResponseEntity.status(HttpStatus.GONE)
                .body(AjaxResult.error(HttpStatus.GONE.value(),
                        "SKILL_POINTS_EARN_DISABLED"));
    }

    // =====================================================================
    // 辅助方法：从 SecurityContext 获取 API Key 实体
    // =====================================================================

    private FbsApiKey getCurrentApiKey() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof FbsApiKey) {
            return (FbsApiKey) auth.getPrincipal();
        }
        return null;
    }

    private AjaxResult validateBoundUser(FbsApiKey apiKey, Long requestedUserId) {
        if (apiKey == null || apiKey.getUserId() == null) {
            return AjaxResult.error(403, "SKILL_API_KEY_USER_BINDING_REQUIRED");
        }
        if (requestedUserId != null && !apiKey.getUserId().equals(requestedUserId)) {
            return AjaxResult.error(403, "SKILL_API_KEY_USER_MISMATCH");
        }
        return null;
    }

    /** Only a database NULL denotes a global key. Blank scope is invalid, never global. */
    private AjaxResult validateStoredPackScope(FbsApiKey apiKey) {
        if (apiKey == null) {
            return packScopeUnverifiable();
        }
        if (apiKey.getPackCode() != null && !StringUtils.hasText(apiKey.getPackCode())) {
            return packScopeUnverifiable();
        }
        return null;
    }

    private AjaxResult validateRequestedPackScope(FbsApiKey apiKey, String requestedPackCode) {
        AjaxResult valueError = validateRequestedPackScopeValue(apiKey, requestedPackCode);
        if (valueError != null) {
            return valueError;
        }
        return validateResolvedRequestedPackScope(apiKey, requestedPackCode);
    }

    private AjaxResult validateApiKeyPackScope(FbsApiKey apiKey) {
        AjaxResult storedScopeError = validateStoredPackScope(apiKey);
        if (storedScopeError != null || apiKey.getPackCode() == null) {
            return storedScopeError;
        }
        return validateResolvedRequestedPackScope(apiKey, apiKey.getPackCode());
    }

    private AjaxResult validateRequestedPackScopeValue(
            FbsApiKey apiKey, String requestedPackCode) {
        AjaxResult storedScopeError = validateStoredPackScope(apiKey);
        if (storedScopeError != null) {
            return storedScopeError;
        }
        if (apiKey.getPackCode() != null
                && !apiKey.getPackCode().equals(requestedPackCode)) {
            return AjaxResult.error(403, PACK_SCOPE_MISMATCH);
        }
        return null;
    }

    private AjaxResult validateResolvedRequestedPackScope(
            FbsApiKey apiKey, String requestedPackCode) {
        if (apiKey.getPackCode() != null) {
            FbsScenePack resolvedPack = resolveExactPackIdentity(requestedPackCode);
            if (resolvedPack == null
                    || !apiKey.getPackCode().equals(resolvedPack.getPackCode())) {
                return packScopeUnverifiable();
            }
        }
        return null;
    }

    private FbsScenePack resolveExactPackIdentity(String packCode) {
        List<FbsScenePack> identities = scenePackMapper.selectIdentitiesByPackCodeExact(packCode);
        if (identities == null || identities.size() != 1) {
            return null;
        }
        FbsScenePack identity = identities.get(0);
        if (identity == null || identity.getId() == null
                || !packCode.equals(identity.getPackCode())) {
            return null;
        }
        return identity;
    }

    private AjaxResult validateUsageRecordPackScope(
            FbsApiKey apiKey, FbsSkillUsageRecord record) {
        AjaxResult storedScopeError = validateStoredPackScope(apiKey);
        if (storedScopeError != null) {
            return storedScopeError;
        }
        if (record.getPackId() == null) {
            return packScopeUnverifiable();
        }
        FbsScenePack recordPack = scenePackMapper.selectById(record.getPackId());
        if (recordPack == null || !StringUtils.hasText(recordPack.getPackCode())) {
            return packScopeUnverifiable();
        }
        if (apiKey.getPackCode() != null
                && !apiKey.getPackCode().equals(recordPack.getPackCode())) {
            return AjaxResult.error(403, PACK_SCOPE_MISMATCH);
        }
        return null;
    }

    private AjaxResult packScopeUnverifiable() {
        return AjaxResult.error(403, PACK_SCOPE_UNVERIFIABLE);
    }

    private boolean isSameUsageScope(FbsSkillUsageRecord record, Long userId, Long packId,
                                     String skillCode, String hostType) {
        return Objects.equals(record.getUserId(), userId)
                && Objects.equals(record.getPackId(), packId)
                && Objects.equals(record.getSkillCode(), skillCode)
                && Objects.equals(normalizeHostType(record.getHostType()), normalizeHostType(hostType));
    }

    private String normalizeHostType(String hostType) {
        return StringUtils.hasText(hostType)
                ? hostType.trim().toUpperCase(Locale.ROOT)
                : "WORKBUDDY";
    }
}

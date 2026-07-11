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
        if (request.getUserId() == null || !StringUtils.hasText(request.getPackCode())) {
            return AjaxResult.error("参数不能为空");
        }

        String hostType = StringUtils.hasText(request.getHostType()) ? request.getHostType() : "WORKBUDDY";
        ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                request.getUserId(), request.getPackCode(), request.getAuthCode(),
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
    // 【OpenSpec #12】userId 参数改为可选：
    //   - 优先从 API Key 反查 userId
    //   - fallback 到 request.getUserId()（兼容旧调用）
    // =====================================================================

    @PostMapping("/usage/consume")
    public AjaxResult usageConsume(@RequestBody SkillApiConsumeRequest request) {
        // 1. 从 API Key 获取 userId（优先）
        FbsApiKey apiKey = getCurrentApiKey();
        Long userId = (apiKey != null && apiKey.getUserId() != null) 
                      ? apiKey.getUserId() 
                      : request.getUserId();
        
        if (userId == null) {
            return AjaxResult.error(403, "无法识别用户（API Key 未绑定且未传 userId）");
        }
        
        // 2. 校验其他参数
        if (!StringUtils.hasText(request.getPackCode())
                || !StringUtils.hasText(request.getUsageRecordId()) 
                || !StringUtils.hasText(request.getSkillCode())) {
            return AjaxResult.error("参数不能为空");
        }

        String hostType = StringUtils.hasText(request.getHostType()) ? request.getHostType() : "WORKBUDDY";
        // hostSessionId 传 null（Skill API 场景无宿主会话）
        ConsumeResult result = skillConsumeService.consume(
                userId, request.getPackCode(), request.getSkillCode(),
                request.getUsageRecordId(), hostType, null, request.getAuthCode());

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
        if (request.getUserId() == null || !StringUtils.hasText(request.getPackCode())
                || !StringUtils.hasText(request.getUsageRecordId()) || !StringUtils.hasText(request.getSkillCode())) {
            return AjaxResult.error("参数不能为空");
        }

        String hostType = StringUtils.hasText(request.getHostType()) ? request.getHostType() : "WORKBUDDY";

        // 幂等语义：先查是否已存在
        FbsSkillUsageRecord existing = usageRecordMapper.selectByRecordId(request.getUsageRecordId());
        if (existing != null) {
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
        record.setUserId(request.getUserId());
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
        if (request.getStatus() == null || (request.getStatus() != 1 && request.getStatus() != 2)) {
            return AjaxResult.error("status 必须为 1（成功）或 2（失败）");
        }

        // 查记录
        FbsSkillUsageRecord existing = usageRecordMapper.selectByRecordId(usageRecordId);
        if (existing == null) {
            return AjaxResult.error(404, "使用记录不存在");
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
            usageRecordMapper.updateStatusByRecordId(usageRecordId, request.getStatus(), request.getErrorMessage());
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
    // 【OpenSpec #12】userId 参数改为可选：
    //   - 优先从 API Key 反查 userId
    //   - fallback 到 request.getUserId()（兼容旧调用）
    // =====================================================================

    @PostMapping("/user/info")
    public AjaxResult userInfo(@RequestBody SkillApiUserInfoRequest request) {
        // 1. 从 API Key 获取 userId（优先）
        FbsApiKey apiKey = getCurrentApiKey();
        Long userId = (apiKey != null && apiKey.getUserId() != null) 
                      ? apiKey.getUserId() 
                      : request.getUserId();
        
        if (userId == null) {
            return AjaxResult.error(403, "无法识别用户（API Key 未绑定且未传 userId）");
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
                // 查场景包编码和名称
                FbsScenePack pack = scenePackMapper.selectById(up.getPackId());
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
    // Skill 端在检测到行为积分事件（首次安装、每日登录、完章等）后调用。
    // 幂等：eventId = usageRecordId，复用 wx_points_record.uk_event_id（#10 模型）
    // =====================================================================

    @PostMapping("/points/earn")
    public AjaxResult pointsEarn(@RequestBody SkillApiPointsEarnRequest request) {
        // 1. 从 API Key 获取 userId（优先）
        FbsApiKey apiKey = getCurrentApiKey();
        Long userId = (apiKey != null && apiKey.getUserId() != null)
                      ? apiKey.getUserId()
                      : request.getUserId();

        if (userId == null) {
            return AjaxResult.error(403, "无法识别用户（API Key 未绑定且未传 userId）");
        }

        // 2. 参数校验
        if (!StringUtils.hasText(request.getSource())) {
            return AjaxResult.error("source 不能为空");
        }
        if (request.getAmount() == null || request.getAmount() <= 0) {
            return AjaxResult.error("amount 必须为正整数");
        }
        if (!StringUtils.hasText(request.getUsageRecordId())) {
            return AjaxResult.error("usageRecordId 不能为空");
        }

        // 3. 调用 changePoints 重载3：eventId = usageRecordId（复用 #10 幂等模型）
        //    scenePackId = null（行为积分不属于场景包消费）
        AjaxResult result = pointsService.changePoints(
                userId, request.getSource(), request.getAmount(),
                null, request.getUsageRecordId(), request.getUsageRecordId());

        // 4. 构造返回
        if (result.get(AjaxResult.CODE_TAG) != null
                && (int) result.get(AjaxResult.CODE_TAG) == 200) {
            // 成功或幂等
            Integer remainPoints = pointsService.getUserPoints(userId);
            if (remainPoints == null) {
                remainPoints = 0;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("success", true);
            data.put("pointsAmount", request.getAmount());
            data.put("remainPoints", remainPoints);
            data.put("usageRecordId", request.getUsageRecordId());
            return AjaxResult.success(data);
        } else {
            // changePoints 返回错误（规则未配置、限频等）
            Map<String, Object> data = new HashMap<>();
            data.put("success", false);
            data.put("pointsAmount", 0);
            data.put("remainPoints", pointsService.getUserPoints(userId) != null ? pointsService.getUserPoints(userId) : 0);
            data.put("usageRecordId", request.getUsageRecordId());
            data.put("failReason", result.get(AjaxResult.MSG_TAG));
            return AjaxResult.error(String.valueOf(result.get(AjaxResult.MSG_TAG)));
        }
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
}

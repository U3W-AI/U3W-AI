package com.wx.fbsir.business.fbs.controller.internal;

import com.wx.fbsir.business.fbs.dto.ConsumeResult;
import com.wx.fbsir.business.fbs.service.SkillConsumeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Skill 消费统一入口（内部接口）
 * 对应 design.md §5.5
 *
 * 这是 MVP 跑通的核心入口：调用方只需一次调用，
 * 系统内部完成"综合校验 → 积分扣减 → 写使用记录"。
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@RestController
@RequestMapping("/fbs/internal/usage")
public class FbsSkillConsumeController {

    @Autowired
    private SkillConsumeService skillConsumeService;

    /**
     * POST /fbs/internal/usage/consume
     * Skill 消费统一入口（对应 design.md §4.4 SkillConsumeService.consume）
     *
     * 内部执行流程：
     *  1. 查 fbs_scene_pack
     *  2. 从 wx_points_rule 读取 amount（ruleCode=NULL 则免费包）
     *  3. comprehensiveCheck（全部 Fail-Closed）
     *  4. 幂等写入 fbs_skill_usage_record（status=0）
     *  5. 调用 IPointsService.changePoints 轻量重载（非免费包）
     *  6. 更新 fbs_skill_usage_record（status=1）
     *  7. 失败时更新 status=2，返回 failReason
     */
    @PostMapping("/consume")
    public com.wx.fbsir.common.core.domain.AjaxResult consume(@RequestBody ConsumeRequest req) {
        if (req.getUserId() == null) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("userId不能为空");
        }
        if (req.getPackCode() == null || req.getPackCode().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("packCode不能为空");
        }
        if (req.getUsageRecordId() == null || req.getUsageRecordId().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("usageRecordId不能为空");
        }
        if (req.getSkillCode() == null || req.getSkillCode().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("skillCode不能为空");
        }

        ConsumeResult result = skillConsumeService.consume(
                req.getUserId(),
                req.getPackCode(),
                req.getSkillCode(),
                req.getUsageRecordId(),
                req.getHostType(),
                req.getHostSessionId(),
                req.getAuthCode()
        );

        return result.isSuccess()
                ? com.wx.fbsir.common.core.domain.AjaxResult.success("消费成功", result)
                : com.wx.fbsir.common.core.domain.AjaxResult.error(result.getFailReason(), result);
    }

    // ====== Request DTO ======

    public static class ConsumeRequest {
        /** 用户ID */
        private Long userId;
        /** 场景包编码 */
        private String packCode;
        /** 技能编码 */
        private String skillCode;
        /** 使用记录幂等键（调用方生成，建议 UUID 或 taskId） */
        private String usageRecordId;
        /** 宿主类型：WORKBUDDY/STANDALONE/API */
        private String hostType;
        /** 宿主会话ID（可空） */
        private String hostSessionId;
        /** 授权码（可空） */
        private String authCode;

        public Long getUserId()       { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getPackCode()   { return packCode; }
        public void setPackCode(String v) { this.packCode = v; }
        public String getSkillCode()  { return skillCode; }
        public void setSkillCode(String v) { this.skillCode = v; }
        public String getUsageRecordId() { return usageRecordId; }
        public void setUsageRecordId(String v) { this.usageRecordId = v; }
        public String getHostType()  { return hostType; }
        public void setHostType(String v) { this.hostType = v; }
        public String getHostSessionId() { return hostSessionId; }
        public void setHostSessionId(String v) { this.hostSessionId = v; }
        public String getAuthCode()   { return authCode; }
        public void setAuthCode(String v) { this.authCode = v; }
    }
}

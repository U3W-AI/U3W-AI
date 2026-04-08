package com.wx.fbsir.business.fbs.controller.internal;

import com.wx.fbsir.business.fbs.dto.ComprehensiveRightsResult;
import com.wx.fbsir.business.fbs.service.RightsCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 权益校验内部接口
 * 对应 design.md §5.3
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@RestController
@RequestMapping("/fbs/internal/rights")
public class FbsRightsCheckController {

    @Autowired
    private RightsCheckService rightsCheckService;

    /**
     * POST /fbs/internal/rights/check
     * 综合权益校验（场景包 + 授权码 + 积分）
     * MVP 策略：全部 Fail-Closed
     */
    @PostMapping("/check")
    public com.wx.fbsir.common.core.domain.AjaxResult check(@RequestBody RightsCheckRequest req) {
        if (req.getUserId() == null) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("userId不能为空");
        }
        if (req.getPackCode() == null || req.getPackCode().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("packCode不能为空");
        }

        ComprehensiveRightsResult result = rightsCheckService.comprehensiveCheck(
                req.getUserId(),
                req.getPackCode(),
                req.getAuthCode(),
                req.getHostType(),
                req.getTaskId()
        );

        if (result.isPass()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.success("校验通过",
                    new RightsCheckResponse(true, null, result.getPackId(),
                            result.getPointsRuleCode(), result.getPointsAmount()));
        } else {
            return com.wx.fbsir.common.core.domain.AjaxResult.success("校验失败",
                    new RightsCheckResponse(false, result.getFailReason(), null, null, null));
        }
    }

    // ====== Request / Response DTOs ======

    public static class RightsCheckRequest {
        private Long userId;
        private String packCode;
        private String authCode;   // 可为空
        private String hostType;    // WORKBUDDY/STANDALONE/API
        private String taskId;      // 任务幂等键

        public Long getUserId()   { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getPackCode()   { return packCode; }
        public void setPackCode(String v) { this.packCode = v; }
        public String getAuthCode()  { return authCode; }
        public void setAuthCode(String v) { this.authCode = v; }
        public String getHostType()  { return hostType; }
        public void setHostType(String v) { this.hostType = v; }
        public String getTaskId()    { return taskId; }
        public void setTaskId(String v) { this.taskId = v; }
    }

    public static class RightsCheckResponse {
        private boolean pass;
        private String failReason;
        private Long packId;
        private String pointsRuleCode;
        private Integer pointsAmount;

        public RightsCheckResponse(boolean pass, String failReason, Long packId,
                                  String pointsRuleCode, Integer pointsAmount) {
            this.pass = pass;
            this.failReason = failReason;
            this.packId = packId;
            this.pointsRuleCode = pointsRuleCode;
            this.pointsAmount = pointsAmount;
        }

        public boolean isPass()           { return pass; }
        public String getFailReason()   { return failReason; }
        public Long getPackId()         { return packId; }
        public String getPointsRuleCode(){ return pointsRuleCode; }
        public Integer getPointsAmount() { return pointsAmount; }
    }
}

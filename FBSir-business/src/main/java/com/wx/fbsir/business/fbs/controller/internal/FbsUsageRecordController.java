package com.wx.fbsir.business.fbs.controller.internal;

import com.wx.fbsir.business.fbs.domain.entity.FbsSkillUsageRecord;
import com.wx.fbsir.business.fbs.domain.enums.UsageStatus;
import com.wx.fbsir.business.fbs.mapper.FbsSkillUsageRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * 使用记录内部接口
 * 对应 design.md §5.4
 *
 * @author FBSir
 * @date 2026-04-08
 */
@RestController
@RequestMapping("/fbs/internal/usage")
@PreAuthorize("@ss.hasRole('admin')")
public class FbsUsageRecordController {

    @Autowired
    private FbsSkillUsageRecordMapper usageRecordMapper;

    /**
     * POST /fbs/internal/usage/record
     * 写入使用记录（开始）
     */
    @PostMapping("/record")
    public com.wx.fbsir.common.core.domain.AjaxResult record(@RequestBody RecordUsageRequest req) {
        if (req.getUsageRecordId() == null || req.getUsageRecordId().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("usageRecordId不能为空");
        }
        if (req.getUserId() == null) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("userId不能为空");
        }
        if (req.getHostType() == null || req.getHostType().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("hostType不能为空");
        }
        if (req.getSkillCode() == null || req.getSkillCode().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("skillCode不能为空");
        }

        // 幂等：已存在则直接返回成功
        FbsSkillUsageRecord existing = usageRecordMapper.selectByRecordId(req.getUsageRecordId());
        if (existing != null) {
            return com.wx.fbsir.common.core.domain.AjaxResult.success("记录已存在",
                    new RecordUsageResponse(existing.getUsageRecordId(), existing.getStatus()));
        }

        FbsSkillUsageRecord record = new FbsSkillUsageRecord();
        record.setUsageRecordId(req.getUsageRecordId());
        record.setUserId(req.getUserId());
        record.setHostType(req.getHostType());
        record.setHostSessionId(req.getHostSessionId());
        record.setSkillCode(req.getSkillCode());
        record.setPackId(req.getPackId());
        record.setPackVersion(req.getPackVersion());
        record.setPointsAmount(req.getPointsAmount() != null ? req.getPointsAmount() : 0);
        record.setStatus(UsageStatus.IN_PROGRESS.getCode()); // 0=进行中
        record.setStartTime(req.getStartTime() != null ? req.getStartTime() : new Date());
        usageRecordMapper.insertUsageRecord(record);

        return com.wx.fbsir.common.core.domain.AjaxResult.success("记录成功",
                new RecordUsageResponse(record.getUsageRecordId(), record.getStatus()));
    }

    /**
     * PUT /fbs/internal/usage/record/{usageRecordId}/end
     * 结束使用记录（成功/失败）
     */
    @PutMapping("/record/{usageRecordId}/end")
    public com.wx.fbsir.common.core.domain.AjaxResult end(
            @PathVariable String usageRecordId,
            @RequestBody EndUsageRequest req) {

        if (req.getStatus() == null) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("status不能为空（1=成功, 2=失败）");
        }

        int updated = usageRecordMapper.updateStatusByRecordId(
                usageRecordId, req.getStatus(), req.getErrorMessage());
        if (updated == 0) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("使用记录不存在");
        }

        return com.wx.fbsir.common.core.domain.AjaxResult.success("更新成功");
    }

    // ====== Request / Response DTOs ======

    public static class RecordUsageRequest {
        private String usageRecordId;
        private Long userId;
        private String hostType;     // WORKBUDDY/STANDALONE/API
        private String hostSessionId;
        private String skillCode;
        private Long packId;        // 可空
        private String packVersion;  // 可空
        private Integer pointsAmount; // 可空，默认0
        private Date startTime;      // 可空，默认当前时间

        public String getUsageRecordId()  { return usageRecordId; }
        public void setUsageRecordId(String v) { this.usageRecordId = v; }
        public Long getUserId()    { return userId; }
        public void setUserId(Long v) { this.userId = v; }
        public String getHostType()  { return hostType; }
        public void setHostType(String v) { this.hostType = v; }
        public String getHostSessionId() { return hostSessionId; }
        public void setHostSessionId(String v) { this.hostSessionId = v; }
        public String getSkillCode()  { return skillCode; }
        public void setSkillCode(String v) { this.skillCode = v; }
        public Long getPackId()      { return packId; }
        public void setPackId(Long v)  { this.packId = v; }
        public String getPackVersion()  { return packVersion; }
        public void setPackVersion(String v) { this.packVersion = v; }
        public Integer getPointsAmount() { return pointsAmount; }
        public void setPointsAmount(Integer v) { this.pointsAmount = v; }
        public Date getStartTime()   { return startTime; }
        public void setStartTime(Date v) { this.startTime = v; }
    }

    public static class RecordUsageResponse {
        private String usageRecordId;
        private Integer status;

        public RecordUsageResponse(String usageRecordId, Integer status) {
            this.usageRecordId = usageRecordId;
            this.status = status;
        }

        public String getUsageRecordId() { return usageRecordId; }
        public Integer getStatus()      { return status; }
    }

    public static class EndUsageRequest {
        private Integer status;       // 1=成功, 2=失败
        private String errorMessage;  // 失败时填写

        public Integer getStatus()    { return status; }
        public void setStatus(Integer v) { this.status = v; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String v) { this.errorMessage = v; }
    }
}

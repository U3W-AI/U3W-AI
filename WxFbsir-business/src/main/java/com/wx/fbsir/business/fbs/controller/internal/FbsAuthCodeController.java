package com.wx.fbsir.business.fbs.controller.internal;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.enums.AuthCodeStatus;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.service.AuthCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.UUID;

/**
 * 授权码内部接口
 * 对应 design.md §5.2
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@RestController
@RequestMapping("/fbs/internal/auth-code")
@PreAuthorize("@ss.hasRole('admin')")
public class FbsAuthCodeController {

    @Autowired
    private FbsAuthCodeMapper authCodeMapper;

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Autowired
    private AuthCodeService authCodeService;

    /**
     * POST /fbs/internal/auth-code/generate
     * 生成授权码
     */
    @PostMapping("/generate")
    public com.wx.fbsir.common.core.domain.AjaxResult generate(@RequestBody GenerateAuthCodeRequest req) {
        if (req.getCodeType() == null || req.getIssuerType() == null || req.getIssuerId() == null) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("codeType/issuerType/issuerId不能为空");
        }
        if (req.getDeadline() != null && new Date().after(req.getDeadline())) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("截止时间不能早于当前时间");
        }

        // 校验场景包状态：已下架/不存在的场景包不能生成授权码
        String targetType = req.getTargetType() != null ? req.getTargetType() : "SCENE_PACK";
        if ("SCENE_PACK".equals(targetType) && req.getTargetId() != null) {
            FbsScenePack pack = scenePackMapper.selectById(req.getTargetId());
            if (pack == null) {
                return com.wx.fbsir.common.core.domain.AjaxResult.error("场景包不存在");
            }
            if (pack.getStatus() == null || pack.getStatus() != 1) {
                return com.wx.fbsir.common.core.domain.AjaxResult.error("场景包已下架，无法生成授权码");
            }
        }

        // 生成唯一授权码（UUID前16位+随机后4位，共20位）
        String authCodeStr = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                + String.format("%04d", (int)(Math.random() * 10000));

        FbsAuthCode code = new FbsAuthCode();
        code.setAuthCode(authCodeStr);
        code.setCodeType(req.getCodeType());
        code.setTargetType(req.getTargetType());
        code.setTargetId(req.getTargetId());
        code.setIssuerType(req.getIssuerType());
        code.setIssuerId(req.getIssuerId());
        code.setAvailable(1); // 默认启用
        code.setStatus(AuthCodeStatus.NOT_ACTIVATED.getCode()); // 0=未激活
        code.setDeadline(req.getDeadline());
        code.setMaxActivations(req.getMaxActivations() != null ? req.getMaxActivations() : 1);
        code.setActivatedCount(0);
        code.setDescription(req.getDescription());
        code.setDelFlag("0");

        authCodeMapper.insertAuthCode(code);

        return com.wx.fbsir.common.core.domain.AjaxResult.success("生成成功",
                new GenerateAuthCodeResponse(code.getId(), code.getAuthCode()));
    }

    /**
     * POST /fbs/internal/auth-code/activate
     * 激活授权码
     */
    @PostMapping("/activate")
    public com.wx.fbsir.common.core.domain.AjaxResult activate(@RequestBody ActivateAuthCodeRequest req) {
        if (req.getAuthCode() == null || req.getAuthCode().isEmpty()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("authCode不能为空");
        }
        if (req.getUserId() == null) {
            return com.wx.fbsir.common.core.domain.AjaxResult.error("userId不能为空");
        }

        AuthCodeService.ActivateResult result = authCodeService.activateAuthCode(req.getAuthCode(), req.getUserId());
        if (result.isSuccess()) {
            return com.wx.fbsir.common.core.domain.AjaxResult.success("激活成功",
                    new ActivateAuthCodeResponse(result.getUserPackId(), result.getPackId(), result.getPackCode()));
        } else {
            return com.wx.fbsir.common.core.domain.AjaxResult.error(result.getFailReason());
        }
    }

    // ====== Request / Response DTOs ======

    public static class GenerateAuthCodeRequest {
        private Integer codeType;       // 1=场景包权益码, 2=通用授权码
        private String targetType;       // SCENE_PACK/GENERIC
        private Long targetId;           // 关联目标ID
        private Integer issuerType;      // 1=平台, 2=企业, 3=用户
        private Long issuerId;
        private Date deadline;          // NULL=不限
        private Integer maxActivations; // 默认1
        private String description;

        public Integer getCodeType()         { return codeType; }
        public void setCodeType(Integer v) { this.codeType = v; }
        public String getTargetType()         { return targetType; }
        public void setTargetType(String v)  { this.targetType = v; }
        public Long getTargetId()           { return targetId; }
        public void setTargetId(Long v)     { this.targetId = v; }
        public Integer getIssuerType()        { return issuerType; }
        public void setIssuerType(Integer v) { this.issuerType = v; }
        public Long getIssuerId()           { return issuerId; }
        public void setIssuerId(Long v)     { this.issuerId = v; }
        public Date getDeadline()           { return deadline; }
        public void setDeadline(Date v)     { this.deadline = v; }
        public Integer getMaxActivations()   { return maxActivations; }
        public void setMaxActivations(Integer v){ this.maxActivations = v; }
        public String getDescription()       { return description; }
        public void setDescription(String v) { this.description = v; }
    }

    public static class GenerateAuthCodeResponse {
        private Long id;
        private String authCode;

        public GenerateAuthCodeResponse(Long id, String authCode) {
            this.id = id;
            this.authCode = authCode;
        }

        public Long getId()         { return id; }
        public String getAuthCode() { return authCode; }
    }

    public static class ActivateAuthCodeRequest {
        private String authCode;
        private Long userId;

        public String getAuthCode() { return authCode; }
        public void setAuthCode(String v) { this.authCode = v; }
        public Long getUserId()   { return userId; }
        public void setUserId(Long v) { this.userId = v; }
    }

    public static class ActivateAuthCodeResponse {
        private Long userPackId;
        private Long packId;
        private String packCode;

        public ActivateAuthCodeResponse(Long userPackId, Long packId, String packCode) {
            this.userPackId = userPackId;
            this.packId = packId;
            this.packCode = packCode;
        }

        public Long getUserPackId() { return userPackId; }
        public Long getPackId()    { return packId; }
        public String getPackCode() { return packCode; }
    }
}

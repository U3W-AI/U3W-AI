package com.wx.fbsir.business.fbs.service.business.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsAuthCode;
import com.wx.fbsir.business.fbs.domain.entity.FbsScenePack;
import com.wx.fbsir.business.fbs.domain.enums.AuthCodeStatus;
import com.wx.fbsir.business.fbs.dto.business.auth_code.AuthCodeGenerateRequest;
import com.wx.fbsir.business.fbs.dto.business.auth_code.AuthCodePageRequest;
import com.wx.fbsir.business.fbs.mapper.FbsAuthCodeMapper;
import com.wx.fbsir.business.fbs.mapper.FbsScenePackMapper;
import com.wx.fbsir.business.fbs.service.business.IFbsAuthCodeBusinessService;
import com.wx.fbsir.common.exception.ServiceException;
import com.wx.fbsir.common.utils.SecurityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 授权码运营BusinessService实现（薄封装：直接调用Mapper，状态更新由Service内UPDATE SQL完成）
 *
 * @author wxfbsir
 * @date 2026-04-08
 */
@Service
public class FbsAuthCodeBusinessServiceImpl implements IFbsAuthCodeBusinessService {

    @Autowired
    private FbsAuthCodeMapper authCodeMapper;

    @Autowired
    private FbsScenePackMapper scenePackMapper;

    @Override
    public List<FbsAuthCode> getAuthCodePage(AuthCodePageRequest request) {
        FbsAuthCode filter = new FbsAuthCode();
        if (request.getAuthCode() != null && !request.getAuthCode().isEmpty()) {
            filter.setAuthCode(request.getAuthCode());
        }
        if (request.getTargetId() != null) {
            filter.setTargetId(request.getTargetId());
        }
        if (request.getAvailable() != null) {
            filter.setAvailable(request.getAvailable());
        }
        if (request.getStatus() != null) {
            filter.setStatus(request.getStatus());
        }
        if (request.getIssuerType() != null) {
            filter.setIssuerType(request.getIssuerType());
        }
        return authCodeMapper.selectAuthCodeList(filter);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<Long, String> generateAuthCodeBatch(AuthCodeGenerateRequest request, String createdBy) {
        // 校验场景包状态：已下架/不存在的场景包不能生成授权码
        if (request.getTargetId() != null) {
            String targetType = request.getTargetType() != null ? request.getTargetType() : "SCENE_PACK";
            if ("SCENE_PACK".equals(targetType)) {
                FbsScenePack pack = scenePackMapper.selectById(request.getTargetId());
                if (pack == null) {
                    throw new ServiceException("场景包不存在", 400);
                }
                if (pack.getStatus() == null || pack.getStatus() != 1) {
                    throw new ServiceException("场景包已下架，无法生成授权码", 400);
                }
            }
        }

        int count = request.getCount() != null && request.getCount() > 0 ? request.getCount() : 1;
        Map<Long, String> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            // 生成唯一授权码（UUID前16位+随机后4位，共20位）
            String authCodeStr = UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                    + String.format("%04d", (int) (Math.random() * 10000));
            FbsAuthCode code = new FbsAuthCode();
            code.setAuthCode(authCodeStr);
            code.setCodeType(request.getTargetType() != null && request.getTargetType().equals("GENERIC") ? 2 : 1);
            code.setTargetType(request.getTargetType() != null ? request.getTargetType() : "SCENE_PACK");
            code.setTargetId(request.getTargetId());
            code.setIssuerType(request.getIssuerType() != null ? request.getIssuerType() : 1);
            // issuerId 为空时默认取当前登录用户 ID（平台运营 = 发放者）
            code.setIssuerId(request.getIssuerId() != null ? request.getIssuerId() : SecurityUtils.getUserId());
            code.setAvailable(1); // 默认启用
            code.setStatus(AuthCodeStatus.NOT_ACTIVATED.getCode()); // 0=未激活
            code.setDeadline(request.getDeadline());
            code.setMaxActivations(request.getMaxActivations() != null ? request.getMaxActivations() : 1);
            code.setActivatedCount(0);
            code.setDescription(request.getDescription());
            code.setDelFlag("0");
            code.setCreatedBy(createdBy);
            authCodeMapper.insertAuthCode(code);
            result.put(code.getId(), code.getAuthCode());
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disableAuthCode(Long id) {
        FbsAuthCode existing = authCodeMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        // Fail-Closed：仅 available=1（启用）可禁用
        if (existing.getAvailable() == null || existing.getAvailable() != 1) {
            return false;
        }
        return authCodeMapper.updateAvailable(id, 0) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enableAuthCode(Long id) {
        FbsAuthCode existing = authCodeMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        // Fail-Closed：仅 available=0 AND status IN(0,1) 可启用
        if (existing.getAvailable() == null || existing.getAvailable() != 0) {
            return false;
        }
        int status = existing.getStatus() != null ? existing.getStatus() : -1;
        if (status != 0 && status != 1) {
            // status IN (2,3,4) 均不可启用
            return false;
        }
        return authCodeMapper.updateAvailable(id, 1) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean revokeAuthCode(Long id) {
        FbsAuthCode existing = authCodeMapper.selectById(id);
        if (existing == null) {
            return false;
        }
        // Fail-Closed：已撤销不可重复撤销
        if (existing.getStatus() != null && existing.getStatus() == 4) {
            return false;
        }
        return authCodeMapper.updateStatus(id, 4) > 0;
    }
}

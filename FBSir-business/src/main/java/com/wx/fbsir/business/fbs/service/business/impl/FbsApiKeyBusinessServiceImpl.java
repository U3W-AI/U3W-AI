package com.wx.fbsir.business.fbs.service.business.impl;

import com.wx.fbsir.business.fbs.domain.entity.FbsApiKey;
import com.wx.fbsir.business.fbs.mapper.FbsApiKeyMapper;
import com.wx.fbsir.business.fbs.service.business.IFbsApiKeyBusinessService;
import com.wx.fbsir.common.exception.ServiceException;
import com.wx.fbsir.common.utils.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;

/**
 * API Key 运营管理 BusinessService 实现
 *
 * @author FBSir
 * @date 2026-04-11
 */
@Service
public class FbsApiKeyBusinessServiceImpl implements IFbsApiKeyBusinessService {

    private static final Logger log = LoggerFactory.getLogger(FbsApiKeyBusinessServiceImpl.class);

    private static final String KEY_PREFIX = "fbs_";

    @Autowired
    private FbsApiKeyMapper apiKeyMapper;

    @Override
    public FbsApiKey generateApiKey(String name, String packCode, Integer rateLimitPerMin, String remark) {
        // 生成 Key：fbs_ + Hex(SecureRandom(32B)) = 68 字符
        String apiKey = generateUniqueKey();

        FbsApiKey entity = new FbsApiKey();
        entity.setApiKey(apiKey);
        entity.setName(name);
        entity.setPackCode(packCode);
        entity.setRateLimitPerMin(rateLimitPerMin != null ? rateLimitPerMin : 60);
        entity.setStatus(1); // 默认启用
        entity.setRemark(remark);

        apiKeyMapper.insertApiKey(entity);

        log.info("API Key 生成成功 id={}, name={}, maskedKey={}", entity.getId(), name, entity.getMaskedApiKey());
        // ⚠️ 返回完整 Key（仅此一次），后续查询只返回脱敏值
        return entity;
    }

    @Override
    public void disableApiKey(Long id) {
        FbsApiKey entity = apiKeyMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("API Key 不存在");
        }
        entity.setStatus(0);
        apiKeyMapper.updateApiKey(entity);
        log.info("API Key 已禁用 id={}", id);
    }

    @Override
    public void enableApiKey(Long id) {
        FbsApiKey entity = apiKeyMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("API Key 不存在");
        }
        entity.setStatus(1);
        apiKeyMapper.updateApiKey(entity);
        log.info("API Key 已启用 id={}", id);
    }

    @Override
    public void deleteApiKey(Long id) {
        apiKeyMapper.deleteById(id);
        log.info("API Key 已删除 id={}", id);
    }

    @Override
    public List<FbsApiKey> listApiKeys(FbsApiKey filter) {
        List<FbsApiKey> list = apiKeyMapper.selectApiKeyList(filter);
        // 脱敏：列表查询不返回完整 Key
        for (FbsApiKey key : list) {
            key.setApiKey(key.getMaskedApiKey());
        }
        return list;
    }

    @Override
    public FbsApiKey getApiKeyById(Long id) {
        FbsApiKey entity = apiKeyMapper.selectById(id);
        if (entity != null) {
            entity.setApiKey(entity.getMaskedApiKey());
        }
        return entity;
    }

    /**
     * 生成唯一 API Key：fbs_ + 64位hex随机串
     * 32 字节 SecureRandom → hex 编码 = 64 字符，前缀 fbs_ 便于识别
     * 字符集 [0-9a-f]，与 SHA-256 hash 格式一致，不暴露编码规律
     */
    private String generateUniqueKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String encoded = bytesToHex(bytes);
        String candidate = KEY_PREFIX + encoded;

        // 确保唯一（极小概率冲突）
        FbsApiKey existing = apiKeyMapper.selectByApiKey(candidate);
        if (existing != null) {
            // 递归重试（理论上几乎不会执行）
            return generateUniqueKey();
        }
        return candidate;
    }

    /**
     * 字节数组转 hex 字符串（小写）
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ========== 用户侧接口实现 ==========

    @Override
    public FbsApiKey createByUser(String name) {
        Long userId = SecurityUtils.getUserId();
        String username = SecurityUtils.getUsername();

        // 生成 Key
        String apiKey = generateUniqueKey();

        FbsApiKey entity = new FbsApiKey();
        entity.setApiKey(apiKey);
        entity.setUserId(userId);  // 自动绑定当前用户
        entity.setName(name);
        entity.setStatus(1); // 默认启用
        entity.setRateLimitPerMin(60);
        entity.setCreatedBy(username);
        entity.setUpdatedBy(username);

        apiKeyMapper.insertApiKey(entity);

        log.info("用户创建 API Key 成功 userId={}, id={}, name={}, maskedKey={}",
                userId, entity.getId(), name, entity.getMaskedApiKey());
        // ⚠️ 返回完整 Key（仅此一次），后续查询只返回脱敏值
        return entity;
    }

    @Override
    public List<FbsApiKey> listMyKeys() {
        Long userId = SecurityUtils.getUserId();
        List<FbsApiKey> keys = apiKeyMapper.selectByUserId(userId);

        // 脱敏处理
        for (FbsApiKey key : keys) {
            key.setApiKey(key.getMaskedApiKey());
        }

        return keys;
    }

    @Override
    public void toggleStatus(Long id, Integer status) {
        Long userId = SecurityUtils.getUserId();
        String username = SecurityUtils.getUsername();

        FbsApiKey key = apiKeyMapper.selectById(id);

        if (key == null) {
            throw new ServiceException("API Key 不存在", 404);
        }

        if (!userId.equals(key.getUserId())) {
            throw new ServiceException("无权操作此 API Key", 403);
        }

        key.setStatus(status);
        key.setUpdatedBy(username);
        apiKeyMapper.updateApiKey(key);

        log.info("API Key 状态切换 userId={}, id={}, status={}", userId, id, status);
    }

    @Override
    public void deleteById(Long id) {
        Long userId = SecurityUtils.getUserId();

        FbsApiKey key = apiKeyMapper.selectById(id);

        if (key == null) {
            throw new ServiceException("API Key 不存在", 404);
        }

        if (!userId.equals(key.getUserId())) {
            throw new ServiceException("无权删除此 API Key", 403);
        }

        apiKeyMapper.deleteById(id);

        log.info("API Key 删除成功 userId={}, id={}", userId, id);
    }
}

package com.wx.fbsir.business.dailyassistant.service.impl;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.wx.fbsir.business.dailyassistant.domain.YuanqiAgentConfig;
import com.wx.fbsir.business.dailyassistant.mapper.YuanqiAgentConfigMapper;
import com.wx.fbsir.business.dailyassistant.service.IYuanqiAgentConfigService;
import com.wx.fbsir.common.utils.AesEncryptUtils;

/**
 * 腾讯元器智能体配置Service业务层处理
 *
 * @author FBSir
 * @date 2025-12-05
 */
@Service
public class YuanqiAgentConfigServiceImpl implements IYuanqiAgentConfigService
{
    private static final Logger log = LoggerFactory.getLogger(YuanqiAgentConfigServiceImpl.class);
    
    @Autowired
    private YuanqiAgentConfigMapper yuanqiAgentConfigMapper;
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // 脱敏标记，表示字段已加密存储
    private static final String MASKED_VALUE = "***已加密***";
    
    public YuanqiAgentConfigServiceImpl() {
        // 配置RestTemplate用于验证
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 连接超时：10秒
        factory.setReadTimeout(15000);     // 读取超时：15秒
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 查询腾讯元器智能体配置
     *
     * @param id 腾讯元器智能体配置ID
     * @return 腾讯元器智能体配置
     */
    @Override
    public YuanqiAgentConfig selectYuanqiAgentConfigById(Long id)
    {
        return yuanqiAgentConfigMapper.selectYuanqiAgentConfigById(id);
    }

    /**
     * 查询腾讯元器智能体配置列表
     *
     * @param yuanqiAgentConfig 腾讯元器智能体配置
     * @return 腾讯元器智能体配置
     */
    @Override
    public List<YuanqiAgentConfig> selectYuanqiAgentConfigList(YuanqiAgentConfig yuanqiAgentConfig)
    {
        return yuanqiAgentConfigMapper.selectYuanqiAgentConfigList(yuanqiAgentConfig);
    }

    /**
     * 根据用户ID和业务类型查询启用的配置（返回脱敏数据）
     *
     * @param userId 用户ID
     * @param businessType 业务类型
     * @return 腾讯元器智能体配置（敏感字段已脱敏）
     */
    @Override
    public YuanqiAgentConfig selectActiveConfigByUserId(Long userId, String businessType)
    {
        YuanqiAgentConfig config = yuanqiAgentConfigMapper.selectActiveConfigByUserId(userId, businessType);
        if (config != null) {
            // 脱敏处理：不返回真实的agentId和apiKey
            if (StringUtils.hasText(config.getAgentId())) {
                config.setAgentId(MASKED_VALUE);
            }
            if (StringUtils.hasText(config.getApiKey())) {
                config.setApiKey(MASKED_VALUE);
            }
        }
        return config;
    }

    /**
     * 根据用户ID和业务类型查询启用的配置（返回解密数据，仅供内部业务逻辑使用）
     * 注意：此方法返回真实的敏感信息，不应暴露给前端
     *
     * @param userId 用户ID
     * @param businessType 业务类型
     * @return 腾讯元器智能体配置（敏感字段已解密）
     */
    @Override
    public YuanqiAgentConfig selectActiveConfigByUserIdDecrypted(Long userId, String businessType)
    {
        YuanqiAgentConfig config = yuanqiAgentConfigMapper.selectActiveConfigByUserId(userId, businessType);
        if (config != null) {
            // 解密敏感字段
            return decryptSensitiveFields(config);
        }
        return null;
    }

    /**
     * 新增腾讯元器智能体配置（加密敏感信息）
     *
     * @param yuanqiAgentConfig 腾讯元器智能体配置
     * @return 结果
     */
    @Override
    public int insertYuanqiAgentConfig(YuanqiAgentConfig yuanqiAgentConfig)
    {
        // 加密敏感信息
        encryptSensitiveFields(yuanqiAgentConfig);
        return yuanqiAgentConfigMapper.insertYuanqiAgentConfig(yuanqiAgentConfig);
    }

    /**
     * 修改腾讯元器智能体配置（加密敏感信息）
     *
     * @param yuanqiAgentConfig 腾讯元器智能体配置
     * @return 结果
     */
    @Override
    public int updateYuanqiAgentConfig(YuanqiAgentConfig yuanqiAgentConfig)
    {
        // 如果前端传入的是脱敏标记，说明用户没有修改该字段，需要保留原值
        if (MASKED_VALUE.equals(yuanqiAgentConfig.getAgentId()) || 
            MASKED_VALUE.equals(yuanqiAgentConfig.getApiKey())) {
            // 查询原有配置
            YuanqiAgentConfig oldConfig = yuanqiAgentConfigMapper.selectYuanqiAgentConfigById(yuanqiAgentConfig.getId());
            if (oldConfig != null) {
                // 保留原有的加密值
                if (MASKED_VALUE.equals(yuanqiAgentConfig.getAgentId())) {
                    yuanqiAgentConfig.setAgentId(oldConfig.getAgentId());
                }
                if (MASKED_VALUE.equals(yuanqiAgentConfig.getApiKey())) {
                    yuanqiAgentConfig.setApiKey(oldConfig.getApiKey());
                }
            }
        } else {
            // 用户修改了敏感字段，需要加密新值
            encryptSensitiveFields(yuanqiAgentConfig);
        }
        return yuanqiAgentConfigMapper.updateYuanqiAgentConfig(yuanqiAgentConfig);
    }

    /**
     * 批量删除腾讯元器智能体配置
     *
     * @param ids 需要删除的腾讯元器智能体配置ID
     * @return 结果
     */
    @Override
    public int deleteYuanqiAgentConfigByIds(Long[] ids)
    {
        return yuanqiAgentConfigMapper.deleteYuanqiAgentConfigByIds(ids);
    }

    /**
     * 删除腾讯元器智能体配置信息
     *
     * @param id 腾讯元器智能体配置ID
     * @return 结果
     */
    @Override
    public int deleteYuanqiAgentConfigById(Long id)
    {
        return yuanqiAgentConfigMapper.deleteYuanqiAgentConfigById(id);
    }
    
    /**
     * 加密敏感字段（智能体ID和API密钥）
     *
     * @param config 配置对象
     */
    private void encryptSensitiveFields(YuanqiAgentConfig config)
    {
        try {
            // 加密智能体ID
            if (StringUtils.hasText(config.getAgentId()) && !MASKED_VALUE.equals(config.getAgentId())) {
                String encryptedAgentId = AesEncryptUtils.encrypt(config.getAgentId());
                config.setAgentId(encryptedAgentId);
            }
            
            // 加密API密钥
            if (StringUtils.hasText(config.getApiKey()) && !MASKED_VALUE.equals(config.getApiKey())) {
                String encryptedApiKey = AesEncryptUtils.encrypt(config.getApiKey());
                config.setApiKey(encryptedApiKey);
            }
        } catch (Exception e) {
            log.error("[智能体配置] 加密失败");
            throw new RuntimeException("配置加密失败，请检查输入数据");
        }
    }
    
    /**
     * 解密敏感字段（供内部调用使用）
     * 注意：此方法仅供业务逻辑内部使用，不应暴露给前端
     *
     * @param config 配置对象
     * @return 解密后的配置对象
     */
    public YuanqiAgentConfig decryptSensitiveFields(YuanqiAgentConfig config)
    {
        if (config == null) {
            return null;
        }
        
        try {
            // 解密智能体ID
            if (StringUtils.hasText(config.getAgentId())) {
                String decryptedAgentId = AesEncryptUtils.decrypt(config.getAgentId());
                config.setAgentId(decryptedAgentId);
            }
            
            // 解密API密钥
            if (StringUtils.hasText(config.getApiKey())) {
                String decryptedApiKey = AesEncryptUtils.decrypt(config.getApiKey());
                config.setApiKey(decryptedApiKey);
            }
            
            return config;
        } catch (Exception e) {
            log.error("[智能体配置] 解密失败");
            throw new RuntimeException("配置解密失败，可能是历史数据问题，请重新配置");
        }
    }
    
    /**
     * 验证智能体配置是否可用
     * 通过调用腾讯元器API测试连通性，使用最简短的测试问题以节省token
     *
     * @param agentId 智能体ID
     * @param apiKey API密钥
     * @param apiEndpoint API端点
     * @return 验证结果消息
     * @throws RuntimeException 验证失败时抛出异常，包含友好的错误信息
     */
    @Override
    public String verifyAgentConfig(String agentId, String apiKey, String apiEndpoint) {
        if (!StringUtils.hasText(agentId)) {
            throw new RuntimeException("智能体ID不能为空");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new RuntimeException("API密钥不能为空");
        }
        if (!StringUtils.hasText(apiEndpoint)) {
            apiEndpoint = "https://yuanqi.tencent.com/openapi/v1/agent/chat/completions";
        }
        
        try {
            // 构建请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey.trim());
            
            // 构建请求体 - 使用最简短的测试消息以节省token（约3个token）
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("assistant_id", agentId.trim());
            requestBody.put("user_id", "test_user");
            requestBody.put("stream", false);  // 非流式，快速返回
            
            // 使用最简短的测试问题（仅2个汉字，约3个token）
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            
            Map<String, String> content = new HashMap<>();
            content.put("type", "text");
            content.put("text", "你好");  // 最简短的测试问题，仅消耗约3个token
            
            message.put("content", new Object[]{content});
            requestBody.put("messages", new Object[]{message});
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            log.info("[智能体验证] 开始验证配置 - AgentId: {}", agentId);
            
            // 发送请求
            ResponseEntity<String> response = restTemplate.exchange(
                apiEndpoint,
                HttpMethod.POST,
                requestEntity,
                String.class
            );
            
            // 解析响应
            String responseBody = response.getBody();
            if (responseBody != null && !responseBody.isEmpty()) {
                // 检查是否有错误
                JsonNode jsonNode = objectMapper.readTree(responseBody);
                if (jsonNode.has("error")) {
                    JsonNode errorNode = jsonNode.get("error");
                    String errorCode = errorNode.has("code") ? errorNode.get("code").asText() : "未知";
                    String errorMsg = errorNode.has("message") ? errorNode.get("message").asText() : "未知错误";
                    
                    log.error("[智能体验证] API返回错误 - Code: {}, Message: {}", errorCode, errorMsg);
                    throw new RuntimeException("验证失败：" + errorMsg);
                }
                
                // 验证成功
                log.info("[智能体验证] 验证成功 - AgentId: {}", agentId);
                return "验证成功，智能体配置可用";
            }
            
            throw new RuntimeException("API响应为空");
            
        } catch (HttpClientErrorException e) {
            // 4xx错误 - 直接根据HTTP状态码返回友好提示
            String errorBody = e.getResponseBodyAsString();
            int statusCode = e.getStatusCode().value();
            log.error("[智能体验证] 客户端错误 - Status: {}, Body: {}", statusCode, errorBody);
            
            // 根据HTTP状态码返回友好提示
            switch (statusCode) {
                case 400:
                    throw new RuntimeException("智能体ID格式错误，请检查ID是否正确");
                case 401:
                    throw new RuntimeException("API密钥验证失败，请检查密钥是否正确");
                case 403:
                    throw new RuntimeException("访问被拒绝，请检查智能体权限配置");
                case 404:
                    throw new RuntimeException("智能体不存在，请检查ID是否正确");
                default:
                    // 尝试解析响应体中的错误信息
                    try {
                        JsonNode errorJson = objectMapper.readTree(errorBody);
                        if (errorJson.has("error")) {
                            JsonNode errorNode = errorJson.get("error");
                            String errorMsg = errorNode.has("message") ? errorNode.get("message").asText() : "";
                            if (!errorMsg.isEmpty()) {
                                throw new RuntimeException(errorMsg);
                            }
                        }
                    } catch (RuntimeException re) {
                        throw re;
                    } catch (Exception ex) {
                        // JSON解析失败，使用默认提示
                    }
                    throw new RuntimeException("配置验证失败，请检查智能体ID和API密钥");
            }
            
        } catch (HttpServerErrorException e) {
            // 5xx错误
            log.error("[智能体验证] 服务器错误 - Status: {}", e.getStatusCode());
            throw new RuntimeException("腾讯元器服务暂时不可用，请稍后重试");
            
        } catch (RuntimeException e) {
            // 已经是友好的错误信息，直接抛出
            throw e;
            
        } catch (Exception e) {
            log.error("[智能体验证] 验证失败", e);
            throw new RuntimeException("验证失败：" + e.getMessage());
        }
    }
}

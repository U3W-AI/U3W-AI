package com.wx.fbsir.business.officialaccount.service.impl;

import java.util.List;
import java.util.Map;
import com.wx.fbsir.common.utils.DateUtils;
import com.wx.fbsir.common.utils.AesEncryptUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wx.fbsir.business.officialaccount.mapper.WcOfficeAccountMapper;
import com.wx.fbsir.business.officialaccount.domain.WcOfficeAccount;
import com.wx.fbsir.business.officialaccount.service.IWcOfficeAccountService;
import com.wx.fbsir.business.officialaccount.service.WechatOfficialApiService;

/**
 * 微信公众号配置Service业务层处理
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@Service
public class WcOfficeAccountServiceImpl implements IWcOfficeAccountService 
{
    private static final Logger log = LoggerFactory.getLogger(WcOfficeAccountServiceImpl.class);
    
    @Autowired
    private WcOfficeAccountMapper wcOfficeAccountMapper;
    
    @Autowired
    private WechatOfficialApiService wechatOfficialApiService;

    /**
     * 查询微信公众号配置
     * 
     * @param id 微信公众号配置主键
     * @return 微信公众号配置
     */
    @Override
    public WcOfficeAccount selectWcOfficeAccountById(Long id)
    {
        return wcOfficeAccountMapper.selectWcOfficeAccountById(id);
    }

    /**
     * 根据用户ID查询微信公众号配置
     * 
     * @param userId 用户ID
     * @return 微信公众号配置
     */
    @Override
    public WcOfficeAccount selectWcOfficeAccountByUserId(Long userId)
    {
        return wcOfficeAccountMapper.selectWcOfficeAccountByUserId(userId);
    }

    /**
     * 查询微信公众号配置列表
     * 
     * @param wcOfficeAccount 微信公众号配置
     * @return 微信公众号配置
     */
    @Override
    public List<WcOfficeAccount> selectWcOfficeAccountList(WcOfficeAccount wcOfficeAccount)
    {
        return wcOfficeAccountMapper.selectWcOfficeAccountList(wcOfficeAccount);
    }

    /**
     * 新增或更新微信公众号配置
     * 该方法会根据用户ID判断是新增还是更新
     * 在保存前会验证公众号配置是否正确，并上传封面图到微信素材库
     * 只有验证通过后才会保存到数据库
     * 对敏感信息进行加密存储
     * 
     * @param wcOfficeAccount 微信公众号配置
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveOrUpdateWcOfficeAccount(WcOfficeAccount wcOfficeAccount)
    {
        wcOfficeAccount.setCreateTime(DateUtils.getNowDate());
        wcOfficeAccount.setUpdateTime(DateUtils.getNowDate());
        
        // 查询用户是否已有配置
        WcOfficeAccount existAccount = wcOfficeAccountMapper.selectWcOfficeAccountByUserId(wcOfficeAccount.getUserId());
        boolean isUpdate = (existAccount != null);
        
        // 保存明文副本用于验证
        String plainAppId = wcOfficeAccount.getAppId();
        String plainAppSecret = wcOfficeAccount.getAppSecret();
        String picUrl = wcOfficeAccount.getPicUrl();
        
        // 验证封面图URL必填（首次配置或重新配置密钥时）
        boolean needVerifyCredentials = (plainAppId != null && !plainAppId.trim().isEmpty()) && 
                                       (plainAppSecret != null && !plainAppSecret.trim().isEmpty());
        
        if (!isUpdate && (picUrl == null || picUrl.trim().isEmpty())) {
            throw new RuntimeException("首次配置时，素材封面图为必填项。封面图用于文章封面，符合微信API要求");
        }
        
        if (needVerifyCredentials && (picUrl == null || picUrl.trim().isEmpty())) {
            throw new RuntimeException("配置或更新密钥时，必须提供素材封面图URL。封面图将上传到微信素材库并用于文章封面");
        }
        
        // 如果是更新操作，且前端未提供新的appId/appSecret（为空或null），则使用原有的
        if (isUpdate) {
            if (plainAppId == null || plainAppId.trim().isEmpty()) {
                // 前端未提供新的appId，使用原有的（已加密）
                wcOfficeAccount.setAppId(existAccount.getAppId());
                plainAppId = null;  // 标记不需要重新验证和加密
                log.debug("更新配置时未提供新的AppID，保留原有配置");
            }
            
            if (plainAppSecret == null || plainAppSecret.trim().isEmpty()) {
                // 前端未提供新的appSecret，使用原有的（已加密）
                wcOfficeAccount.setAppSecret(existAccount.getAppSecret());
                plainAppSecret = null;  // 标记不需要重新验证和加密
                log.debug("更新配置时未提供新的AppSecret，保留原有配置");
            }
        }
        
        // ========== 验证公众号配置并上传封面图 ==========
        // 只有在提供了新的 appId 和 appSecret 时才需要验证
        boolean needVerify = (plainAppId != null && !plainAppId.trim().isEmpty()) && 
                            (plainAppSecret != null && !plainAppSecret.trim().isEmpty());
        
        if (needVerify) {
            try {
                // 验证appId、appSecret是否正确，并上传封面图到素材库
                Map<String, String> uploadResult = wechatOfficialApiService.verifyAndUploadCover(
                        plainAppId, plainAppSecret, picUrl);
                
                String mediaId = uploadResult.get("media_id");
                String materialUrl = uploadResult.get("url");
                
                // 设置素材ID
                wcOfficeAccount.setMediaId(mediaId);
                
                log.info("公众号配置验证成功 - userId: {}, mediaId: {}", 
                        wcOfficeAccount.getUserId(), mediaId);
                
            } catch (Exception e) {
                log.error("公众号配置验证失败 - userId: {}, error: {}", wcOfficeAccount.getUserId(), e.getMessage());
                throw new RuntimeException("公众号配置验证失败：" + e.getMessage());
            }
            
            // ========== 加密新的敏感信息 ==========
            try {
                if (plainAppId != null && !plainAppId.isEmpty()) {
                    wcOfficeAccount.setAppId(AesEncryptUtils.encrypt(plainAppId));
                    log.debug("新AppID加密成功");
                }
                if (plainAppSecret != null && !plainAppSecret.isEmpty()) {
                    wcOfficeAccount.setAppSecret(AesEncryptUtils.encrypt(plainAppSecret));
                    log.debug("新AppSecret加密成功");
                }
            } catch (Exception e) {
                log.error("加密公众号配置信息失败: {}", e.getMessage());
                throw new RuntimeException("加密失败，请检查AES密钥配置：" + e.getMessage());
            }
        } else {
            log.info("未提供新的密钥配置，跳过验证，保留原有密钥 - userId: {}", wcOfficeAccount.getUserId());
        }
        
        // ========== 保存到数据库 ==========
        int result;
        if (isUpdate) {
            // 更新现有配置
            wcOfficeAccount.setId(existAccount.getId());
            
            // 如果前端未传递isActive（用户不能自己禁用），则保持原值
            if (wcOfficeAccount.getIsActive() == null) {
                wcOfficeAccount.setIsActive(existAccount.getIsActive());
            }
            
            // 设置更新者为用户ID（字符串形式）
            wcOfficeAccount.setUpdateBy(String.valueOf(wcOfficeAccount.getUserId()));
            
            result = wcOfficeAccountMapper.updateWcOfficeAccount(wcOfficeAccount);
            log.info("公众号配置已更新 - userId: {}", wcOfficeAccount.getUserId());
        } else {
            // 新增配置（必须提供appId和appSecret）
            if (plainAppId == null || plainAppSecret == null) {
                throw new RuntimeException("首次配置时必须提供开发者ID和密钥");
            }
            
            // 新增时默认启用（用户配置时默认为启用状态）
            if (wcOfficeAccount.getIsActive() == null) {
                wcOfficeAccount.setIsActive(1);
            }
            
            // 设置创建者为用户ID（字符串形式）
            wcOfficeAccount.setCreateBy(String.valueOf(wcOfficeAccount.getUserId()));
            
            result = wcOfficeAccountMapper.insertWcOfficeAccount(wcOfficeAccount);
            log.info("公众号配置已保存 - userId: {}", wcOfficeAccount.getUserId());
        }
        
        return result;
    }

    /**
     * 修改微信公众号配置
     * 
     * @param wcOfficeAccount 微信公众号配置
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateWcOfficeAccount(WcOfficeAccount wcOfficeAccount)
    {
        wcOfficeAccount.setUpdateTime(DateUtils.getNowDate());
        
        // 如果提供了新的appId或appSecret，则使用AES加密
        try {
            if (wcOfficeAccount.getAppId() != null && !wcOfficeAccount.getAppId().isEmpty()) {
                wcOfficeAccount.setAppId(AesEncryptUtils.encrypt(wcOfficeAccount.getAppId()));
                log.debug("AppID加密成功");
            }
            if (wcOfficeAccount.getAppSecret() != null && !wcOfficeAccount.getAppSecret().isEmpty()) {
                wcOfficeAccount.setAppSecret(AesEncryptUtils.encrypt(wcOfficeAccount.getAppSecret()));
                log.debug("AppSecret加密成功");
            }
        } catch (Exception e) {
            log.error("加密公众号配置信息失败: {}", e.getMessage());
            throw new RuntimeException("加密失败，请检查AES密钥配置：" + e.getMessage());
        }
        
        return wcOfficeAccountMapper.updateWcOfficeAccount(wcOfficeAccount);
    }

    /**
     * 批量删除微信公众号配置
     * 
     * @param ids 需要删除的微信公众号配置主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWcOfficeAccountByIds(Long[] ids)
    {
        return wcOfficeAccountMapper.deleteWcOfficeAccountByIds(ids);
    }

    /**
     * 删除微信公众号配置信息
     * 
     * @param id 微信公众号配置主键
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteWcOfficeAccountById(Long id)
    {
        return wcOfficeAccountMapper.deleteWcOfficeAccountById(id);
    }
}

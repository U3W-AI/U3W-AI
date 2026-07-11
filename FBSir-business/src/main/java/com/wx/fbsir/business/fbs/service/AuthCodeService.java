package com.wx.fbsir.business.fbs.service;

import java.util.Date;

/**
 * 授权码激活服务接口
 *
 * @author FBSir
 * @date 2026-04-08
 */
public interface AuthCodeService {

    /**
     * 激活授权码，为用户创建场景包权益
     *
     * 激活逻辑：
     *  1. checkAuthCode 预校验（status IN(0,1) 均可通过）
     *  2. FOR UPDATE 行锁查询，防并发重复激活
     *  3. activated_count + 1
     *  4. 若激活前 status=0，更新 status=1（已激活）
     *  5. 若激活后 activated_count >= max_activations，更新 status=2（已用尽）
     *  6. 创建 fbs_user_pack 记录（source_type=3，user_id 关联）
     *
     * @param authCode 授权码字符串
     * @param userId   激活用户ID
     * @return 激活结果
     */
    ActivateResult activateAuthCode(String authCode, Long userId);

    /**
     * 授权码激活结果
     */
    class ActivateResult {
        private boolean success;
        private Long    userPackId;
        private Long    packId;
        private String  packCode;
        /** 权益过期时间（来自 fbs_user_pack.expiresAt 快照，null=永不过期） */
        private Date    expiresAt;
        private String  failReason;

        private ActivateResult() {}

        public static ActivateResult success(Long userPackId, Long packId, String packCode, Date expiresAt) {
            ActivateResult r = new ActivateResult();
            r.success    = true;
            r.userPackId = userPackId;
            r.packId     = packId;
            r.packCode   = packCode;
            r.expiresAt  = expiresAt;
            return r;
        }

        public static ActivateResult fail(String reason) {
            ActivateResult r = new ActivateResult();
            r.success    = false;
            r.failReason = reason;
            return r;
        }

        public boolean isSuccess()    { return success; }
        public Long getUserPackId()   { return userPackId; }
        public Long getPackId()       { return packId; }
        public String getPackCode()   { return packCode; }
        public Date getExpiresAt()    { return expiresAt; }
        public String getFailReason() { return failReason; }
    }
}

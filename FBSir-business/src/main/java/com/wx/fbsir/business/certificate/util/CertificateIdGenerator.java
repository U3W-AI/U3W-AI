package com.wx.fbsir.business.certificate.util;

import com.wx.fbsir.business.certificate.mapper.CertificateApplicationMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 证书编号生成器
 * 格式：CERT + YYYYMMDD + 6位递增序号（例如：CERT20260110000001）
 */
@Component
public class CertificateIdGenerator {
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String PREFIX = "CERT";
    
    private static CertificateIdGenerator instance; // 单例实例
    
    private CertificateApplicationMapper applicationMapper;
    
    private volatile long currentSequence = 0; // 当前序列号
    private volatile String currentDateStr = ""; // 当前日期字符串
    
    @Autowired
    public void setApplicationMapper(CertificateApplicationMapper mapper) {
        this.applicationMapper = mapper;
        instance = this; // 设置实例引用
    }
    
    /**
     * 获取当前实例（用于静态方法访问）
     */
    private static CertificateIdGenerator getInstance() {
        return instance;
    }
    
    /**
     * 生成证书编号
     * @return 证书编号字符串
     */
    public static String generateCertificateId() {
        CertificateIdGenerator generator = getInstance();
        if (generator == null) {
            // 如果尚未初始化，使用简单方式生成（仅在测试或初始化期间使用）
            String dateStr = LocalDateTime.now().format(DATE_FORMATTER);
            return PREFIX + dateStr + String.format("%06d", 1);
        }
        
        return generator.generateUniqueCertificateId();
    }
    
    /**
     * 生成唯一的证书编号
     */
    private synchronized String generateUniqueCertificateId() {
        String dateStr = LocalDateTime.now().format(DATE_FORMATTER);
        
        if (!currentDateStr.equals(dateStr)) {
            // 如果日期变化，重新从数据库获取当天最大序号
            currentDateStr = dateStr;
            currentSequence = getCurrentMaxSequenceForDate(dateStr);
        }
        
        // 递增序号
        currentSequence++;
        
        // 确保序列号为6位，不足前面补0
        String seqStr = String.format("%06d", currentSequence);
        
        return PREFIX + dateStr + seqStr;
    }
    
    /**
     * 从数据库获取当天的最大序号
     */
    private long getCurrentMaxSequenceForDate(String dateStr) {
        // 查询当天的最大序号
        long maxSeq = 0;
        try {
            // 从数据库查询当天的最大序号
            maxSeq = applicationMapper.selectMaxCertificateSequenceForDate(dateStr, PREFIX);
        } catch (Exception e) {
            // 如果查询失败，从0开始
            maxSeq = 0;
        }
        
        return maxSeq;
    }
}
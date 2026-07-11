package com.wx.fbsir.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES加密解密工具类
 * 
 * 使用AES-256-GCM模式，提供高强度加密
 * GCM模式提供认证加密，防止篡改
 * 
 * @author FBSir
 * @date 2025-12-08
 */
@Component
public class AesEncryptUtils {
    
    private static final Logger log = LoggerFactory.getLogger(AesEncryptUtils.class);
    
    // AES加密算法
    private static final String ALGORITHM = "AES";
    // AES/GCM/NoPadding 模式，提供认证加密
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    // 密钥长度：256位
    private static final int KEY_SIZE = 256;
    // GCM标签长度：128位
    private static final int GCM_TAG_LENGTH = 128;
    // IV长度：12字节（GCM推荐）
    private static final int GCM_IV_LENGTH = 12;
    
    // 静态密钥，从配置文件注入
    private static String secretKey;
    
    /**
     * 从配置文件注入密钥
     */
    @Value("${aes.secret.key:}")
    public void setSecretKey(String key) {
        secretKey = key;
    }
    
    /**
     * 加密字符串
     * 
     * @param plainText 明文
     * @return Base64编码的密文（包含IV）
     * @throws Exception 加密失败
     */
    public static String encrypt(String plainText) throws Exception {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("AES密钥未配置，请在application.yml中配置aes.secret.key");
        }
        
        try {
            // 生成密钥
            SecretKeySpec keySpec = generateKey(secretKey);
            
            // 生成随机IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);
            
            // 初始化加密器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);
            
            // 加密
            byte[] plainTextBytes = plainText.getBytes(StandardCharsets.UTF_8);
            byte[] cipherText = cipher.doFinal(plainTextBytes);
            
            // 将IV和密文组合（IV + 密文）
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            byte[] cipherMessage = byteBuffer.array();
            
            // Base64编码
            return Base64.getEncoder().encodeToString(cipherMessage);
            
        } catch (Exception e) {
            log.error("[AES加密] 加密失败");
            throw new Exception("数据加密失败");
        }
    }
    
    /**
     * 解密字符串
     * 
     * @param encryptedText Base64编码的密文（包含IV）
     * @return 明文
     * @throws Exception 解密失败
     */
    public static String decrypt(String encryptedText) throws Exception {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }
        
        if (secretKey == null || secretKey.isEmpty()) {
            throw new IllegalStateException("AES密钥未配置，请在application.yml中配置aes.secret.key");
        }
        
        try {
            // 生成密钥
            SecretKeySpec keySpec = generateKey(secretKey);
            
            // Base64解码
            byte[] cipherMessage = Base64.getDecoder().decode(encryptedText);
            
            // 分离IV和密文
            ByteBuffer byteBuffer = ByteBuffer.wrap(cipherMessage);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);
            
            // 初始化解密器
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);
            
            // 解密
            byte[] plainTextBytes = cipher.doFinal(cipherText);
            
            return new String(plainTextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            log.error("[AES解密] 解密失败");
            throw new Exception("数据解密失败");
        }
    }
    
    /**
     * 生成AES密钥
     * 
     * @param key 原始密钥字符串
     * @return SecretKeySpec
     */
    private static SecretKeySpec generateKey(String key) throws Exception {
        // 使用SHA-256对密钥进行哈希，确保密钥长度为256位
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashedKey = sha.digest(keyBytes);
        
        return new SecretKeySpec(hashedKey, ALGORITHM);
    }
    
    /**
     * 生成随机AES密钥（用于初始化配置）
     * 
     * @return Base64编码的随机密钥
     */
    public static String generateRandomKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_SIZE, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            log.error("[AES] 生成随机密钥失败 - 错误: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 验证加密解密是否正常工作
     * 
     * @return true-正常，false-异常
     */
    public static boolean validateEncryption() {
        try {
            String testData = "测试数据123ABC!@#";
            String encrypted = encrypt(testData);
            String decrypted = decrypt(encrypted);
            return testData.equals(decrypted);
        } catch (Exception e) {
            log.error("[AES] 加密验证失败 - 错误: {}", e.getMessage());
            return false;
        }
    }
}

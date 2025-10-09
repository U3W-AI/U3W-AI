package com.cube.wechat.selfapp.app.util;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年01月23日 08:29
 */
import com.cube.wechat.selfapp.corpchat.util.KeyManager;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AESEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * 加密文本
     *
     * @param plainText 明文
     * @return 加密后的Base64字符串
     * @throws Exception
     */
    public static String encrypt(String plainText) throws Exception {
        SecretKeySpec secretKey = KeyManager.getSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encryptedBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encryptedBytes);
    }

    /**
     * 解密文本
     *
     * @param encryptedText 加密后的Base64字符串
     * @return 明文
     * @throws Exception
     */
    public static String decrypt(String encryptedText) throws Exception {
        SecretKeySpec secretKey = KeyManager.getSecretKey();
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        byte[] decodedBytes = Base64.getDecoder().decode(encryptedText);
        byte[] decryptedBytes = cipher.doFinal(decodedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }


    public static void main(String[] args) {
        String originalText = "{\"keyword\":\"胖东来\",\"userPrompt\":\"写一篇关于胖东来的文章，要求200字以内\",\"type\":\"START_YB\",\"taskId\":\"123232\",\"corpId\":\"ww722362817b3c466a\",\"username\":\"o3lds67b1zyFvifHTC_32epnmzqM\",\"userId\":\"22\"}";

        try {
            // 加密
            String encryptedText = AESEncryptor.encrypt(originalText);
            System.out.println("加密后的文本: " + encryptedText);

            // 解密
            String decryptedText = AESEncryptor.decrypt("vJaj+bLeyp2qAwAuD1kIfM3sLYxuQee5hclrBLTD6kGRVi8MppQSHSCmCrD42CjsfVGUxC2eejg9/OObU7ISK8rz+IRecpnbSTf8aSK/o1a1Cx/u/8X51J+npr9Hz0Rzflvj4xRT/UlMlF0v2/1plKT972faR7TogvL9cR22mkmEUn6h+bBMi/B8Yd5BS2PRdtN0IN4VctTWcDPkjWoguVsKcQbf3Ns6f0Hh70zjOfx+yn2ycNsR4NnIC4lwkfdi4c630FIUpotLoOzcdzvnxg==");
            System.out.println("解密后的文本: " + decryptedText);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

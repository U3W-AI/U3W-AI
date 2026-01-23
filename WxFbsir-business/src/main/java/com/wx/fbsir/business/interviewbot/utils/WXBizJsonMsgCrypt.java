package com.wx.fbsir.business.interviewbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Random;

/**
 * 企业微信 JSON 协议加解密工具类
 * <p>
 * 复刻自 Python Demo 中的 WXBizJsonMsgCrypt.py
 * 负责处理企业微信智能机器人的消息签名验证、解密与加密回复
 * </p>
 *
 * @author WxFbsir Team
 * @date 2026-01-22
 */
public class WXBizJsonMsgCrypt {

    private static final Logger log = LoggerFactory.getLogger(WXBizJsonMsgCrypt.class);

    static final int AES_BLOCK_SIZE = 32;

    String token;
    String encodingAesKey;
    String receiveId;
    byte[] aesKey;

    /**
     * 构造函数
     *
     * @param token          企业微信后台配置的 Token
     * @param encodingAesKey 企业微信后台配置的 EncodingAESKey
     * @param receiveId      企业ID (CorpID)
     * @throws AesException 初始化异常
     */
    public WXBizJsonMsgCrypt(String token, String encodingAesKey, String receiveId) throws AesException {
        this.token = token;
        this.encodingAesKey = encodingAesKey;
        this.receiveId = receiveId;
        try {
            this.aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
        } catch (Exception e) {
            log.error("[加解密] AES Key 初始化失败", e);
            throw new AesException(AesException.IllegalAesKey);
        }
    }

    /**
     * 验证 URL (GET 请求)
     * 用于企业微信后台配置回调地址时的首次验证
     *
     * @param msgSignature 签名串
     * @param timeStamp    时间戳
     * @param nonce        随机串
     * @param echoStr      回显串（密文）
     * @return 解密后的回显串
     * @throws AesException 验证失败
     */
    public String VerifyURL(String msgSignature, String timeStamp, String nonce, String echoStr) throws AesException {
        String signature = getSHA1(token, timeStamp, nonce, echoStr);
        if (!signature.equals(msgSignature)) {
            log.warn("[加解密] URL验证失败 - 签名不匹配");
            throw new AesException(AesException.ValidateSignatureError);
        }
        return decrypt(echoStr);
    }

    /**
     * 解密消息 (POST 请求)
     *
     * @param msgSignature 签名串
     * @param timeStamp    时间戳
     * @param nonce        随机串
     * @param postData     POST请求体（JSON字符串）
     * @return 解密后的明文 JSON 字符串（包含 content 等业务信息）
     * @throws Exception 解密异常
     */
    public String DecryptMsg(String msgSignature, String timeStamp, String nonce, String postData) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(postData);

        // 提取密文
        String encrypt = root.path("encrypt").asText();

        // 验证签名
        String signature = getSHA1(token, timeStamp, nonce, encrypt);
        if (!signature.equals(msgSignature)) {
            log.warn("[加解密] 消息解密失败 - 签名不匹配");
            throw new AesException(AesException.ValidateSignatureError);
        }

        // 解密
        return decrypt(encrypt);
    }

    /**
     * 加密消息 (用于回复)
     *
     * @param replyMsg  待回复的明文内容 (通常是 JSON 格式的字符串)
     * @param timeStamp 时间戳
     * @param nonce     随机串
     * @return 加密后的 JSON 字符串 (符合企业微信 Stream 协议)
     * @throws Exception 加密异常
     */
    public String EncryptMsg(String replyMsg, String timeStamp, String nonce) throws Exception {
        // 加密内容
        String encrypt = encrypt(getRandomStr(), replyMsg);

        // 生成签名
        if (timeStamp == null) {
            timeStamp = Long.toString(System.currentTimeMillis() / 1000);
        }
        String signature = getSHA1(token, timeStamp, nonce, encrypt);

        // 构造 JSON 返回包
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("encrypt", encrypt);
        root.put("msgsignature", signature);
        root.put("timestamp", timeStamp);
        root.put("nonce", nonce);

        return mapper.writeValueAsString(root);
    }

    // --- 内部算法实现 ---

    /**
     * 内部解密逻辑
     */
    String decrypt(String text) throws AesException {
        byte[] original;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
            byte[] encrypted = Base64.getDecoder().decode(text);
            original = cipher.doFinal(encrypted);
        } catch (Exception e) {
            log.error("[加解密] AES解密执行失败", e);
            throw new AesException(AesException.DecryptAESError);
        }

        String content;
        try {
            byte[] bytes = PKCS7Encoder.decode(original);
            byte[] networkOrder = Arrays.copyOfRange(bytes, 16, 20);
            int xmlLength = recoverNetworkBytesOrder(networkOrder);
            content = new String(Arrays.copyOfRange(bytes, 20, 20 + xmlLength), StandardCharsets.UTF_8);
            // receiveId 校验逻辑在此处省略，智能机器人场景通常不强校验
        } catch (Exception e) {
            log.error("[加解密] 解密后Buffer解析失败", e);
            throw new AesException(AesException.IllegalBuffer);
        }

        return content;
    }

    /**
     * 内部加密逻辑
     */
    String encrypt(String randomStr, String text) throws AesException {
        ByteGroup byteCollector = new ByteGroup();
        byte[] randomStrBytes = randomStr.getBytes(StandardCharsets.UTF_8);
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
        byte[] networkBytesOrder = getNetworkBytesOrder(textBytes.length);
        byte[] receiveIdBytes = receiveId.getBytes(StandardCharsets.UTF_8);

        byteCollector.addBytes(randomStrBytes);
        byteCollector.addBytes(networkBytesOrder);
        byteCollector.addBytes(textBytes);
        byteCollector.addBytes(receiveIdBytes);

        byte[] padBytes = PKCS7Encoder.encode(byteCollector.size());
        byteCollector.addBytes(padBytes);
        byte[] unencrypted = byteCollector.toBytes();

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, iv);
            byte[] encrypted = cipher.doFinal(unencrypted);
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            log.error("[加解密] AES加密执行失败", e);
            throw new AesException(AesException.EncryptAESError);
        }
    }

    /**
     * SHA1 签名生成
     */
    public static String getSHA1(String token, String timestamp, String nonce, String encrypt) throws AesException {
        try {
            String[] array = new String[]{token, timestamp, nonce, encrypt};
            Arrays.sort(array);
            StringBuilder sb = new StringBuilder();
            for (String s : array) sb.append(s);
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            md.update(sb.toString().getBytes());
            byte[] digest = md.digest();

            StringBuilder hexstr = new StringBuilder();
            for (byte b : digest) {
                String shaHex = Integer.toHexString(b & 0xFF);
                if (shaHex.length() < 2) hexstr.append(0);
                hexstr.append(shaHex);
            }
            return hexstr.toString();
        } catch (Exception e) {
            log.error("[加解密] SHA1签名计算失败", e);
            throw new AesException(AesException.ComputeSignatureError);
        }
    }

    // --- 辅助工具类 ---

    private static byte[] getNetworkBytesOrder(int sourceNumber) {
        byte[] orderBytes = new byte[4];
        orderBytes[3] = (byte) (sourceNumber & 0xFF);
        orderBytes[2] = (byte) (sourceNumber >> 8 & 0xFF);
        orderBytes[1] = (byte) (sourceNumber >> 16 & 0xFF);
        orderBytes[0] = (byte) (sourceNumber >> 24 & 0xFF);
        return orderBytes;
    }

    private static int recoverNetworkBytesOrder(byte[] orderBytes) {
        int sourceNumber = 0;
        for (int i = 0; i < 4; i++) {
            sourceNumber <<= 8;
            sourceNumber |= orderBytes[i] & 0xff;
        }
        return sourceNumber;
    }

    private static String getRandomStr() {
        String base = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            int number = random.nextInt(base.length());
            sb.append(base.charAt(number));
        }
        return sb.toString();
    }

    /**
     * 字节数组容器
     */
    static class ByteGroup {
        java.util.ArrayList<Byte> byteContainer = new java.util.ArrayList<>();

        public byte[] toBytes() {
            byte[] bytes = new byte[byteContainer.size()];
            for (int i = 0; i < byteContainer.size(); i++) bytes[i] = byteContainer.get(i);
            return bytes;
        }

        public void addBytes(byte[] bytes) {
            for (byte b : bytes) byteContainer.add(b);
        }

        public int size() {
            return byteContainer.size();
        }
    }

    /**
     * PKCS7 填充算法
     */
    static class PKCS7Encoder {
        static byte[] encode(int count) {
            int amountToPad = AES_BLOCK_SIZE - (count % AES_BLOCK_SIZE);
            if (amountToPad == 0) amountToPad = AES_BLOCK_SIZE;
            char padChr = chr(amountToPad);
            StringBuilder tmp = new StringBuilder();
            for (int index = 0; index < amountToPad; index++) tmp.append(padChr);
            return tmp.toString().getBytes(StandardCharsets.UTF_8);
        }

        static byte[] decode(byte[] decrypted) {
            int pad = decrypted[decrypted.length - 1];
            if (pad < 1 || pad > 32) pad = 0;
            return Arrays.copyOfRange(decrypted, 0, decrypted.length - pad);
        }

        static char chr(int a) {
            return (char) (a & 0xFF);
        }
    }
}
package com.wx.fbsir.business.resume.utils;

import java.security.SecureRandom;

/**
 * 随机访问码生成器
 * 用于简历访问
 */
public class AccessCodeGenerator {

    // 可用字符：大写 + 小写
    private static final String CHAR_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    // 安全随机数生成器
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成指定长度的随机访问码
     *
     * @param length 访问码长度
     * @return 随机访问码字符串
     */
    public static String generate(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException("访问码长度必须大于0");
        }

        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = RANDOM.nextInt(CHAR_POOL.length());
            sb.append(CHAR_POOL.charAt(index));
        }
        return sb.toString();
    }

    /**
     * 生成4位访问码
     *
     * @return 4位随机访问码
     */
    public static String generate4() {
        return generate(4);
    }


}
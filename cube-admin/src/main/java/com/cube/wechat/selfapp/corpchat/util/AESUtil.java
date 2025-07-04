package com.cube.wechat.selfapp.corpchat.util;

/**
 * @author YHX
 * @date 2024年07月24日 13:52
 */

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.Charset;
import java.util.Arrays;

public class AESUtil {
    private static final String key = "0ectVftLe76LrtbPGbzJnwqNuXo8rLpMeFaBjPcZJ8J"; // 替换成你自己的密钥
    static Charset CHARSET = Charset.forName("utf-8");

    String receiveid;


    // 还原4个字节的网络字节序
    public static int recoverNetworkBytesOrder(byte[] orderBytes) {
        int sourceNumber = 0;
        for (int i = 0; i < 4; i++) {
            sourceNumber <<= 8;
            sourceNumber |= orderBytes[i] & 0xff;
        }
        return sourceNumber;
    }
     public static String decrypt(String text)  {
        byte[] original = new byte[0];
        try {
            // 设置解密模式为AES的CBC模式
            byte[] aesKey = org.apache.commons.codec.binary.Base64.decodeBase64("0ectVftLe76LrtbPGbzJnwqNuXo8rLpMeFaBjPcZJ8J" + "=");

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec key_spec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.DECRYPT_MODE, key_spec, iv);

            // 使用BASE64对密文进行解码
            byte[] encrypted = org.apache.commons.codec.binary.Base64.decodeBase64(text);

            // 解密
            original = cipher.doFinal(encrypted);
        } catch (Exception e) {
            e.printStackTrace();
        }

        String xmlContent = null;
        try {
            // 去除补位字符
            byte[] bytes = PKCS7Encoder.decode(original);

            // 分离16位随机字符串,网络字节序和receiveid
            byte[] networkOrder = Arrays.copyOfRange(bytes, 16, 20);

            int xmlLength = recoverNetworkBytesOrder(networkOrder);

            xmlContent = new String(Arrays.copyOfRange(bytes, 20, 20 + xmlLength), CHARSET);
           String from_receiveid = new String(Arrays.copyOfRange(bytes, 20 + xmlLength, bytes.length),
                    CHARSET);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // receiveid不相同的情况
        return xmlContent;

    }
    public static void main(String[] args) {
        String msg = decrypt("dHJQ322gEVnsv9i41ckTNdVg6yetYiV1nawgXniF/4o0lwcyBldvW8X/VPMwbwVOGU+cYxz/bTWbE5MFQeC2PJSUUF/aoQu3GeBy6HE+/6Rfy6bpYKtg2x3lRzmktF2Y8EOdAnt5dB8G98lrcdrDnB4e62kk6JmxSK4rH6yJCDIGiJjUmh4ZLxVta6c6YYxyR3b3Dj2XK8eyZO0+ix3mztxjL9hR3MFlBJqtegcf/Fe4BaV+Gp+qonvsEbAJPs9LY1Y+96LdJ9S5rrryQf24QOaqA9ImLh5AEyCPqA2OuCLI7m8d4Lm30g73YI6bpLUI4C8fZh1+NWbaThNrJIt3uzoBH7IgF114nD9Rd5glXOOeSRD4TpbEUgy9NR6NXxrp");
        System.out.println(msg);
    }
}

package com.tencent.wework;

import org.apache.commons.codec.binary.Base64;
import sun.security.util.DerInputStream;
import sun.security.util.DerValue;

import javax.crypto.Cipher;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.RSAPrivateCrtKeySpec;

/**
 * @author wl
 * @version 1.0
 * @description: TODO
 * @date 2021/6/28 15:36
 */
public class RSAEncrypt {

    public static String decryptRSA(String str, String privateKey) throws Exception {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        Cipher rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding", "BC");
        rsa.init(Cipher.DECRYPT_MODE, getPrivateKey(privateKey));
        byte[] utf8 = rsa.doFinal(Base64.decodeBase64(str));
        String result = new String(utf8,"UTF-8");
        return result;
    }

    public static PrivateKey getPrivateKey (String privateKey) throws Exception {
        String privKeyPEMnew = privateKey.replaceAll("\\n", "").replace("-----BEGIN RSA PRIVATE KEY-----", "").replace("-----END RSA PRIVATE KEY-----", "");
        byte[] bytes = java.util.Base64.getDecoder().decode(privKeyPEMnew);

        DerInputStream derReader = new DerInputStream(bytes);
        DerValue[] seq = derReader.getSequence(0);
        BigInteger modulus = seq[1].getBigInteger();
        BigInteger publicExp = seq[2].getBigInteger();
        BigInteger privateExp = seq[3].getBigInteger();
        BigInteger prime1 = seq[4].getBigInteger();
        BigInteger prime2 = seq[5].getBigInteger();
        BigInteger exp1 = seq[6].getBigInteger();
        BigInteger exp2 = seq[7].getBigInteger();
        BigInteger crtCoef = seq[8].getBigInteger();

        RSAPrivateCrtKeySpec keySpec = new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKeys = keyFactory.generatePrivate(keySpec);
        return privateKeys;

    }

}

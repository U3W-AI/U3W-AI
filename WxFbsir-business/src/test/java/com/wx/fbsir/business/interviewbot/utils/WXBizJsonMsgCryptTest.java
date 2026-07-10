package com.wx.fbsir.business.interviewbot.utils;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WXBizJsonMsgCryptTest {

    private static final String AES_KEY = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";

    @Test
    void emptyReceiveIdDoesNotAppendCorpIdToSmartBotEnvelope() throws Exception {
        WXBizJsonMsgCrypt crypt = new WXBizJsonMsgCrypt("token", AES_KEY, "");
        String message = "{\"msgtype\":\"stream\"}";

        String encrypted = crypt.encrypt("0123456789abcdef", message);
        byte[] unpadded = PKCS7Encoder.decode(decryptRaw(crypt.aesKey, encrypted));
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(messageBytes, Arrays.copyOfRange(unpadded, 20, 20 + messageBytes.length));
        assertEquals(20 + messageBytes.length, unpadded.length);
    }

    @Test
    void decryptRejectsAnEnvelopeForAnotherReceiveId() throws Exception {
        WXBizJsonMsgCrypt anotherApplication = new WXBizJsonMsgCrypt("token", AES_KEY, "corp-id");
        WXBizJsonMsgCrypt smartBot = new WXBizJsonMsgCrypt("token", AES_KEY, "");

        String encrypted = anotherApplication.encrypt("0123456789abcdef", "{\"msgtype\":\"text\"}");

        AesException exception = assertThrows(AesException.class, () -> smartBot.decrypt(encrypted));
        assertEquals(AesException.ValidateCorpidError, exception.getCode());
    }

    private byte[] decryptRaw(byte[] aesKey, String encrypted) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,
            new SecretKeySpec(aesKey, "AES"),
            new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16)));
        return cipher.doFinal(Base64.getDecoder().decode(encrypted));
    }
}

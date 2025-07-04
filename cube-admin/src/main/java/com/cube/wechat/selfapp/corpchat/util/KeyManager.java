package com.cube.wechat.selfapp.corpchat.util;

import lombok.Data;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2025年01月22日 09:28
 */
@Data
public class KeyManager {

    private static final String publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA4J14wkXaHKo5DDhIBI7f\n" +
            "p+Eayt5HP+xm+lY9L9PjAc/vH294vasXj/rjb4gOw1kaoExWOgL9z/5mu/c3mQqw\n" +
            "tT/29f57VKpIKkeL0Rz+2tS21WxzkRdITqH/P5F88udkjQ1DmqmfTDHJIHTwCsgX\n" +
            "UaxN3XfrDL5/dfyw/dpJmXau0UMsyHor0VduKvX0tjmKKAN/mJai7eIYCo6WBt7q\n" +
            "zf0kjNyi+wagdLz8S9rBSnohTNkehWMrwrjNfwpBqkZtTuqXcK/BeedPW4cDWHCL\n" +
            "r6YtqTJLwspeUAHvd/j0FvMnHQ7CZ+sAfIEoDzmEcLvRUt9ptGMOZg9Kzg+wBTjQ\n" +
            "awIDAQAB";

    private static final String privateKey ="-----BEGIN PRIVATE KEY-----\n" +
            "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDgnXjCRdocqjkM\n" +
            "OEgEjt+n4RrK3kc/7Gb6Vj0v0+MBz+8fb3i9qxeP+uNviA7DWRqgTFY6Av3P/ma7\n" +
            "9zeZCrC1P/b1/ntUqkgqR4vRHP7a1LbVbHORF0hOof8/kXzy52SNDUOaqZ9MMckg\n" +
            "dPAKyBdRrE3dd+sMvn91/LD92kmZdq7RQyzIeivRV24q9fS2OYooA3+YlqLt4hgK\n" +
            "jpYG3urN/SSM3KL7BqB0vPxL2sFKeiFM2R6FYyvCuM1/CkGqRm1O6pdwr8F5509b\n" +
            "hwNYcIuvpi2pMkvCyl5QAe93+PQW8ycdDsJn6wB8gSgPOYRwu9FS32m0Yw5mD0rO\n" +
            "D7AFONBrAgMBAAECggEBANRpFoHhvNnOwBQmRTTKSAdigQvIGGkDDn8+fq50GgDk\n" +
            "uqVnrS7wVV/7Sol2iT+DTAK7Le++VHgVhOHMb+9yhJli3BDVj5wJTCVvc3FVkk0v\n" +
            "S5eY79ENy3tRByj+VMMAv74EBSKFtfdAPYtMCpSnlCXQY/JELSvoGnFhfZ8Shvdv\n" +
            "12UHfladcS2B67Acxux8fLYWmoT5GgW9+rJ/lrwc6nOth8PB7YNSWylPznIGOswH\n" +
            "6VKdRv+H+Jc75iRACWmQzF+P1Dy66y7Xy1URYK0ZPHDNtoKLm+ckZyKInmgPhsUj\n" +
            "EvvhcHEICzWXAOqGk4TFq5rEHPkbSrq7ceypwLAMzMECgYEA8bvwWmbZMfz/SUXD\n" +
            "I9UakKnheV81FI3YwkDqOhPIlP8m9OS1mc4Q5jC8J7I3PzjOmeZrcQvMSNCCLA8i\n" +
            "a9Kn8YsUTgEVziG+0SbGSo0fDe3+EoG6M7SdULbRbbYtlQCjlGknjlOm9ZoBh3/f\n" +
            "6Rvm1A6QPFIBPa6w3ljy/ETGRJcCgYEA7d7nJammwneCClGVl3EeAuZgWEKeZW/l\n" +
            "6LQ4KiKoM7P4mtmDyb2Qx/9KUtuTyrFVIIU7cUeyFQM/AHldMB1eOCdAk7xE9YSf\n" +
            "4DKV3ypydr4UMwL6h3jPpFI+JozEAdkJpjQh+QR332hBecI90u6vUQBNweZ2SZH4\n" +
            "Kb0zg6F2KU0CgYAA/Q/CjuEcfzhcCFj/8IMnTEqLwIPrDZlxUeqjSilr0o9KORU1\n" +
            "B86x4kkisGvhak4N+w89axruwUuG+pjdcAMXzcXLe92m9iRdnpb4/xK9b9e2I6mr\n" +
            "RHXQOq8oj7IoBdSlariqsFTidDrFXxR/U4niHB0i4gL3SZuXjS2RMobNCQKBgHDF\n" +
            "QlRqr7J2MeJudzsK162MRpTRUILKn/bIqe/f6lpIsk3HXWFhS6hlcXkCvHhVxgPa\n" +
            "5il4UgcAOPgikXlkEu0QPt9LXG4BaEJD+SWJ3CZLSww8F0XukpJWozxnc+1kVdzD\n" +
            "zm9eHO5/YLs2yVSc7+S4/iQ0/FM4rqwrFKcM/xXpAoGALdyBu2ccLaCJ2cjcFraK\n" +
            "BNtFCbiSTaQyFTNHHLDQ31uey/7bnXpja27ur2lezgL5LdwJ7vMIqgXuHoNhnHp3\n" +
            "5ue1y82UYhACo4p94OozoutXtbZX1VFX9i0t/0HHY5c0Wd9BA+LhFRx+lsVEb2H1\n" +
            "ujJ4HlHxXtx+5PEhiqGPd0w=\n" +
            "-----END PRIVATE KEY-----\n";


    private static final String KEY = "Meooota@2025#123";

    public static SecretKeySpec getSecretKey() {
        return new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES");
    }
    // 获取公钥
    public static String getPublicKey() {
        return publicKey;
    }

    // 获取私钥
    public static String getPrivateKey() {
        return privateKey;
    }
}

package uz.taskapp.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class CryptoSupport {
    private CryptoSupport() {
    }

    static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 mavjud emas", exception);
        }
    }

    static byte[] hmacSha256(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 mavjud emas", exception);
        }
    }

    static String hmacHex(byte[] key, String value) {
        return HexFormat.of().formatHex(hmacSha256(key, value));
    }

    static boolean constantTimeEqualsHex(String expectedHex, String actualHex) {
        try {
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(expectedHex),
                    HexFormat.of().parseHex(actualHex)
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}

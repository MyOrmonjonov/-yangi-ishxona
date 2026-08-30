package org.example.yangi_ishxona.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Verifies the Telegram Login Widget callback per the algorithm described at
 * https://core.telegram.org/widgets/login#checking-authorization -
 * HMAC-SHA256(data_check_string, SHA256(bot_token)) must equal the provided hash,
 * and auth_date must not be stale.
 */
@Service
public class TelegramLoginService {

    private static final long MAX_AUTH_AGE_SECONDS = 24 * 60 * 60;

    @Value("${telegram.bot.token}")
    private String botToken;

    public boolean verify(Map<String, String> params) {
        String hash = params.get("hash");
        if (hash == null || botToken == null || botToken.isBlank()) {
            return false;
        }
        SortedMap<String, String> sorted = new TreeMap<>(params);
        sorted.remove("hash");
        String dataCheckString = sorted.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] secretKey = sha256.digest(botToken.getBytes(StandardCharsets.UTF_8));

            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] computed = mac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            String computedHex = toHex(computed);

            if (!computedHex.equalsIgnoreCase(hash)) {
                return false;
            }
            String authDate = params.get("auth_date");
            if (authDate == null) {
                return false;
            }
            long ageSeconds = Instant.now().getEpochSecond() - Long.parseLong(authDate);
            return ageSeconds >= 0 && ageSeconds <= MAX_AUTH_AGE_SECONDS;
        } catch (Exception e) {
            return false;
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

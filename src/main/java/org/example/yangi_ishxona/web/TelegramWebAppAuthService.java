package org.example.yangi_ishxona.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Verifies Telegram Mini App {@code initData} per
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app -
 * a DIFFERENT algorithm from the Login Widget (secret key here is
 * HMAC-SHA256("WebAppData", bot_token), not SHA256(bot_token) directly).
 */
@Service
public class TelegramWebAppAuthService {

    private static final long MAX_AUTH_AGE_SECONDS = 24 * 60 * 60;

    @Value("${telegram.bot.token}")
    private String botToken;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Optional<Long> verifyAndExtractUserId(String initData) {
        if (initData == null || initData.isBlank() || botToken == null || botToken.isBlank()) {
            return Optional.empty();
        }
        SortedMap<String, String> params = new TreeMap<>();
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        String hash = params.remove("hash");
        if (hash == null) {
            return Optional.empty();
        }
        String dataCheckString = params.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        try {
            Mac secretMac = Mac.getInstance("HmacSHA256");
            secretMac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] secretKey = secretMac.doFinal(botToken.getBytes(StandardCharsets.UTF_8));

            Mac dataMac = Mac.getInstance("HmacSHA256");
            dataMac.init(new SecretKeySpec(secretKey, "HmacSHA256"));
            byte[] computed = dataMac.doFinal(dataCheckString.getBytes(StandardCharsets.UTF_8));
            String computedHex = toHex(computed);

            if (!computedHex.equalsIgnoreCase(hash)) {
                return Optional.empty();
            }
            String authDate = params.get("auth_date");
            if (authDate == null) {
                return Optional.empty();
            }
            long ageSeconds = Instant.now().getEpochSecond() - Long.parseLong(authDate);
            if (ageSeconds < 0 || ageSeconds > MAX_AUTH_AGE_SECONDS) {
                return Optional.empty();
            }
            String userJson = params.get("user");
            if (userJson == null) {
                return Optional.empty();
            }
            JsonNode userNode = objectMapper.readTree(userJson);
            return Optional.of(userNode.path("id").asLong());
        } catch (Exception e) {
            return Optional.empty();
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

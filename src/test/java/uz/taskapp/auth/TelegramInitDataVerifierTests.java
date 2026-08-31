package uz.taskapp.auth;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.TelegramProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelegramInitDataVerifierTests {
    private static final String BOT_TOKEN = "123456:test-token";
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void acceptsValidTelegramPayload() throws Exception {
        TelegramInitDataVerifier verifier = verifier(Duration.ofHours(1));
        String user = "{\"id\":998877,\"first_name\":\"Ali\",\"username\":\"ali\",\"language_code\":\"uz\"}";
        String check = "auth_date=" + NOW.getEpochSecond() + "\nquery_id=AAE123\nuser=" + user;
        String hash = sign(check);
        String initData = "query_id=AAE123&user=" + encode(user) + "&auth_date=" + NOW.getEpochSecond() + "&hash=" + hash;

        VerifiedTelegramData result = verifier.verify(initData);

        assertEquals(998877L, result.user().id());
        assertEquals("Ali", result.user().firstName());
    }

    @Test
    void rejectsExpiredPayload() throws Exception {
        TelegramInitDataVerifier verifier = verifier(Duration.ofMinutes(10));
        long oldDate = NOW.minus(Duration.ofHours(2)).getEpochSecond();
        String user = "{\"id\":1,\"first_name\":\"Test\"}";
        String check = "auth_date=" + oldDate + "\nuser=" + user;
        String initData = "auth_date=" + oldDate + "&user=" + encode(user) + "&hash=" + sign(check);

        assertThrows(ApiException.class, () -> verifier.verify(initData));
    }

    private TelegramInitDataVerifier verifier(Duration maxAge) {
        TelegramProperties properties = new TelegramProperties(BOT_TOKEN, "TaskAppBot", "https://app.example.com", maxAge);
        return new TelegramInitDataVerifier(properties, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private String sign(String data) throws Exception {
        Mac secretMac = Mac.getInstance("HmacSHA256");
        secretMac.init(new SecretKeySpec("WebAppData".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] secret = secretMac.doFinal(BOT_TOKEN.getBytes(StandardCharsets.UTF_8));
        Mac dataMac = Mac.getInstance("HmacSHA256");
        dataMac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return HexFormat.of().formatHex(dataMac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}

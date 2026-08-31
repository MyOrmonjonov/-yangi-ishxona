package uz.taskapp.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.TelegramProperties;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Verifies the Telegram Login Widget's callback payload - a different signing scheme from the
 * Mini App's {@code initData} (secret = SHA-256(bot token) directly, not HMAC-SHA256("WebAppData", token)).
 * Used for browser access to the dashboard outside Telegram (e.g. a director opening it from a desktop browser).
 */
@Component
public class TelegramLoginVerifier {
    private final TelegramProperties properties;
    private final Clock clock;

    public TelegramLoginVerifier(TelegramProperties properties) {
        this(properties, Clock.systemUTC());
    }

    TelegramLoginVerifier(TelegramProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public TelegramUserData verify(Map<String, String> fields) {
        if (properties.botToken() == null || properties.botToken().isBlank()) {
            throw unauthorized("Telegram bot tokeni sozlanmagan");
        }
        Map<String, String> values = new TreeMap<>(fields);
        String actualHash = values.remove("hash");
        if (actualHash == null || actualHash.isBlank()) {
            throw unauthorized("Telegram login hash mavjud emas");
        }

        String dataCheckString = values.entrySet().stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        byte[] secretKey = CryptoSupport.sha256(properties.botToken());
        String expectedHash = CryptoSupport.hmacHex(secretKey, dataCheckString);
        if (!CryptoSupport.constantTimeEqualsHex(expectedHash, actualHash)) {
            throw unauthorized("Telegram login imzosi noto'g'ri");
        }

        Instant authTime = parseAuthDate(values.get("auth_date"));
        Instant now = clock.instant();
        if (authTime.isAfter(now.plusSeconds(30)) || authTime.isBefore(now.minus(Duration.ofDays(1)))) {
            throw unauthorized("Telegram login eskirgan, qayta urinib ko'ring");
        }

        String idRaw = values.get("id");
        String firstName = values.get("first_name");
        if (idRaw == null || firstName == null || firstName.isBlank()) {
            throw unauthorized("Telegram foydalanuvchi ma'lumoti noto'g'ri");
        }
        long id;
        try {
            id = Long.parseLong(idRaw);
        } catch (NumberFormatException exception) {
            throw unauthorized("Telegram foydalanuvchi id noto'g'ri");
        }
        return new TelegramUserData(id, firstName, values.get("last_name"), values.get("username"),
                values.get("photo_url"), null);
    }

    private Instant parseAuthDate(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (RuntimeException exception) {
            throw unauthorized("Telegram auth_date noto'g'ri");
        }
    }

    private ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "TELEGRAM_LOGIN_INVALID", message);
    }
}

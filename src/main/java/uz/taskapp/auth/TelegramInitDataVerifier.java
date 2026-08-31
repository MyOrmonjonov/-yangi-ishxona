package uz.taskapp.auth;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.TelegramProperties;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class TelegramInitDataVerifier {
    private static final byte[] WEB_APP_DATA_KEY = "WebAppData".getBytes(StandardCharsets.UTF_8);

    private final TelegramProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public TelegramInitDataVerifier(TelegramProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    TelegramInitDataVerifier(TelegramProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public VerifiedTelegramData verify(String initData) {
        if (properties.botToken() == null || properties.botToken().isBlank()) {
            throw unauthorized("Telegram bot tokeni sozlanmagan");
        }
        Map<String, String> values = parseQuery(initData);
        String actualHash = values.remove("hash");
        if (actualHash == null || actualHash.isBlank()) {
            throw unauthorized("Telegram initData hash mavjud emas");
        }

        String dataCheckString = values.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));
        byte[] secretKey = CryptoSupport.hmacSha256(WEB_APP_DATA_KEY, properties.botToken());
        String expectedHash = CryptoSupport.hmacHex(secretKey, dataCheckString);
        if (!CryptoSupport.constantTimeEqualsHex(expectedHash, actualHash)) {
            throw unauthorized("Telegram initData imzosi noto'g'ri");
        }

        Instant authTime = parseAuthDate(values.get("auth_date"));
        Duration maxAge = properties.initDataMaxAge() == null ? Duration.ofHours(24) : properties.initDataMaxAge();
        Instant now = clock.instant();
        if (authTime.isAfter(now.plusSeconds(30)) || authTime.isBefore(now.minus(maxAge))) {
            throw unauthorized("Telegram initData eskirgan");
        }

        String rawUser = values.get("user");
        if (rawUser == null) {
            throw unauthorized("Telegram foydalanuvchi ma'lumoti mavjud emas");
        }
        try {
            TelegramUserData user = objectMapper.readValue(rawUser, TelegramUserData.class);
            if (user.id() <= 0 || user.firstName() == null || user.firstName().isBlank()) {
                throw unauthorized("Telegram foydalanuvchi ma'lumoti noto'g'ri");
            }
            return new VerifiedTelegramData(user, authTime, values.get("start_param"));
        } catch (JacksonException exception) {
            throw unauthorized("Telegram foydalanuvchi ma'lumoti o'qilmadi");
        }
    }

    private Map<String, String> parseQuery(String query) {
        if (query == null || query.isBlank()) {
            throw unauthorized("Telegram initData bo'sh");
        }
        try {
            return Arrays.stream(query.split("&"))
                    .map(part -> part.split("=", 2))
                    .collect(Collectors.toMap(
                            part -> decode(part[0]),
                            part -> part.length == 2 ? decode(part[1]) : "",
                            (left, right) -> right,
                            TreeMap::new
                    ));
        } catch (RuntimeException exception) {
            throw unauthorized("Telegram initData formati noto'g'ri");
        }
    }

    private Instant parseAuthDate(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (RuntimeException exception) {
            throw unauthorized("Telegram auth_date noto'g'ri");
        }
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "TELEGRAM_AUTH_INVALID", message);
    }
}

package uz.taskapp.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.AppProperties;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AccessTokenService {
    private final AppProperties properties;
    private final Clock clock = Clock.systemUTC();

    public AccessTokenService(AppProperties properties) {
        this.properties = properties;
    }

    public String issue(Long userId) {
        Duration ttl = properties.accessTokenTtl() == null ? Duration.ofDays(7) : properties.accessTokenTtl();
        String payload = userId + ":" + clock.instant().plus(ttl).getEpochSecond();
        String encodedPayload = encode(payload.getBytes(StandardCharsets.UTF_8));
        String signature = encode(CryptoSupport.hmacSha256(secret(), encodedPayload));
        return encodedPayload + "." + signature;
    }

    public Long verify(String token) {
        try {
            String[] parts = token.split("\\.", 2);
            if (parts.length != 2) {
                throw unauthorized();
            }
            byte[] expected = CryptoSupport.hmacSha256(secret(), parts[0]);
            byte[] actual = Base64.getUrlDecoder().decode(parts[1]);
            if (!java.security.MessageDigest.isEqual(expected, actual)) {
                throw unauthorized();
            }
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] values = payload.split(":", 2);
            long userId = Long.parseLong(values[0]);
            Instant expiresAt = Instant.ofEpochSecond(Long.parseLong(values[1]));
            if (!expiresAt.isAfter(clock.instant())) {
                throw unauthorized();
            }
            return userId;
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
    }

    private byte[] secret() {
        String value = properties.authSecret();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("APP_AUTH_SECRET sozlanmagan");
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "ACCESS_TOKEN_INVALID", "Sessiya yaroqsiz yoki muddati tugagan");
    }
}

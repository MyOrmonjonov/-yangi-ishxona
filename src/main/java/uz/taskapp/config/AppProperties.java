package uz.taskapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app")
public record AppProperties(
        String authSecret,
        Duration accessTokenTtl,
        String uploadDirectory,
        long bootstrapOwnerTelegramId
) {
    public AppProperties {
        if (authSecret == null || authSecret.isBlank()) {
            throw new IllegalStateException(
                    "APP_AUTH_SECRET muhit o'zgaruvchisi majburiy - jim fallback qiymat bilan ishga tushish taqiqlangan");
        }
    }
}

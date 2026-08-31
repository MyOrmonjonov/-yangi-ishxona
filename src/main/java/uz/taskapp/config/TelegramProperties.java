package uz.taskapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("telegram")
public record TelegramProperties(
        String botToken,
        String botUsername,
        String miniAppUrl,
        Duration initDataMaxAge
) {
}

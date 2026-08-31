package uz.taskapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("yandex")
public record YandexProperties(
        String apiKey,
        String folderId,
        String baseUrl,
        String language,
        Duration requestTimeout
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank() && folderId != null && !folderId.isBlank();
    }
}

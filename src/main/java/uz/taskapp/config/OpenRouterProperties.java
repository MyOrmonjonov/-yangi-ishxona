package uz.taskapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("openrouter")
public record OpenRouterProperties(
        String apiKey,
        String baseUrl,
        String transcriptionModel,
        String extractionModel,
        Duration requestTimeout
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}

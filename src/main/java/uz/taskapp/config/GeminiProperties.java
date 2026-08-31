package uz.taskapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("gemini")
public record GeminiProperties(
        String apiKey,
        String model,
        String baseUrl,
        Duration requestTimeout
) {
    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }
}

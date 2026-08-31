package uz.taskapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("groq")
public record GroqProperties(
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

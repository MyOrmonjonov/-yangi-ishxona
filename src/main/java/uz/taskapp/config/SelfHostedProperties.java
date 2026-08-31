package uz.taskapp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Config for a self-hosted STT (faster-whisper-server) + extraction LLM (llama.cpp server), both
 * exposing OpenAI-compatible REST APIs, running on a separate box we control (not a paid third-party
 * API) - see {@code voice-services/compose.yml}.
 */
@ConfigurationProperties("selfhosted")
public record SelfHostedProperties(
        String sttBaseUrl,
        String llmBaseUrl,
        String sttModel,
        String llmModel,
        String apiKey,
        Duration requestTimeout
) {
    public boolean configured() {
        return sttBaseUrl != null && !sttBaseUrl.isBlank()
                && llmBaseUrl != null && !llmBaseUrl.isBlank();
    }
}

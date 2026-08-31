package uz.taskapp.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uz.taskapp.config.GroqProperties;
import uz.taskapp.config.YandexProperties;

/**
 * Transcribes via Yandex SpeechKit (strong Uzbek support) and extracts structured
 * fields via Groq's free chat completions - the extraction step already worked well
 * there, so only the weaker transcription half is being swapped out.
 */
@Component
@ConditionalOnProperty(prefix = "voice", name = "provider", havingValue = "yandex")
class YandexVoiceModelClient extends AbstractTwoStepVoiceModelClient {
    private final YandexClient yandexClient;
    private final YandexProperties yandexProperties;
    private final GroqClient groqClient;
    private final GroqProperties groqProperties;

    YandexVoiceModelClient(YandexClient yandexClient, YandexProperties yandexProperties,
                            GroqClient groqClient, GroqProperties groqProperties, ObjectMapper objectMapper) {
        super(objectMapper);
        this.yandexClient = yandexClient;
        this.yandexProperties = yandexProperties;
        this.groqClient = groqClient;
        this.groqProperties = groqProperties;
    }

    @Override
    public boolean configured() {
        return yandexProperties.configured() && groqProperties.configured();
    }

    @Override
    protected String transcribeAudio(byte[] audio, String mimeType) {
        return yandexClient.recognize(audio, mimeType);
    }

    @Override
    protected String extractionModel() {
        return groqProperties.extractionModel();
    }

    @Override
    protected OpenRouterChatResponse sendChat(OpenRouterChatRequest request) {
        return groqClient.chatCompletion(request);
    }
}

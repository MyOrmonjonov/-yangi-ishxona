package uz.taskapp.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uz.taskapp.config.OpenRouterProperties;

import java.util.Base64;

@Component
@ConditionalOnProperty(prefix = "voice", name = "provider", havingValue = "openrouter")
class OpenRouterVoiceModelClient extends AbstractTwoStepVoiceModelClient {
    private final OpenRouterClient client;
    private final OpenRouterProperties properties;

    OpenRouterVoiceModelClient(OpenRouterClient client, OpenRouterProperties properties, ObjectMapper objectMapper) {
        super(objectMapper);
        this.client = client;
        this.properties = properties;
    }

    @Override
    public boolean configured() {
        return properties.configured();
    }

    @Override
    protected String transcribeAudio(byte[] audio, String mimeType) {
        OpenRouterTranscriptionRequest request = new OpenRouterTranscriptionRequest(
                properties.transcriptionModel(),
                new OpenRouterInputAudio(Base64.getEncoder().encodeToString(audio), AudioFormats.openRouterFormat(mimeType)),
                "uz"
        );
        OpenRouterTranscriptionResponse response = client.transcribe(request);
        return response == null ? null : response.text();
    }

    @Override
    protected String extractionModel() {
        return properties.extractionModel();
    }

    @Override
    protected OpenRouterChatResponse sendChat(OpenRouterChatRequest request) {
        return client.chatCompletion(request);
    }
}

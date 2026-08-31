package uz.taskapp.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uz.taskapp.config.GroqProperties;

@Component
@ConditionalOnProperty(prefix = "voice", name = "provider", havingValue = "groq")
class GroqVoiceModelClient extends AbstractTwoStepVoiceModelClient {
    private final GroqClient client;
    private final GroqProperties properties;

    GroqVoiceModelClient(GroqClient client, GroqProperties properties, ObjectMapper objectMapper) {
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
        return client.transcribe(audio, mimeType);
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

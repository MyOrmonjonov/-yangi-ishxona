package uz.taskapp.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uz.taskapp.config.SelfHostedProperties;

@Component
@ConditionalOnProperty(prefix = "voice", name = "provider", havingValue = "selfhosted")
class SelfHostedVoiceModelClient extends AbstractTwoStepVoiceModelClient {
    private final SelfHostedClient client;
    private final SelfHostedProperties properties;

    SelfHostedVoiceModelClient(SelfHostedClient client, SelfHostedProperties properties, ObjectMapper objectMapper) {
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
        return properties.llmModel();
    }

    @Override
    protected OpenRouterChatResponse sendChat(OpenRouterChatRequest request) {
        return client.chatCompletion(request);
    }
}

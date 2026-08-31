package uz.taskapp.voice;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.GeminiProperties;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(prefix = "voice", name = "provider", havingValue = "gemini", matchIfMissing = true)
class GeminiVoiceModelClient implements VoiceModelClient {
    private final GeminiClient geminiClient;
    private final GeminiProperties geminiProperties;
    private final ObjectMapper objectMapper;

    GeminiVoiceModelClient(GeminiClient geminiClient, GeminiProperties geminiProperties, ObjectMapper objectMapper) {
        this.geminiClient = geminiClient;
        this.geminiProperties = geminiProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean configured() {
        return geminiProperties.configured();
    }

    @Override
    public VoiceDraftPayload transcribeAndExtract(byte[] audio, String mimeType, String promptText) {
        GeminiGenerateContentRequest request = new GeminiGenerateContentRequest(
                List.of(new GeminiContent("user", List.of(
                        new GeminiPart(promptText, null),
                        new GeminiPart(null, new GeminiInlineData(mimeType, Base64.getEncoder().encodeToString(audio)))
                ))),
                new GeminiGenerationConfig("application/json", responseSchema())
        );
        GeminiGenerateContentResponse response = geminiClient.generateContent(request);
        return parsePayload(response);
    }

    @Override
    public VoiceDraftPayload extractFromText(String text, String promptText) {
        String combined = promptText + """


                Vazifa matni (bu allaqachon tayyor - uni qayta yozmasdan, faqat undan foydalanib yuqoridagi
                maydonlarni to'ldiring):
                \"""" + text + "\"";
        GeminiGenerateContentRequest request = new GeminiGenerateContentRequest(
                List.of(new GeminiContent("user", List.of(new GeminiPart(combined, null)))),
                new GeminiGenerationConfig("application/json", responseSchema())
        );
        GeminiGenerateContentResponse response = geminiClient.generateContent(request);
        return parsePayload(response);
    }

    private static Map<String, Object> responseSchema() {
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "transcript", Map.of("type", "STRING"),
                        "title", Map.of("type", "STRING"),
                        "description", Map.of("type", "STRING"),
                        "dueAtIso", Map.of("type", "STRING", "nullable", true),
                        "matchedAssigneeIds", Map.of("type", "ARRAY", "items", Map.of("type", "INTEGER")),
                        "unmatchedAssigneeNames", Map.of("type", "ARRAY", "items", Map.of("type", "STRING")),
                        "matchedGroupId", Map.of("type", "INTEGER", "nullable", true),
                        "unmatchedGroupName", Map.of("type", "STRING", "nullable", true),
                        "reminderMinutes", Map.of("type", "INTEGER", "nullable", true),
                        "noSpeechDetected", Map.of("type", "BOOLEAN")
                ),
                "required", List.of("transcript", "title", "description", "matchedAssigneeIds",
                        "unmatchedAssigneeNames", "noSpeechDetected")
        );
    }

    private VoiceDraftPayload parsePayload(GeminiGenerateContentResponse response) {
        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_DRAFT_PARSE_FAILED", "Javobni tahlil qilib bo'lmadi");
        }
        GeminiCandidate candidate = response.candidates().get(0);
        if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_DRAFT_PARSE_FAILED", "Javobni tahlil qilib bo'lmadi");
        }
        String json = candidate.content().parts().get(0).text();
        try {
            return objectMapper.readValue(json, VoiceDraftPayload.class);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_DRAFT_PARSE_FAILED", "Javobni tahlil qilib bo'lmadi");
        }
    }
}

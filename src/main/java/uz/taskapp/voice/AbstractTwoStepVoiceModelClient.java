package uz.taskapp.voice;

import org.springframework.http.HttpStatus;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uz.taskapp.common.ApiException;

import java.util.List;
import java.util.Map;

/**
 * Shared "extract structured fields from a transcript (audio-derived or plain text) via a
 * separate OpenAI-compatible chat completion" flow, used by any provider whose API is
 * split into a dedicated STT endpoint plus a JSON-schema-capable chat endpoint
 * (OpenRouter, Groq) - as opposed to Gemini, which does both audio+extraction in one
 * multimodal call.
 */
abstract class AbstractTwoStepVoiceModelClient implements VoiceModelClient {
    private final ObjectMapper objectMapper;

    protected AbstractTwoStepVoiceModelClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected abstract String transcribeAudio(byte[] audio, String mimeType);

    protected abstract String extractionModel();

    protected abstract OpenRouterChatResponse sendChat(OpenRouterChatRequest request);

    @Override
    public VoiceDraftPayload transcribeAndExtract(byte[] audio, String mimeType, String promptText) {
        String transcript = transcribeAudio(audio, mimeType);
        if (transcript == null || transcript.isBlank()) {
            return new VoiceDraftPayload("", "", "", null, List.of(), List.of(), null, null, null, true);
        }
        return extractFromText(transcript, promptText);
    }

    @Override
    public VoiceDraftPayload extractFromText(String text, String promptText) {
        if (text == null || text.isBlank()) {
            return new VoiceDraftPayload("", "", "", null, List.of(), List.of(), null, null, null, true);
        }
        ExtractionPayload extracted = extract(text, promptText);
        return new VoiceDraftPayload(
                text,
                extracted.title(),
                extracted.description(),
                extracted.dueAtIso(),
                extracted.matchedAssigneeIds(),
                extracted.unmatchedAssigneeNames(),
                extracted.matchedGroupId(),
                extracted.unmatchedGroupName(),
                extracted.reminderMinutes(),
                false
        );
    }

    private ExtractionPayload extract(String transcript, String promptText) {
        String userContent = promptText + """


                Vazifa matni (bu allaqachon tayyor - uni qayta yozmang, faqat undan foydalanib yuqoridagi
                boshqa maydonlarni to'ldiring; "transcript" maydonini javobingizga qo'shmang):
                \"""" + transcript + "\"";

        OpenRouterChatRequest request = new OpenRouterChatRequest(
                extractionModel(),
                List.of(new OpenRouterChatMessage("user", userContent)),
                new OpenRouterResponseFormat("json_schema",
                        new OpenRouterJsonSchema("voice_draft_extraction", true, extractionSchema()))
        );
        OpenRouterChatResponse response = sendChat(request);
        String json = response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null ? null : response.choices().get(0).message().content();
        if (json == null || json.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_DRAFT_PARSE_FAILED", "Javobni tahlil qilib bo'lmadi");
        }
        try {
            return objectMapper.readValue(json, ExtractionPayload.class);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_DRAFT_PARSE_FAILED", "Javobni tahlil qilib bo'lmadi");
        }
    }

    private static Map<String, Object> extractionSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string"),
                        "description", Map.of("type", "string"),
                        "dueAtIso", Map.of("type", List.of("string", "null")),
                        "matchedAssigneeIds", Map.of("type", "array", "items", Map.of("type", "integer")),
                        "unmatchedAssigneeNames", Map.of("type", "array", "items", Map.of("type", "string")),
                        "matchedGroupId", Map.of("type", List.of("integer", "null")),
                        "unmatchedGroupName", Map.of("type", List.of("string", "null")),
                        "reminderMinutes", Map.of("type", List.of("integer", "null"))
                ),
                "required", List.of("title", "description", "dueAtIso", "matchedAssigneeIds",
                        "unmatchedAssigneeNames", "matchedGroupId", "unmatchedGroupName", "reminderMinutes"),
                "additionalProperties", false
        );
    }

    private record ExtractionPayload(
            String title,
            String description,
            String dueAtIso,
            List<Long> matchedAssigneeIds,
            List<String> unmatchedAssigneeNames,
            Long matchedGroupId,
            String unmatchedGroupName,
            Long reminderMinutes
    ) {
    }
}

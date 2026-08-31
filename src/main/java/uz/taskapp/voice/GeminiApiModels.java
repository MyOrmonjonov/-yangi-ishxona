package uz.taskapp.voice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record GeminiGenerateContentRequest(List<GeminiContent> contents, GeminiGenerationConfig generationConfig) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record GeminiContent(String role, List<GeminiPart> parts) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record GeminiPart(String text, @JsonProperty("inline_data") GeminiInlineData inlineData) {
}

record GeminiInlineData(@JsonProperty("mime_type") String mimeType, String data) {
}

record GeminiGenerationConfig(String responseMimeType, Map<String, Object> responseSchema) {
}

record GeminiGenerateContentResponse(List<GeminiCandidate> candidates) {
}

record GeminiCandidate(GeminiContent content, String finishReason) {
}

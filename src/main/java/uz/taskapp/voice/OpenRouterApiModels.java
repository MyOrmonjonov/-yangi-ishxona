package uz.taskapp.voice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OpenRouterTranscriptionRequest(String model, @JsonProperty("input_audio") OpenRouterInputAudio inputAudio, String language) {
}

record OpenRouterInputAudio(String data, String format) {
}

record OpenRouterTranscriptionResponse(String text) {
}

@JsonInclude(JsonInclude.Include.NON_NULL)
record OpenRouterChatRequest(String model, List<OpenRouterChatMessage> messages,
                              @JsonProperty("response_format") OpenRouterResponseFormat responseFormat) {
}

record OpenRouterChatMessage(String role, String content) {
}

record OpenRouterResponseFormat(String type, @JsonProperty("json_schema") OpenRouterJsonSchema jsonSchema) {
}

record OpenRouterJsonSchema(String name, boolean strict, Map<String, Object> schema) {
}

record OpenRouterChatResponse(List<OpenRouterChoice> choices) {
}

record OpenRouterChoice(OpenRouterChoiceMessage message) {
}

record OpenRouterChoiceMessage(String content) {
}

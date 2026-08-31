package uz.taskapp.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.GroqProperties;

@Component
class GroqClient {
    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);

    private final GroqProperties properties;
    private final RestClient restClient;

    GroqClient(GroqProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) properties.requestTimeout().toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    String transcribe(byte[] audio, String mimeType) {
        String filename = "voice." + AudioFormats.extensionFor(mimeType);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", properties.transcriptionModel());
        form.add("language", "uz");
        form.add("response_format", "json");
        form.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        try {
            OpenRouterTranscriptionResponse response = restClient.post()
                    .uri("/audio/transcriptions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(OpenRouterTranscriptionResponse.class);
            return response == null ? null : response.text();
        } catch (RestClientResponseException exception) {
            log.warn("Groq transcription xatosi: status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        } catch (ResourceAccessException exception) {
            log.warn("Groq API'ga ulanishda xatolik: {}", exception.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        }
    }

    OpenRouterChatResponse chatCompletion(OpenRouterChatRequest request) {
        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(OpenRouterChatResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Groq chat completion xatosi: status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        } catch (ResourceAccessException exception) {
            log.warn("Groq API'ga ulanishda xatolik: {}", exception.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        }
    }
}

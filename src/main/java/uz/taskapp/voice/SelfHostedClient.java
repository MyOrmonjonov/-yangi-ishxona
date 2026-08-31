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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.SelfHostedProperties;

/**
 * Talks to our own self-hosted STT (faster-whisper-server) and extraction LLM (llama.cpp server),
 * both OpenAI-API-compatible - modeled directly on {@link GroqClient}, since the request/response
 * shapes are identical. Two base URLs since the two models run as separate containers/services.
 */
@Component
class SelfHostedClient {
    private static final Logger log = LoggerFactory.getLogger(SelfHostedClient.class);

    private final SelfHostedProperties properties;
    private final RestClient sttClient;
    private final RestClient llmClient;

    SelfHostedClient(SelfHostedProperties properties) {
        this.properties = properties;
        int timeoutMillis = (int) properties.requestTimeout().toMillis();
        this.sttClient = restClient(properties.sttBaseUrl(), timeoutMillis);
        this.llmClient = restClient(properties.llmBaseUrl(), timeoutMillis);
    }

    private static RestClient restClient(String baseUrl, int timeoutMillis) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return RestClient.builder()
                .baseUrl(baseUrl == null ? "" : baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    String transcribe(byte[] audio, String mimeType) {
        String filename = "voice." + AudioFormats.extensionFor(mimeType);
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("model", properties.sttModel());
        form.add("language", "uz");
        form.add("response_format", "json");
        form.add("file", new ByteArrayResource(audio) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        try {
            RestClient.RequestBodySpec request = sttClient.post()
                    .uri("/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA);
            if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                request = request.header("Authorization", "Bearer " + properties.apiKey());
            }
            OpenRouterTranscriptionResponse response = request
                    .body(form)
                    .retrieve()
                    .body(OpenRouterTranscriptionResponse.class);
            return response == null ? null : response.text();
        } catch (RestClientResponseException exception) {
            log.warn("O'z serverimizdagi STT xatosi: status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        } catch (ResourceAccessException exception) {
            log.warn("O'z serverimizdagi STT'ga ulanishda xatolik: {}", exception.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        }
    }

    OpenRouterChatResponse chatCompletion(OpenRouterChatRequest request) {
        try {
            RestClient.RequestBodySpec spec = llmClient.post().uri("/v1/chat/completions");
            if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
                spec = spec.header("Authorization", "Bearer " + properties.apiKey());
            }
            return spec
                    .body(request)
                    .retrieve()
                    .body(OpenRouterChatResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("O'z serverimizdagi LLM xatosi: status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        } catch (ResourceAccessException exception) {
            log.warn("O'z serverimizdagi LLM'ga ulanishda xatolik: {}", exception.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        }
    }
}

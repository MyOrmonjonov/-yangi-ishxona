package uz.taskapp.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.OpenRouterProperties;

@Component
class OpenRouterClient {
    private static final Logger log = LoggerFactory.getLogger(OpenRouterClient.class);

    private final OpenRouterProperties properties;
    private final RestClient restClient;

    OpenRouterClient(OpenRouterProperties properties) {
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

    OpenRouterTranscriptionResponse transcribe(OpenRouterTranscriptionRequest request) {
        return post("/audio/transcriptions", request, OpenRouterTranscriptionResponse.class);
    }

    OpenRouterChatResponse chatCompletion(OpenRouterChatRequest request) {
        return post("/chat/completions", request, OpenRouterChatResponse.class);
    }

    private <T> T post(String uri, Object body, Class<T> responseType) {
        try {
            return restClient.post()
                    .uri(uri)
                    .header("Authorization", "Bearer " + properties.apiKey())
                    .body(body)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException exception) {
            log.warn("OpenRouter API xatosi: uri={} status={} body={}", uri, exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED",
                    "Ovozni tahlil qilishda xatolik yuz berdi");
        } catch (ResourceAccessException exception) {
            log.warn("OpenRouter API'ga ulanishda xatolik: uri={} message={}", uri, exception.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED",
                    "Ovozni tahlil qilishda xatolik yuz berdi");
        }
    }
}

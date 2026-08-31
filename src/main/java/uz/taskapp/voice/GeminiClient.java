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
import uz.taskapp.config.GeminiProperties;

@Component
class GeminiClient {
    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final GeminiProperties properties;
    private final RestClient restClient;

    GeminiClient(GeminiProperties properties) {
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

    GeminiGenerateContentResponse generateContent(GeminiGenerateContentRequest request) {
        try {
            return restClient.post()
                    .uri("/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(GeminiGenerateContentResponse.class);
        } catch (RestClientResponseException exception) {
            log.warn("Gemini API xatosi: status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED",
                    "Ovozni tahlil qilishda xatolik yuz berdi");
        } catch (ResourceAccessException exception) {
            log.warn("Gemini API'ga ulanishda xatolik: {}", exception.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED",
                    "Ovozni tahlil qilishda xatolik yuz berdi");
        }
    }
}

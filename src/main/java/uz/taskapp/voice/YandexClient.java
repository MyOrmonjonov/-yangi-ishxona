package uz.taskapp.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.YandexProperties;

@Component
class YandexClient {
    private static final Logger log = LoggerFactory.getLogger(YandexClient.class);
    /** Our WAV frames are always produced by the frontend's own minimal encoder: a fixed 44-byte header, no extra chunks. */
    private static final int WAV_HEADER_BYTES = 44;
    private static final int SAMPLE_RATE_HZ = 16000;

    private final YandexProperties properties;
    private final RestClient restClient;

    YandexClient(YandexProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) properties.requestTimeout().toMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    String recognize(byte[] audio, String mimeType) {
        boolean isOgg = mimeType != null && mimeType.toLowerCase().contains("ogg");
        byte[] body = isOgg || audio.length <= WAV_HEADER_BYTES
                ? audio
                : java.util.Arrays.copyOfRange(audio, WAV_HEADER_BYTES, audio.length);
        String uri = isOgg
                ? properties.baseUrl() + "?lang={lang}&folderId={folderId}&format=oggopus"
                : properties.baseUrl() + "?lang={lang}&folderId={folderId}&format=lpcm&sampleRateHertz={rate}";
        try {
            YandexRecognizeResponse response = (isOgg
                    ? restClient.post().uri(uri, properties.language(), properties.folderId())
                    : restClient.post().uri(uri, properties.language(), properties.folderId(), SAMPLE_RATE_HZ))
                    .header("Authorization", "Api-Key " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(body)
                    .retrieve()
                    .body(YandexRecognizeResponse.class);
            return response == null ? null : response.result();
        } catch (RestClientResponseException exception) {
            log.warn("Yandex SpeechKit xatosi: status={} body={}", exception.getStatusCode(), exception.getResponseBodyAsString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        } catch (ResourceAccessException exception) {
            log.warn("Yandex SpeechKit'ga ulanishda xatolik: {}", exception.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VOICE_TRANSCRIBE_FAILED", "Ovozni tahlil qilishda xatolik yuz berdi");
        }
    }
}

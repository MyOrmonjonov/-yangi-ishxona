package org.example.yangi_ishxona.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.yangi_ishxona.bot.WebhookSecret;
import org.example.yangi_ishxona.bot.YangiIshxonaBot;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Receives Telegram updates via webhook (used instead of long polling when
 * {@code telegram.bot.mode=webhook} - see {@link org.example.yangi_ishxona.config.BotConfig}).
 * Free-tier hosts like Render put idle services to sleep; an inbound webhook POST counts as
 * traffic and wakes the service, whereas a long-polling connection would just die silently.
 *
 * <p>The body is bound as a raw String and deserialized manually with a plain (Jackson 2.x)
 * {@link ObjectMapper} instead of {@code @RequestBody Update} - Spring Boot 4's default HTTP
 * message converter uses Jackson 3.x ({@code tools.jackson.databind}), which does not recognise
 * the Jackson-2.x {@code @JsonDeserialize(builder=...)} annotations that telegrambots-meta's
 * Lombok {@code @Jacksonized} classes (e.g. {@link org.telegram.telegrambots.meta.api.objects.User})
 * generate, and fails with "no Creators, like default constructor, exist". The library's own
 * long-polling path avoids this because it deserializes updates with its own Jackson-2.x
 * ObjectMapper internally - this controller does the same.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TelegramWebhookController {

    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final YangiIshxonaBot bot;
    private final WebhookSecret webhookSecret;
    private final ObjectMapper telegramObjectMapper = new ObjectMapper();

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> onUpdate(@RequestHeader(value = SECRET_HEADER, required = false) String secretHeader,
                                          @RequestBody String rawBody) {
        if (!webhookSecret.get().equals(secretHeader)) {
            return ResponseEntity.status(403).build();
        }
        try {
            Update update = telegramObjectMapper.readValue(rawBody, Update.class);
            bot.consume(update);
        } catch (Exception e) {
            log.error("Не удалось разобрать входящий Telegram update", e);
        }
        return ResponseEntity.ok().build();
    }
}

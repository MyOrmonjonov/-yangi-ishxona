package org.example.yangi_ishxona.web;

import lombok.RequiredArgsConstructor;
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
 */
@RestController
@RequiredArgsConstructor
public class TelegramWebhookController {

    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final YangiIshxonaBot bot;
    private final WebhookSecret webhookSecret;

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> onUpdate(@RequestHeader(value = SECRET_HEADER, required = false) String secretHeader,
                                          @RequestBody Update update) {
        if (!webhookSecret.get().equals(secretHeader)) {
            return ResponseEntity.status(403).build();
        }
        bot.consume(update);
        return ResponseEntity.ok().build();
    }
}

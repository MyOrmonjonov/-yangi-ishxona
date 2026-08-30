package org.example.yangi_ishxona.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Shared secret verified against Telegram's {@code X-Telegram-Bot-Api-Secret-Token} header
 * on every webhook call (webhook mode only - see {@link org.example.yangi_ishxona.config.BotConfig}).
 * Auto-generated per process start if not explicitly configured.
 */
@Component
public class WebhookSecret {

    private final String value;

    public WebhookSecret(@Value("${telegram.bot.webhook-secret:}") String configured) {
        this.value = (configured == null || configured.isBlank()) ? UUID.randomUUID().toString() : configured;
    }

    public String get() {
        return value;
    }
}

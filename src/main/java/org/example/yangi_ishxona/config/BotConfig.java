package org.example.yangi_ishxona.config;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.yangi_ishxona.bot.WebhookSecret;
import org.example.yangi_ishxona.bot.YangiIshxonaBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotConfig {

    private final YangiIshxonaBot bot;
    private final WebhookSecret webhookSecret;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.mode}")
    private String mode;

    @Value("${telegram.bot.public-url}")
    private String publicUrl;

    @Value("${app.miniapp.base-url}")
    private String miniAppBaseUrl;

    private TelegramBotsLongPollingApplication longPollingApplication;

    @EventListener(ApplicationReadyEvent.class)
    public void registerBot() {
        if (botToken == null || botToken.isBlank()) {
            log.warn("TELEGRAM_BOT_TOKEN не задан - бот не запущен. Укажите токен в .env (см. README).");
            return;
        }
        if ("webhook".equalsIgnoreCase(mode)) {
            registerWebhook();
        } else {
            registerLongPolling();
        }
        setGlobalMenuButton();
    }

    private void registerLongPolling() {
        try {
            longPollingApplication = new TelegramBotsLongPollingApplication();
            longPollingApplication.registerBot(botToken, bot);
            log.info("Telegram-бот зарегистрирован (long polling).");
        } catch (TelegramApiException e) {
            log.error("Не удалось зарегистрировать Telegram-бота (long polling)", e);
        }
    }

    /**
     * Free hosts (Render free tier) put idle services to sleep; long polling would just die
     * silently, whereas a webhook POST counts as inbound traffic and wakes the service.
     */
    private void registerWebhook() {
        if (publicUrl == null || publicUrl.isBlank()) {
            log.warn("telegram.bot.mode=webhook, но PUBLIC_BASE_URL/RENDER_EXTERNAL_URL не задан - бот не запущен.");
            return;
        }
        String webhookUrl = publicUrl.replaceAll("/+$", "") + "/telegram/webhook";
        try {
            OkHttpTelegramClient client = new OkHttpTelegramClient(botToken);
            client.execute(SetWebhook.builder()
                    .url(webhookUrl)
                    .secretToken(webhookSecret.get())
                    .dropPendingUpdates(true)
                    .build());
            log.info("Telegram-бот зарегистрирован (webhook): {}", webhookUrl);
        } catch (Exception e) {
            log.error("Не удалось установить Telegram webhook {}", webhookUrl, e);
        }
    }

    private void setGlobalMenuButton() {
        if (miniAppBaseUrl == null || !miniAppBaseUrl.startsWith("https://")) {
            return;
        }
        try {
            OkHttpTelegramClient client = new OkHttpTelegramClient(botToken);
            client.execute(SetChatMenuButton.builder()
                    .menuButton(MenuButtonWebApp.builder()
                            .text("📱 App")
                            .webAppInfo(WebAppInfo.builder().url(miniAppBaseUrl).build())
                            .build())
                    .build());
            log.info("Глобальная кнопка меню (Mini App) установлена.");
        } catch (Exception e) {
            log.warn("Не удалось установить кнопку меню Mini App: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() throws Exception {
        if (longPollingApplication != null) {
            longPollingApplication.close();
        }
    }
}

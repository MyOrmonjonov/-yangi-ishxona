package org.example.yangi_ishxona.config;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.yangi_ishxona.bot.YangiIshxonaBot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.api.methods.menubutton.SetChatMenuButton;
import org.telegram.telegrambots.meta.api.objects.menubutton.MenuButtonWebApp;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class BotConfig {

    private final YangiIshxonaBot bot;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${app.miniapp.base-url}")
    private String miniAppBaseUrl;

    private TelegramBotsLongPollingApplication application;

    @EventListener(ApplicationReadyEvent.class)
    public void registerBot() {
        if (botToken == null || botToken.isBlank()) {
            log.warn("TELEGRAM_BOT_TOKEN не задан - бот не запущен. Укажите токен в .env (см. README).");
            return;
        }
        try {
            application = new TelegramBotsLongPollingApplication();
            application.registerBot(botToken, bot);
            log.info("Telegram-бот зарегистрирован и слушает обновления (long polling).");
        } catch (TelegramApiException e) {
            log.error("Не удалось зарегистрировать Telegram-бота", e);
            return;
        }
        setGlobalMenuButton();
    }

    /**
     * Sets the persistent "Menu" button (next to the message input) to open the Mini App.
     * Telegram requires an HTTPS url for web_app buttons, so this silently fails on local
     * http:// dev runs - only bites in production once MINIAPP_BASE_URL is https (Railway).
     */
    private void setGlobalMenuButton() {
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
            log.warn("Не удалось установить кнопку меню Mini App (ожидаемо для http:// в локальной разработке): {}",
                    e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() throws Exception {
        if (application != null) {
            application.close();
        }
    }
}

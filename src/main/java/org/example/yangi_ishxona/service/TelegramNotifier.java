package org.example.yangi_ishxona.service;

import java.util.List;

/**
 * Outbound Telegram messaging, implemented by the bot module. Kept as an interface
 * in the service package so scheduler/service code never depends on the Telegram API types.
 */
public interface TelegramNotifier {
    void sendMessage(Long telegramUserId, String text);

    void sendMessageWithButtons(Long telegramUserId, String text, List<List<NotifyButton>> buttonRows);
}

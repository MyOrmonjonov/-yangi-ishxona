package uz.taskapp.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.response.GetFileResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Downloads a Telegram-hosted file (e.g. a voice message) into memory via the Bot API. */
@Component
class TelegramFileDownloader {
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    byte[] download(TelegramBot bot, String fileId) {
        GetFileResponse response = bot.execute(new GetFile(fileId));
        if (response == null || !response.isOk() || response.file() == null) {
            throw new IllegalStateException("Telegram fayl ma'lumotini olib bo'lmadi: " + fileId);
        }
        String url = bot.getFullFilePath(response.file());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<byte[]> fileResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (fileResponse.statusCode() < 200 || fileResponse.statusCode() >= 300) {
                throw new IllegalStateException("Telegram faylini yuklab bo'lmadi: status=" + fileResponse.statusCode());
            }
            return fileResponse.body();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Telegram faylini yuklab bo'lmadi", exception);
        }
    }
}

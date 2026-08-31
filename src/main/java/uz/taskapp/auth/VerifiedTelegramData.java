package uz.taskapp.auth;

import java.time.Instant;

public record VerifiedTelegramData(
        TelegramUserData user,
        Instant authenticatedAt,
        String startParam
) {
}

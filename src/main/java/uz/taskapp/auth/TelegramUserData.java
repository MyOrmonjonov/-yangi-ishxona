package uz.taskapp.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramUserData(
        long id,
        @JsonProperty("first_name") String firstName,
        @JsonProperty("last_name") String lastName,
        String username,
        @JsonProperty("photo_url") String photoUrl,
        @JsonProperty("language_code") String languageCode
) {
}

package uz.taskapp.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.taskapp.config.TelegramProperties;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final TelegramProperties telegramProperties;

    public AuthController(AuthService authService, TelegramProperties telegramProperties) {
        this.authService = authService;
        this.telegramProperties = telegramProperties;
    }

    /** Public config the /login page needs before the user has authenticated. */
    @GetMapping("/config")
    ConfigResponse config() {
        return new ConfigResponse(telegramProperties.botUsername());
    }

    @PostMapping("/telegram")
    AuthService.AuthResponse telegram(@Valid @RequestBody TelegramAuthRequest request) {
        return authService.authenticate(request.initData());
    }

    /** Browser login callback from the Telegram Login Widget - lets the dashboard open outside Telegram. */
    @PostMapping("/telegram-login")
    AuthService.AuthResponse telegramLogin(@RequestBody TelegramLoginRequest request) {
        Map<String, String> fields = new HashMap<>();
        putIfPresent(fields, "id", request.id());
        putIfPresent(fields, "first_name", request.firstName());
        putIfPresent(fields, "last_name", request.lastName());
        putIfPresent(fields, "username", request.username());
        putIfPresent(fields, "photo_url", request.photoUrl());
        putIfPresent(fields, "auth_date", request.authDate());
        putIfPresent(fields, "hash", request.hash());
        return authService.authenticateViaLoginWidget(fields);
    }

    private static void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null) fields.put(key, value);
    }

    public record TelegramAuthRequest(@NotBlank String initData) {}

    public record TelegramLoginRequest(String id, String firstName, String lastName, String username,
                                       String photoUrl, String authDate, String hash) {}

    public record ConfigResponse(String botUsername) {}
}

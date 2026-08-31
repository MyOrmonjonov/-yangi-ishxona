package uz.taskapp.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/telegram")
    AuthService.AuthResponse telegram(@Valid @RequestBody TelegramAuthRequest request) {
        return authService.authenticate(request.initData());
    }

    public record TelegramAuthRequest(@NotBlank String initData) {}
}

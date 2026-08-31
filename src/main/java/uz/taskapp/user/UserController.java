package uz.taskapp.user;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.taskapp.auth.AuthInterceptor;
import uz.taskapp.common.ApiException;

import java.util.Set;

@RestController
@RequestMapping("/api/users/me")
public class UserController {
    private static final Set<String> LANGUAGES = Set.of("uz", "ru", "en");
    private static final Set<String> THEMES = Set.of("system", "dark", "light");

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/preferences")
    UserPreferencesResponse preferences(HttpServletRequest request) {
        return toResponse(currentUser(request));
    }

    @PatchMapping("/preferences")
    UserPreferencesResponse updatePreferences(HttpServletRequest request,
                                              @RequestBody UpdateUserPreferencesRequest body) {
        if (body.uiLanguage() != null && !LANGUAGES.contains(body.uiLanguage())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "LANGUAGE_INVALID", "Til noto'g'ri");
        }
        if (body.theme() != null && !THEMES.contains(body.theme())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "THEME_INVALID", "Ko'rinish noto'g'ri");
        }
        UserEntity user = currentUser(request);
        user.updatePreferences(body.uiLanguage(), body.theme(), body.remindersEnabled());
        return toResponse(userRepository.save(user));
    }

    private UserEntity currentUser(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND", "Foydalanuvchi topilmadi"));
    }

    private UserPreferencesResponse toResponse(UserEntity user) {
        return new UserPreferencesResponse(user.getUiLanguage(), user.getTheme(), user.isRemindersEnabled());
    }

    public record UserPreferencesResponse(String uiLanguage, String theme, boolean remindersEnabled) {
    }
}
package org.example.yangi_ishxona.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.AppUser;
import org.example.yangi_ishxona.service.Messages;
import org.example.yangi_ishxona.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final TelegramLoginService telegramLoginService;
    private final UserService userService;
    private final LocaleResolver localeResolver;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @GetMapping("/login")
    public String login(Model model) {
        // Defensive trim: a stray leading/trailing space in TELEGRAM_BOT_USERNAME (easy to
        // introduce when pasting into a hosting dashboard's env var field) makes the Telegram
        // Login Widget reject it outright with "Username invalid" and never render the button.
        model.addAttribute("botUsername", botUsername == null ? "" : botUsername.trim());
        return "login";
    }

    @GetMapping("/auth/telegram/callback")
    public String callback(@RequestParam Map<String, String> params, HttpSession session,
                            HttpServletRequest request, HttpServletResponse response,
                            RedirectAttributes redirectAttributes) {
        if (!telegramLoginService.verify(params)) {
            redirectAttributes.addFlashAttribute("error", "Не удалось подтвердить вход через Telegram.");
            return "redirect:/login";
        }
        Long telegramUserId = Long.parseLong(params.get("id"));
        Optional<AppUser> userOpt = userService.findByTelegramId(telegramUserId);
        if (userOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    "Сначала напишите боту /start в Telegram, чтобы зарегистрироваться.");
            return "redirect:/login";
        }
        AppUser user = userOpt.get();
        session.setAttribute("userId", user.getId());
        localeResolver.setLocale(request, response, Messages.localeFor(user.getLanguage()));
        return "redirect:/projects";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}

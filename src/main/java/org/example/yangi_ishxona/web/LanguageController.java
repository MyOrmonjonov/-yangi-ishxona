package org.example.yangi_ishxona.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.Language;
import org.example.yangi_ishxona.service.Messages;
import org.example.yangi_ishxona.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.LocaleResolver;

@Controller
@RequiredArgsConstructor
public class LanguageController {

    private final LocaleResolver localeResolver;
    private final UserService userService;

    @GetMapping("/language/{code}")
    public String change(@PathVariable String code, HttpServletRequest request, HttpServletResponse response,
                          HttpSession session) {
        Language language;
        try {
            language = Language.valueOf(code.toUpperCase());
        } catch (IllegalArgumentException e) {
            language = Language.RU;
        }
        Language finalLanguage = language;
        Object userId = session.getAttribute("userId");
        if (userId instanceof Long id) {
            userService.findById(id).ifPresent(u -> userService.updateLanguage(u, finalLanguage));
        }
        localeResolver.setLocale(request, response, Messages.localeFor(language));
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null && !referer.isBlank() ? referer : "/projects");
    }
}

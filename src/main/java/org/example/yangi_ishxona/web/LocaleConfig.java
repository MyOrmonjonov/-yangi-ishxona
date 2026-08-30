package org.example.yangi_ishxona.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.util.Locale;

/**
 * Drives Thymeleaf's #{...} messages (see i18n/messages_*.properties). The bean MUST be
 * named "localeResolver" - Spring MVC looks it up by that exact name.
 */
@Configuration
public class LocaleConfig {

    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("app.lang");
        resolver.setDefaultLocale(Locale.forLanguageTag("ru"));
        resolver.setCookieMaxAge(java.time.Duration.ofDays(365));
        return resolver;
    }
}

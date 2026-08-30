package org.example.yangi_ishxona.service;

import org.example.yangi_ishxona.domain.Language;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Single translation source shared by the bot, the web dashboard and the Mini App API
 * (ТЗ 4 requires Russian; the user additionally asked for Uzbek + English). Backed by
 * {@code i18n/messages_{uz,ru,en}.properties} - loaded fresh per lookup via
 * {@link ResourceBundle#getBundle}, which the JVM caches internally.
 */
@Component
public class Messages {

    private static final String BASENAME = "i18n.messages";
    private static final Map<Language, Locale> LOCALES = new EnumMap<>(Language.class);

    static {
        LOCALES.put(Language.UZ, Locale.forLanguageTag("uz"));
        LOCALES.put(Language.RU, Locale.forLanguageTag("ru"));
        LOCALES.put(Language.EN, Locale.ENGLISH);
    }

    public String t(Language language, String key, Object... args) {
        Language lang = language == null ? Language.RU : language;
        ResourceBundle bundle = ResourceBundle.getBundle(BASENAME, LOCALES.get(lang));
        String pattern = bundle.containsKey(key) ? bundle.getString(key) : key;
        for (int i = 0; i < args.length; i++) {
            pattern = pattern.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return pattern;
    }

    public static Locale localeFor(Language language) {
        return LOCALES.get(language == null ? Language.RU : language);
    }
}

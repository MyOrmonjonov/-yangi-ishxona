package org.example.yangi_ishxona.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses deadline input per ТЗ 3.3: accepts {@code dd.MM.yyyy} and the relative
 * expressions "сегодня", "завтра", "через N дней" (and their casual/Uzbek variants).
 */
public final class DeadlineParser {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final Pattern RELATIVE_DAYS = Pattern.compile("(?i)через\\s+(\\d+)\\s+д");

    private DeadlineParser() {
    }

    public static LocalDate parse(String rawInput) {
        if (rawInput == null) {
            return null;
        }
        String input = rawInput.trim().toLowerCase();
        if (input.isEmpty()) {
            return null;
        }
        switch (input) {
            case "сегодня", "bugun":
                return LocalDate.now();
            case "завтра", "ertaga":
                return LocalDate.now().plusDays(1);
            case "послезавтра":
                return LocalDate.now().plusDays(2);
            default:
                break;
        }
        Matcher matcher = RELATIVE_DAYS.matcher(input);
        if (matcher.find()) {
            return LocalDate.now().plusDays(Long.parseLong(matcher.group(1)));
        }
        try {
            return LocalDate.parse(input, DATE_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public static String format(LocalDate date) {
        return date == null ? "-" : date.format(DATE_FORMAT);
    }
}

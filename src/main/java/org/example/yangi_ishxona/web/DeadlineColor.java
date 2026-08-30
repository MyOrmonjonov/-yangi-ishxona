package org.example.yangi_ishxona.web;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Colour coding per ТЗ 3.7: зелёный (в норме), жёлтый (осталось ≤3 дней),
 * красный (осталось ≤24 часов, i.e. due today, or overdue). Finished/cancelled
 * items are shown neutral regardless of date.
 */
final class DeadlineColor {

    private DeadlineColor() {
    }

    static String cssClass(LocalDate deadline, boolean finished) {
        if (finished) {
            return "neutral";
        }
        long daysLeft = ChronoUnit.DAYS.between(LocalDate.now(), deadline);
        if (daysLeft <= 0) {
            return "red";
        }
        if (daysLeft <= 3) {
            return "yellow";
        }
        return "green";
    }
}

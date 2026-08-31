package uz.taskapp.common;

import java.time.Instant;
import java.util.Map;

public record ApiError(
        String code,
        String message,
        Map<String, String> fields,
        Instant timestamp
) {
}

package uz.taskapp.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChecklistItemRequest(
        @NotBlank @Size(max = 500) String text,
        Boolean done
) {
}

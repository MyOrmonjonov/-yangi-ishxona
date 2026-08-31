package uz.taskapp.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;

public record UpdateTaskRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 10000) String description,
        @NotNull TaskStatus status,
        TaskPriority priority,
        TaskVisibility visibility,
        Long groupId,
        Long topicId,
        List<Long> assigneeIds,
        Long authorId,
        Instant dueAt,
        Boolean dueAtProvided,
        Integer reminderMinutes,
        Boolean reminderProvided,
        @Valid List<CreateChecklistItemRequest> checklist
) {
}

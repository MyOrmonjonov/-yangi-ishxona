package uz.taskapp.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;

public record CreateTaskRequest(
        @NotNull Long workspaceId,
        Long groupId,
        Long topicId,
        @NotBlank @Size(max = 300) String title,
        @Size(max = 10000) String description,
        TaskStatus status,
        TaskPriority priority,
        TaskVisibility visibility,
        Instant dueAt,
        Integer reminderMinutes,
        List<Long> assigneeIds,
        @Valid List<CreateChecklistItemRequest> checklist,
        Long voiceDraftId
) {
}

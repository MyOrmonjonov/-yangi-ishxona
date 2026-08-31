package uz.taskapp.voice;

import java.time.Instant;
import java.util.List;

public record VoiceDraftResponse(
        String transcript,
        String title,
        String description,
        Instant dueAt,
        List<Long> assigneeIds,
        List<String> unmatchedAssigneeNames,
        Long groupId,
        String unmatchedGroupName,
        Long reminderMinutes,
        Long topicId
) {
}

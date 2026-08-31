package uz.taskapp.voice;

import java.util.List;

record VoiceDraftPayload(
        String transcript,
        String title,
        String description,
        String dueAtIso,
        List<Long> matchedAssigneeIds,
        List<String> unmatchedAssigneeNames,
        Long matchedGroupId,
        String unmatchedGroupName,
        Long reminderMinutes,
        boolean noSpeechDetected
) {
}

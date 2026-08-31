package uz.taskapp.voice;

public record PendingVoiceDraftResponse(Long workspaceId, VoiceDraftResponse draft, boolean hasAudio) {
}

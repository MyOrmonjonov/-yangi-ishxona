package uz.taskapp.voice;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds voice-extracted task drafts awaiting the user's review - e.g. one created from a
 * Telegram voice message, waiting for the user to open the Mini App and confirm it there.
 * In-memory only: drafts are short-lived (minutes) and losing them on a restart is fine, the
 * user just re-records.
 */
@Component
class PendingVoiceDraftStore {
    private static final Duration TTL = Duration.ofMinutes(30);

    private final AtomicLong idGenerator = new AtomicLong();
    private final Map<Long, PendingDraft> drafts = new ConcurrentHashMap<>();

    long store(Long workspaceId, Long userId, VoiceDraftResponse draft, byte[] audio, String audioMimeType) {
        long id = idGenerator.incrementAndGet();
        drafts.put(id, new PendingDraft(workspaceId, userId, draft, audio, audioMimeType, Instant.now(), null, null));
        return id;
    }

    PendingDraft get(long id) {
        return drafts.get(id);
    }

    void discard(long id) {
        drafts.remove(id);
    }

    /**
     * Records where the draft's preview message was posted in Telegram, so it can be deleted
     * once the user actually creates the task from this draft in the Mini App - otherwise the
     * stale "Vazifa drafti tayyor" preview (with its now-meaningless buttons) lingers in the chat.
     */
    void attachPreviewMessage(long id, long chatId, int messageId) {
        drafts.computeIfPresent(id, (key, existing) -> new PendingDraft(existing.workspaceId(), existing.userId(),
                existing.draft(), existing.audio(), existing.audioMimeType(), existing.createdAt(), chatId, messageId));
    }

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    void purgeExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        drafts.values().removeIf(pending -> pending.createdAt().isBefore(cutoff));
    }

    record PendingDraft(Long workspaceId, Long userId, VoiceDraftResponse draft, byte[] audio, String audioMimeType,
                         Instant createdAt, Long previewChatId, Integer previewMessageId) {
    }
}

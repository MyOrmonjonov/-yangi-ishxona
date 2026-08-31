package uz.taskapp.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uz.taskapp.common.ApiException;
import uz.taskapp.group.GroupRepository;
import uz.taskapp.user.UserEntity;
import uz.taskapp.user.UserRepository;
import uz.taskapp.workspace.WorkspaceMemberEntity;
import uz.taskapp.workspace.WorkspaceMemberRepository;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;

@Service
public class VoiceDraftService {
    private static final Logger log = LoggerFactory.getLogger(VoiceDraftService.class);
    /** Mirrors TaskService.APP_ZONE / StatisticsService.APP_ZONE (both private to their classes). */
    private static final ZoneId VOICE_ZONE = ZoneId.of("Asia/Tashkent");
    private static final long MAX_AUDIO_BYTES = 15L * 1024 * 1024;

    private final VoiceModelClient voiceModelClient;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final ObjectMapper objectMapper;
    private final PendingVoiceDraftStore pendingVoiceDraftStore;

    public VoiceDraftService(VoiceModelClient voiceModelClient,
                              WorkspaceMemberRepository workspaceMemberRepository, UserRepository userRepository,
                              GroupRepository groupRepository, ObjectMapper objectMapper,
                              PendingVoiceDraftStore pendingVoiceDraftStore) {
        this.voiceModelClient = voiceModelClient;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.objectMapper = objectMapper;
        this.pendingVoiceDraftStore = pendingVoiceDraftStore;
    }

    public boolean available() {
        return voiceModelClient.configured();
    }

    /**
     * Stores a draft (e.g. extracted from a Telegram voice message) for later pickup by the Mini
     * App, along with the original recording so it can be attached to the task once created.
     */
    public long storePendingDraft(Long workspaceId, Long userId, VoiceDraftResponse draft, byte[] audio, String audioMimeType) {
        return pendingVoiceDraftStore.store(workspaceId, userId, draft, audio, audioMimeType);
    }

    public void discardPendingDraft(long id) {
        pendingVoiceDraftStore.discard(id);
    }

    /** See {@link PendingVoiceDraftStore#attachPreviewMessage}. */
    public void attachPreviewMessage(long draftId, long chatId, int messageId) {
        pendingVoiceDraftStore.attachPreviewMessage(draftId, chatId, messageId);
    }

    /**
     * Discards the draft and returns where its Telegram preview message lives, if it was ever
     * sent to a chat - called once the user actually creates the task from this draft, so the
     * caller can delete the now-redundant preview message.
     */
    public PreviewMessageLocation discardAndGetPreviewMessage(long draftId) {
        PendingVoiceDraftStore.PendingDraft pending = pendingVoiceDraftStore.get(draftId);
        pendingVoiceDraftStore.discard(draftId);
        if (pending == null || pending.previewChatId() == null || pending.previewMessageId() == null) {
            return null;
        }
        return new PreviewMessageLocation(pending.previewChatId(), pending.previewMessageId());
    }

    public record PreviewMessageLocation(Long chatId, Integer messageId) {
    }

    public PendingVoiceDraftResponse getPendingDraft(long id, Long userId) {
        PendingVoiceDraftStore.PendingDraft pending = requirePendingDraft(id, userId);
        return new PendingVoiceDraftResponse(pending.workspaceId(), pending.draft(),
                pending.audio() != null && pending.audio().length > 0);
    }

    public PendingVoiceAudio getPendingAudio(long id, Long userId) {
        PendingVoiceDraftStore.PendingDraft pending = requirePendingDraft(id, userId);
        if (pending.audio() == null || pending.audio().length == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "VOICE_DRAFT_NOT_FOUND", "Ovozli yozuv topilmadi");
        }
        return new PendingVoiceAudio(pending.audio(), pending.audioMimeType());
    }

    private PendingVoiceDraftStore.PendingDraft requirePendingDraft(long id, Long userId) {
        PendingVoiceDraftStore.PendingDraft pending = pendingVoiceDraftStore.get(id);
        if (pending == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "VOICE_DRAFT_NOT_FOUND", "Taklif topilmadi yoki eskirgan");
        }
        if (!pending.userId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WORKSPACE_ACCESS_DENIED", "Bu taklifga kirish huquqingiz yo'q");
        }
        return pending;
    }

    public record PendingVoiceAudio(byte[] bytes, String mimeType) {
    }

    public VoiceDraftResponse createDraft(Long userId, Long workspaceId, MultipartFile audio) {
        if (!voiceModelClient.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_NOT_CONFIGURED",
                    "Ovozli yordamchi hozircha sozlanmagan");
        }
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(workspaceId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WORKSPACE_ACCESS_DENIED", "Ish maydoniga kirishga ruxsat yo'q");
        }
        validateAudio(audio);

        byte[] audioBytes = readBytes(audio);
        return transcribeAndFinalize(workspaceId, audioBytes, "audio/wav");
    }

    /**
     * Same extraction pipeline as {@link #createDraft}, but for audio bytes obtained outside an
     * HTTP multipart upload (e.g. a Telegram voice message) - skips the audio/wav-only
     * multipart validation and passes the audio's real MIME type through to the model client.
     */
    public VoiceDraftResponse createDraftFromAudio(Long userId, Long workspaceId, byte[] audioBytes, String mimeType) {
        if (!voiceModelClient.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_NOT_CONFIGURED",
                    "Ovozli yordamchi hozircha sozlanmagan");
        }
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(workspaceId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WORKSPACE_ACCESS_DENIED", "Ish maydoniga kirishga ruxsat yo'q");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VOICE_AUDIO_INVALID", "Ovozli fayl topilmadi");
        }
        if (audioBytes.length > MAX_AUDIO_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VOICE_AUDIO_INVALID", "Ovozli fayl hajmi juda katta");
        }
        return transcribeAndFinalize(workspaceId, audioBytes, mimeType);
    }

    private VoiceDraftResponse transcribeAndFinalize(Long workspaceId, byte[] audioBytes, String mimeType) {
        List<MemberCandidate> candidates = loadCandidates(workspaceId);
        List<GroupCandidate> groupCandidates = loadGroupCandidates(workspaceId);
        String prompt = buildPrompt(candidates, groupCandidates, true);

        VoiceDraftPayload payload = voiceModelClient.transcribeAndExtract(audioBytes, mimeType, prompt);
        log.info("Voice draft parsed: workspaceId={} transcript='{}' dueAtIso={} reminderMinutes={} matchedGroupId={} unmatchedGroupName='{}' groupCandidates={}",
                workspaceId, payload.transcript(), payload.dueAtIso(), payload.reminderMinutes(),
                payload.matchedGroupId(), payload.unmatchedGroupName(), groupCandidates);

        return finalizePayload(payload, candidates, groupCandidates);
    }

    /**
     * Same extraction pipeline as {@link #createDraft}, but for plain text (e.g. a Telegram
     * "/task ..." command) instead of an audio recording - skips transcription entirely and
     * asks the model to extract structured fields directly from the given text.
     */
    public VoiceDraftResponse createDraftFromText(Long userId, Long workspaceId, String text) {
        if (!voiceModelClient.configured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEMINI_NOT_CONFIGURED",
                    "Ovozli yordamchi hozircha sozlanmagan");
        }
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(workspaceId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WORKSPACE_ACCESS_DENIED", "Ish maydoniga kirishga ruxsat yo'q");
        }
        if (text == null || text.isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VOICE_NO_SPEECH_DETECTED",
                    "Vazifa matni bo'sh, qayta urinib ko'ring");
        }

        List<MemberCandidate> candidates = loadCandidates(workspaceId);
        List<GroupCandidate> groupCandidates = loadGroupCandidates(workspaceId);
        String prompt = buildPrompt(candidates, groupCandidates, false);

        VoiceDraftPayload payload = voiceModelClient.extractFromText(text, prompt);
        log.info("Text draft parsed: workspaceId={} transcript='{}' dueAtIso={} reminderMinutes={} matchedGroupId={} unmatchedGroupName='{}' groupCandidates={}",
                workspaceId, payload.transcript(), payload.dueAtIso(), payload.reminderMinutes(),
                payload.matchedGroupId(), payload.unmatchedGroupName(), groupCandidates);

        return finalizePayload(payload, candidates, groupCandidates);
    }

    private VoiceDraftResponse finalizePayload(VoiceDraftPayload payload, List<MemberCandidate> candidates,
                                                List<GroupCandidate> groupCandidates) {
        Set<Long> candidateIds = candidates.stream().map(MemberCandidate::id).collect(java.util.stream.Collectors.toSet());
        List<Long> assigneeIds = new ArrayList<>(payload.matchedAssigneeIds() == null ? List.of()
                : payload.matchedAssigneeIds().stream().filter(candidateIds::contains).toList());

        boolean blankResult = (payload.title() == null || payload.title().isBlank())
                && (payload.transcript() == null || payload.transcript().isBlank());
        if (payload.noSpeechDetected() || blankResult) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VOICE_NO_SPEECH_DETECTED",
                    "Ovozda gap aniqlanmadi, qayta urinib ko'ring");
        }

        Instant dueAt = parseDueAt(payload.dueAtIso());

        Set<Long> groupCandidateIds = groupCandidates.stream().map(GroupCandidate::id).collect(java.util.stream.Collectors.toSet());
        Long groupId = payload.matchedGroupId() != null && groupCandidateIds.contains(payload.matchedGroupId())
                ? payload.matchedGroupId() : null;

        // The LLM is deliberately conservative about matching names it's not confident in
        // (leaving them in unmatchedGroupName/unmatchedAssigneeNames instead of guessing) - but
        // ASR mishearings (e.g. "Task" heard as "pask") are common enough that a deterministic
        // fuzzy-match safety net here catches near-misses the model itself won't commit to.
        String unmatchedGroupName = payload.unmatchedGroupName();
        if (groupId == null && unmatchedGroupName != null && !unmatchedGroupName.isBlank()) {
            GroupCandidate fuzzyGroup = bestFuzzyMatch(unmatchedGroupName, groupCandidates, GroupCandidate::name);
            if (fuzzyGroup != null) {
                groupId = fuzzyGroup.id();
                unmatchedGroupName = null;
            }
        }

        List<String> unmatchedAssigneeNames = new ArrayList<>(
                payload.unmatchedAssigneeNames() == null ? List.of() : payload.unmatchedAssigneeNames());
        List<String> stillUnmatched = new ArrayList<>();
        for (String spokenName : unmatchedAssigneeNames) {
            MemberCandidate fuzzyMember = bestFuzzyMatch(spokenName, candidates, MemberCandidate::displayName);
            if (fuzzyMember == null) {
                fuzzyMember = bestFuzzyMatch(spokenName, candidates, MemberCandidate::username);
            }
            if (fuzzyMember != null && !assigneeIds.contains(fuzzyMember.id())) {
                assigneeIds.add(fuzzyMember.id());
            } else if (fuzzyMember == null) {
                stillUnmatched.add(spokenName);
            }
        }

        Long reminderMinutes = dueAt != null && payload.reminderMinutes() != null
                ? snapReminderMinutes(payload.reminderMinutes()) : null;

        // Falls back to the raw transcript/text itself when there's nothing "task-shaped" to
        // extract a title from (e.g. "Salom") - the user still gets a usable draft to edit in the
        // Mini App instead of a blank form, regardless of whether the model judged this a real task.
        String title = payload.title() != null && !payload.title().isBlank()
                ? payload.title() : fallbackTitle(payload.transcript());

        return new VoiceDraftResponse(
                payload.transcript(),
                title,
                payload.description(),
                dueAt,
                assigneeIds,
                stillUnmatched,
                groupId,
                unmatchedGroupName,
                reminderMinutes,
                null
        );
    }

    /**
     * Levenshtein-distance based fallback for names the LLM itself wasn't confident enough to
     * match (see the ASR-tolerance note in {@link #buildPrompt}) - only promotes a single,
     * unambiguous best match; if two candidates are both close, declines rather than guessing.
     */
    private static <T> T bestFuzzyMatch(String spoken, List<T> candidates, java.util.function.Function<T, String> nameOf) {
        T best = null;
        for (T candidate : candidates) {
            if (isCloseMatch(spoken, nameOf.apply(candidate))) {
                if (best != null) return null;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isCloseMatch(String spoken, String candidateName) {
        if (spoken == null || candidateName == null) return false;
        String a = spoken.trim().toLowerCase(Locale.ROOT);
        String b = candidateName.trim().toLowerCase(Locale.ROOT);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;
        int maxLen = Math.max(a.length(), b.length());
        if (maxLen < 3) return false;
        int distance = levenshteinDistance(a, b);
        double threshold = maxLen <= 5 ? 1 : maxLen <= 9 ? 2 : maxLen * 0.25;
        return distance <= threshold;
    }

    private static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private static final int MAX_TITLE_LENGTH = 300;

    private static String fallbackTitle(String transcript) {
        if (transcript == null || transcript.isBlank()) return "";
        String trimmed = transcript.trim();
        return trimmed.length() <= MAX_TITLE_LENGTH ? trimmed : trimmed.substring(0, MAX_TITLE_LENGTH - 1) + "…";
    }

    private static final long[] REMINDER_PRESETS = {0, 5, 15, 30, 60, 1440};

    private static long snapReminderMinutes(long raw) {
        long closest = REMINDER_PRESETS[0];
        long smallestDiff = Math.abs(raw - closest);
        for (long preset : REMINDER_PRESETS) {
            long diff = Math.abs(raw - preset);
            if (diff < smallestDiff) {
                smallestDiff = diff;
                closest = preset;
            }
        }
        return closest;
    }

    private void validateAudio(MultipartFile audio) {
        if (audio == null || audio.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VOICE_AUDIO_INVALID", "Ovozli fayl topilmadi");
        }
        if (!"audio/wav".equals(audio.getContentType())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VOICE_AUDIO_INVALID", "Ovozli fayl formati noto'g'ri");
        }
        if (audio.getSize() > MAX_AUDIO_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VOICE_AUDIO_INVALID", "Ovozli fayl hajmi juda katta");
        }
    }

    private byte[] readBytes(MultipartFile audio) {
        try {
            return audio.getBytes();
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VOICE_AUDIO_INVALID", "Ovozli faylni o'qib bo'lmadi");
        }
    }

    private List<MemberCandidate> loadCandidates(Long workspaceId) {
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepository.findAllByWorkspaceIdAndActiveTrue(workspaceId)
                .stream()
                .filter(membership -> !membership.isTemporarilyBlocked())
                .toList();
        List<Long> userIds = memberships.stream().map(WorkspaceMemberEntity::getUserId).toList();
        return StreamSupport.stream(userRepository.findAllById(userIds).spliterator(), false)
                .map(this::toCandidate)
                .toList();
    }

    private MemberCandidate toCandidate(UserEntity user) {
        String displayName = user.getLastName() == null || user.getLastName().isBlank()
                ? user.getFirstName()
                : user.getFirstName() + " " + user.getLastName();
        return new MemberCandidate(user.getId(), displayName, user.getUsername());
    }

    private List<GroupCandidate> loadGroupCandidates(Long workspaceId) {
        return groupRepository.findAllByWorkspaceIdAndActiveTrueOrderByNameAsc(workspaceId).stream()
                .map(group -> new GroupCandidate(group.getId(), group.getName()))
                .toList();
    }

    private String buildPrompt(List<MemberCandidate> candidates, List<GroupCandidate> groupCandidates, boolean fromAudio) {
        String membersJson;
        try {
            membersJson = objectMapper.writeValueAsString(candidates);
        } catch (JacksonException exception) {
            membersJson = "[]";
        }
        String groupsJson;
        try {
            groupsJson = objectMapper.writeValueAsString(groupCandidates);
        } catch (JacksonException exception) {
            groupsJson = "[]";
        }
        String sourceIntro = fromAudio
                ? "Foydalanuvchi yaratmoqchi bo'lgan vazifa haqida ovozli xabar yozdi."
                : "Foydalanuvchi yaratmoqchi bo'lgan vazifa haqida quyidagi matnni yozdi.";
        String transcriptInstruction = fromAudio
                ? """
                1. Audio yozuvni "transcript" maydoniga so'zma-so'z (aynan eshitilganidek, xuddi shu tilda) yozib bering.
                   "transcript" faqat haqiqatan aytilgan so'zlardangina iborat bo'lishi SHART. Agar audio ichida
                   sukunat, tinish yoki tushunarsiz/shovqinli bo'lak bo'lsa, uni shunchaki e'tiborsiz qoldirib
                   o'tkazib yuboring - hech qachon vaqt belgilari yoki subtitr uslubidagi yozuvlar (masalan
                   "00:00", "00:01", "[00:02]" va hokazo) qo'shmang. "transcript" faqat gapirilgan so'zlardan
                   iborat bo'lishi kerak, boshqa hech narsa emas."""
                : """
                1. Foydalanuvchi yozgan matnni "transcript" maydoniga aynan o'zgarishsiz ko'chiring - imlo yoki
                   so'zlarni tuzatmang, tarjima qilmang.""";
        String noSpeechInstruction = fromAudio
                ? """
                8. Agar audio'da tushunarli nutq umuman bo'lmasa (sukunat yoki faqat shovqin), "noSpeechDetected" ni
                   true qiling va boshqa maydonlarni bo'sh qoldiring. Bunday holatda ham "transcript"ga vaqt
                   belgilari yoki boshqa hech qanday matn qo'shmang - uni bo'sh qoldiring."""
                : """
                8. Agar matnda vazifa haqida tushunarli hech narsa bo'lmasa (bo'sh yoki mavhum matn), "noSpeechDetected"
                   ni true qiling va boshqa maydonlarni bo'sh qoldiring.""";
        return """
                Siz Telegram-asosidagi jamoaviy vazifa boshqaruv ilovasiga o'rnatilgan vazifa-yaratish yordamchisisiz.
                %s Nutq o'zbek, rus yoki ingliz tilida (yoki ularning aralashmasida) bo'lishi mumkin.

                ENG MUHIM QOIDA - HECH NARSANI O'YLAB TOPMANG: Har bir maydonni FAQAT transkriptda aniq aytilgan
                ma'lumot asosida to'ldiring. Agar biror narsa (sana, ism, guruh, eslatma) aytilmagan yoki noaniq
                bo'lsa, uni taxmin qilib to'ldirmang - shunchaki bo'sh/null qoldiring. Noto'g'ri taxmin qilingan
                ma'lumot bo'sh maydondan ancha yomonroq, chunki foydalanuvchi buni sezmasdan qoldirib yuborishi mumkin.

                MUHIM: "transcript", "title" va "description" doim foydalanuvchi GAPIRGAN/YOZGAN tilda bo'lishi SHART.
                Hech qachon boshqa tilga (masalan ingliz tiliga) tarjima qilmang - agar foydalanuvchi o'zbek tilida
                yozgan bo'lsa, javob ham o'zbek tilida bo'lishi kerak; rus tilida yozsa - rus tilida.

                Vazifalar:
                %s
                2. "title" - vazifaning qisqa, buyruq shaklidagi nomi, transkript bilan bir xil tilda.
                3. "description" - title'dan tashqari qo'shimcha tafsilotlar, transkript bilan bir xil tilda; agar bo'lmasa bo'sh satr.
                4. "dueAtIso" - agar sana/vaqt aytilgan bo'lsa, uni Toshkent MAHALLIY vaqti sifatida, offset yoki
                   "Z" QO'SHMASDAN, ISO-8601 shaklida qaytaring (masalan "2026-07-27T12:00:00" - hech qanday
                   UTC'ga o'girish shart emas, faqat aytilgan mahalliy sana/soatni shu formatga solib bering).
                   Hozirgi mahalliy vaqt (Asia/Tashkent): %s. Nisbiy iboralarni ("ertaga", "indinga", "dushanba
                   kuni", "soat beshda" va hokazo) shu hozirgi vaqtga nisbatan hisoblang. Agar sana aytilmagan
                   bo'lsa, null qaytaring.
                   Aniqlashtirish: "tong/ertalab" - 06:00-11:00, "kunduzi/tushdan keyin" - 12:00-17:00,
                   "kechqurun" - 18:00-22:00, "tunda" - 23:00-05:00 oralig'ini bildiradi. Agar faqat son aytilib
                   (masalan "soat beshda", "soat 5 da") kun bo'limi (tong/kechqurun) ko'rsatilmagan bo'lsa, ENG
                   YAQIN KELAJAKDAGI vaqtni tanlang: agar hozirgi vaqtdan keyin shu kun ichida ertalabki variant
                   (masalan 05:00) hali o'tmagan bo'lsa o'shani, aks holda kechki variantni (masalan 17:00) qo'llang.
                MUHIM (ovozli tanib olish xatolariga chidamlilik): audio matnga aylantirilganda harflar ko'pincha
                talaffuzi yaqin bo'lgan boshqa harf bilan almashtirilib qo'yiladi (masalan "t"<->"p", "k"<->"g",
                "b"<->"p", "d"<->"t" va hokazo, ayniqsa inglizcha o'zlashma so'zlarda, masalan "task" so'zi "pask"
                deb eshitilishi mumkin). Nom solishtirishda (5- va 6-bandlar) FAQAT harfma-harf yozilishiga qarab
                hukm chiqarmang - agar transkriptdagi so'z ro'yxatdagi biror nomga shunday bitta-ikkita undosh
                harf almashinuvidan boshqa jihatdan deyarli bir xil bo'lsa (bir xil uzunlik, bir xil unlilar,
                bir xil bo'g'in soni), buni ISHONCHLI MOS sifatida hisoblang - faqat butunlay boshqa so'z bo'lsa
                (masalan uzunligi yoki unlilari mos kelmasa) mos kelmadi deb hisoblang.
                5. Quyida ish maydoni a'zolarining ro'yxati JSON ko'rinishida berilgan: %s
                   Agar foydalanuvchi kimgadir vazifa tayinlashni aytgan bo'lsa, aytilgan ismni (ism, taxallus yoki
                   @username, uchala tildan birortasida, talaffuz/imlo farqlarini hisobga olgan holda) shu ro'yxatdagi
                   a'zolar bilan solishtiring. Faqat ANIQ va ISHONCHLI mos kelgan a'zolarning "id" larini
                   "matchedAssigneeIds" ga qo'shing - ikkita yoki undan ortiq a'zo bir xil darajada mos kelishi
                   mumkin bo'lsa (noaniq holat), hech birini tanlamang. Agar aytilgan ism ro'yxatdagi hech kimga
                   aniq mos kelmasa, TAXMIN QILMANG - aytilgan ismni aynan "unmatchedAssigneeNames" ga qo'shing.
                6. Quyida ish maydoniga ulangan Telegram guruhlarining ro'yxati JSON ko'rinishida berilgan: %s
                   Agar foydalanuvchi ushbu vazifa qaysi guruhga tegishli ekanini aytgan bo'lsa (masalan "X guruhida",
                   "X chatida"), aytilgan nomni shu ro'yxatdagi guruhlar bilan solishtiring va ishonchli mos kelgan
                   guruhning "id" sini "matchedGroupId" ga qo'ying. Agar guruh haqida umuman gap ketmagan bo'lsa,
                   "matchedGroupId" ni null qoldiring. Agar guruh nomi aytilgan bo'lsa-yu, lekin ro'yxatdagi hech
                   qaysisiga aniq mos kelmasa, TAXMIN QILMANG - aytilgan nomni aynan "unmatchedGroupName" ga yozing.
                7. "reminderMinutes" - agar foydalanuvchi eslatma haqida aytgan bo'lsa (masalan "30 daqiqa oldin eslat",
                   "1 soat oldin ogohlantir", "bir kun oldin eslatma qil", "belgilangan vaqtda eslat"), quyidagi
                   ruxsat etilgan qiymatlardan ENG YAQININI tanlang: 0 (belgilangan vaqtning o'zida), 5, 15, 30,
                   60 (1 soat oldin), 1440 (1 kun oldin). Boshqa hech qanday son qaytarmang - faqat shu ro'yxatdagi
                   qiymatlardan birini. Eslatma haqida gap ketmagan bo'lsa YOKI "dueAtIso" aniqlanmagan bo'lsa
                   (chunki eslatma faqat muddati bor vazifalarda ishlaydi), null qaytaring.
                %s

                Javobni FAQAT berilgan JSON sxemasi bo'yicha qaytaring - JSON'dan tashqarida hech qanday izoh,
                tushuntirish yoki qo'shimcha matn yozmang.
                """.formatted(sourceIntro, transcriptInstruction, java.time.LocalDateTime.now(VOICE_ZONE),
                        membersJson, groupsJson, noSpeechInstruction);
    }

    private Instant parseDueAt(String dueAtIso) {
        if (dueAtIso == null || dueAtIso.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dueAtIso);
        } catch (DateTimeParseException exception) {
            try {
                return java.time.LocalDateTime.parse(dueAtIso).atZone(VOICE_ZONE).toInstant();
            } catch (DateTimeParseException innerException) {
                return null;
            }
        }
    }

    record MemberCandidate(Long id, String displayName, String username) {
    }

    record GroupCandidate(Long id, String name) {
    }
}

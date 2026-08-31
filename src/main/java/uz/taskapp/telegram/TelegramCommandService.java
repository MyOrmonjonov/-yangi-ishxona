package uz.taskapp.telegram;

import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.User;
import org.springframework.stereotype.Component;
import uz.taskapp.common.ApiException;
import uz.taskapp.group.GroupEntity;
import uz.taskapp.group.GroupRepository;
import uz.taskapp.task.TaskEntity;
import uz.taskapp.task.TaskRepository;
import uz.taskapp.task.TaskService;
import uz.taskapp.task.TaskStatus;
import uz.taskapp.user.UserEntity;
import uz.taskapp.user.UserRepository;
import uz.taskapp.voice.VoiceDraftResponse;
import uz.taskapp.voice.VoiceDraftService;
import uz.taskapp.workspace.WorkspaceMemberEntity;
import uz.taskapp.workspace.WorkspaceMemberRepository;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.StreamSupport;

/**
 * Handles the Telegram bot's text slash-commands (/task, /my, /due, /done, /members, /help).
 * Reuses TaskService for all task operations and VoiceDraftService's LLM-based extraction
 * pipeline (built for voice, generalized to accept plain text) for /task's free-text parsing.
 */
@Component
public class TelegramCommandService {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Tashkent");
    private static final DateTimeFormatter DUE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final TaskService taskService;
    private final TaskRepository taskRepository;
    private final VoiceDraftService voiceDraftService;
    private final GroupRepository groupRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;

    public TelegramCommandService(TaskService taskService, TaskRepository taskRepository,
                                   VoiceDraftService voiceDraftService, GroupRepository groupRepository,
                                   WorkspaceMemberRepository workspaceMemberRepository, UserRepository userRepository) {
        this.taskService = taskService;
        this.taskRepository = taskRepository;
        this.voiceDraftService = voiceDraftService;
        this.groupRepository = groupRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
    }

    public String handleHelp() {
        return """
                📋 Mavjud komandalar:

                /start - Ilova tugmasini qayta yuborish
                /task @kimga bugun 17:00 matn - Yangi vazifa yaratish
                /my - Mening ochiq vazifalarim
                /due - Muddati bor vazifalar
                /done TASK-0001 - Vazifani bajarildi deb belgilash
                /members - Ish maydoni a'zolari
                /help - Ushbu yordam xabari""";
    }

    public String handleMembers(Chat chat, User telegramUser) {
        Optional<CommandContext> context = resolveContext(chat, telegramUser);
        if (context.isEmpty()) return workspaceUnresolvedMessage(chat);
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepository
                .findAllByWorkspaceIdAndActiveTrue(context.get().workspaceId());
        List<Long> userIds = memberships.stream().map(WorkspaceMemberEntity::getUserId).toList();
        List<UserEntity> members = StreamSupport.stream(userRepository.findAllById(userIds).spliterator(), false).toList();
        if (members.isEmpty()) return "Ish maydonida a'zolar topilmadi.";
        StringBuilder reply = new StringBuilder("👥 Ish maydoni a'zolari:\n\n");
        for (UserEntity member : members) {
            reply.append("• ").append(displayName(member));
            if (member.getUsername() != null && !member.getUsername().isBlank()) {
                reply.append(" (@").append(member.getUsername()).append(")");
            }
            reply.append("\n");
        }
        return reply.toString();
    }

    public String handleMy(Chat chat, User telegramUser) {
        Optional<CommandContext> context = resolveContext(chat, telegramUser);
        if (context.isEmpty()) return workspaceUnresolvedMessage(chat);
        List<TaskService.TaskResponse> tasks = taskService.list(
                context.get().userId(), context.get().workspaceId(), TaskService.TaskScope.MINE);
        if (tasks.isEmpty()) return "Sizda ochiq vazifalar yo'q.";
        StringBuilder reply = new StringBuilder("📝 Mening vazifalarim:\n\n");
        tasks.forEach(task -> reply.append(formatTaskLine(task)).append("\n"));
        return reply.toString();
    }

    public String handleDue(Chat chat, User telegramUser) {
        Optional<CommandContext> context = resolveContext(chat, telegramUser);
        if (context.isEmpty()) return workspaceUnresolvedMessage(chat);
        List<TaskService.TaskResponse> tasks = taskService.list(
                        context.get().userId(), context.get().workspaceId(), TaskService.TaskScope.ALL)
                .stream()
                .filter(task -> task.dueAt() != null
                        && task.status() != TaskStatus.COMPLETED && task.status() != TaskStatus.CANCELLED)
                .sorted(Comparator.comparing(TaskService.TaskResponse::dueAt))
                .toList();
        if (tasks.isEmpty()) return "Muddati bor vazifalar topilmadi.";
        StringBuilder reply = new StringBuilder("⏰ Muddati bor vazifalar:\n\n");
        tasks.forEach(task -> reply.append(formatTaskLine(task)).append("\n"));
        return reply.toString();
    }

    public String handleDone(Chat chat, User telegramUser, String code) {
        Optional<CommandContext> context = resolveContext(chat, telegramUser);
        if (context.isEmpty()) return workspaceUnresolvedMessage(chat);
        Long sequenceNumber = parseTaskCode(code);
        if (sequenceNumber == null) {
            return "Vazifa kodini to'g'ri kiriting, masalan: /done TASK-0001";
        }
        TaskEntity task = taskRepository
                .findByWorkspaceIdAndSequenceNumberAndDeletedAtIsNull(context.get().workspaceId(), sequenceNumber)
                .orElse(null);
        if (task == null) {
            return "Bunday vazifa topilmadi: " + code;
        }
        try {
            taskService.changeStatus(context.get().userId(), task.getId(), TaskStatus.COMPLETED);
            return "✅ TASK-" + String.format("%04d", sequenceNumber) + " (\"" + task.getTitle() + "\") bajarildi deb belgilandi.";
        } catch (ApiException exception) {
            return exception.getMessage();
        }
    }

    /**
     * Parses a typed "/task ..." command (or the text of a message someone replied to with a
     * bare "/task") and stores the resulting draft for the Mini App to pick up - nothing is
     * created in the database here, exactly like {@link #handleVoiceTask}: the user reviews and
     * actually creates the task in the Mini App's form.
     */
    public VoicePreview handleTextTask(Chat chat, User telegramUser, String text, Long topicId) {
        Optional<CommandContext> context = resolveContext(chat, telegramUser);
        if (context.isEmpty()) return VoicePreview.error(workspaceUnresolvedMessage(chat));
        CommandContext ctx = context.get();

        VoiceDraftResponse draft;
        try {
            draft = voiceDraftService.createDraftFromText(ctx.userId(), ctx.workspaceId(), text);
        } catch (ApiException exception) {
            return VoicePreview.error(exception.getMessage());
        }
        draft = withGroupFallback(draft, ctx, topicId);

        long draftId = voiceDraftService.storePendingDraft(ctx.workspaceId(), ctx.userId(), draft, null, null);
        return VoicePreview.pending(draftId, formatDraftPreview(draft, false));
    }

    /**
     * Transcribes a Telegram voice message and stores the resulting draft for the Mini App to
     * pick up - nothing is created in the database here. The caller opens the Mini App
     * (pre-filled with this draft) for the user to review/edit and create it there, exactly
     * like the in-app voice recorder flow.
     */
    public VoicePreview handleVoiceTask(Chat chat, User telegramUser, byte[] audio, String mimeType, Long topicId) {
        Optional<CommandContext> context = resolveContext(chat, telegramUser);
        if (context.isEmpty()) return VoicePreview.error(workspaceUnresolvedMessage(chat));
        CommandContext ctx = context.get();

        VoiceDraftResponse draft;
        try {
            draft = voiceDraftService.createDraftFromAudio(ctx.userId(), ctx.workspaceId(), audio, mimeType);
        } catch (ApiException exception) {
            return VoicePreview.error(exception.getMessage());
        }
        draft = withGroupFallback(draft, ctx, topicId);

        long draftId = voiceDraftService.storePendingDraft(ctx.workspaceId(), ctx.userId(), draft, audio, mimeType);
        return VoicePreview.pending(draftId, formatDraftPreview(draft, true));
    }

    public String cancelVoiceTask(long draftId) {
        voiceDraftService.discardPendingDraft(draftId);
        return "❌ Bekor qilindi.";
    }

    /** Remembers where a draft's preview message was posted, so it can be deleted once the task is created. */
    public void attachDraftMessage(long draftId, long chatId, int messageId) {
        voiceDraftService.attachPreviewMessage(draftId, chatId, messageId);
    }

    /**
     * Fills in the group the /task command was typed in when the LLM didn't detect one from the
     * text, and attaches the topic (forum sub-thread) the command was sent from - but only when
     * the draft ends up targeting that same chat's group, since a topic id only makes sense there.
     */
    private VoiceDraftResponse withGroupFallback(VoiceDraftResponse draft, CommandContext ctx, Long topicId) {
        if (ctx.groupId() == null) return draft;
        Long groupId = draft.groupId() != null ? draft.groupId() : ctx.groupId();
        Long resolvedTopicId = ctx.groupId().equals(groupId) ? topicId : draft.topicId();
        if (groupId.equals(draft.groupId()) && Objects.equals(resolvedTopicId, draft.topicId())) {
            return draft;
        }
        return new VoiceDraftResponse(draft.transcript(), draft.title(), draft.description(), draft.dueAt(),
                draft.assigneeIds(), draft.unmatchedAssigneeNames(), groupId, draft.unmatchedGroupName(),
                draft.reminderMinutes(), resolvedTopicId);
    }

    private String formatDraftPreview(VoiceDraftResponse draft, boolean fromVoice) {
        StringBuilder preview = new StringBuilder("📝 Vazifa drafti tayyor\n\n");
        if (draft.transcript() != null && !draft.transcript().isBlank()) {
            preview.append("\"").append(draft.transcript()).append("\"\n\n");
        }
        preview.append("📌 ").append(draft.title());
        if (draft.description() != null && !draft.description().isBlank()) {
            preview.append("\n").append(draft.description());
        }
        if (draft.dueAt() != null) {
            preview.append("\n⏰ Muddat: ").append(DUE_FORMAT.format(draft.dueAt().atZone(APP_ZONE)));
        }
        if (draft.unmatchedAssigneeNames() != null && !draft.unmatchedAssigneeNames().isEmpty()) {
            preview.append("\n⚠️ Mas'ul sifatida topilmadi: ").append(String.join(", ", draft.unmatchedAssigneeNames()));
        }
        if (draft.unmatchedGroupName() != null && !draft.unmatchedGroupName().isBlank()) {
            preview.append("\n⚠️ Guruh sifatida topilmadi: ").append(draft.unmatchedGroupName());
        }
        preview.append("\n\nManba: ").append(fromVoice ? "ovozli xabar" : "matnli xabar")
                .append("\nVazifa hali yaratilmagan. Formada tekshirib, saqlang.");
        return preview.toString();
    }

    public sealed interface VoicePreview {
        record Pending(long draftId, String previewText) implements VoicePreview {
        }

        record Error(String message) implements VoicePreview {
        }

        static VoicePreview pending(long draftId, String previewText) {
            return new Pending(draftId, previewText);
        }

        static VoicePreview error(String message) {
            return new Error(message);
        }
    }

    private static String formatTaskLine(TaskService.TaskResponse task) {
        StringBuilder line = new StringBuilder(task.code()).append(" ")
                .append(statusLabel(task.status())).append(" — ").append(task.title());
        if (task.dueAt() != null) {
            line.append(" (").append(DUE_FORMAT.format(task.dueAt().atZone(APP_ZONE))).append(")");
        }
        return line.toString();
    }

    private static String statusLabel(TaskStatus status) {
        return switch (status) {
            case NEW -> "🆕";
            case IN_PROGRESS -> "▶️";
            case BLOCKED -> "⚠️";
            case REVIEW -> "🔎";
            case COMPLETED -> "✅";
            case CANCELLED -> "🚫";
        };
    }

    private static Long parseTaskCode(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("\\D", "");
        if (digits.isBlank()) return null;
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String displayName(UserEntity user) {
        return user.getLastName() == null || user.getLastName().isBlank()
                ? user.getFirstName()
                : user.getFirstName() + " " + user.getLastName();
    }

    private String workspaceUnresolvedMessage(Chat chat) {
        if (chat.type() == Chat.Type.group || chat.type() == Chat.Type.supergroup) {
            return "Bu guruh hali biror ish maydoniga ulanmagan. Avval ilovada guruhni ulang.";
        }
        return "Ish maydoningizni aniqlay olmadim. Agar bir nechta ish maydoningiz bo'lsa, bu komandani "
                + "tegishli Telegram guruhida yuboring.";
    }

    private Optional<CommandContext> resolveContext(Chat chat, User telegramUser) {
        UserEntity user = resolveUser(telegramUser);
        boolean groupChat = chat.type() == Chat.Type.group || chat.type() == Chat.Type.supergroup;
        if (groupChat) {
            Optional<GroupEntity> group = groupRepository.findByTelegramChatId(chat.id()).filter(GroupEntity::isActive);
            if (group.isEmpty()) return Optional.empty();
            user.updateLastBotWorkspace(group.get().getWorkspaceId());
            userRepository.save(user);
            return Optional.of(new CommandContext(group.get().getWorkspaceId(), user.getId(), group.get().getId()));
        }
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepository.findAllByUserIdAndActiveTrue(user.getId());
        Long workspaceId = null;
        if (memberships.size() == 1) {
            workspaceId = memberships.get(0).getWorkspaceId();
        } else if (user.getLastBotWorkspaceId() != null
                && memberships.stream().anyMatch(m -> m.getWorkspaceId().equals(user.getLastBotWorkspaceId()))) {
            workspaceId = user.getLastBotWorkspaceId();
        }
        if (workspaceId == null) return Optional.empty();
        return Optional.of(new CommandContext(workspaceId, user.getId(), null));
    }

    private UserEntity resolveUser(User telegramUser) {
        String firstName = telegramUser.firstName() == null || telegramUser.firstName().isBlank()
                ? "Foydalanuvchi" : telegramUser.firstName();
        UserEntity user = userRepository.findByTelegramId(telegramUser.id())
                .orElseGet(() -> new UserEntity(telegramUser.id(), firstName));
        user.updateTelegramProfile(firstName, telegramUser.lastName(), telegramUser.username(),
                user.getPhotoUrl(), telegramUser.languageCode());
        user.markBotConnected();
        return userRepository.save(user);
    }

    private record CommandContext(Long workspaceId, Long userId, Long groupId) {
    }
}

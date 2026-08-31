package uz.taskapp.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.ChatMember;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.request.GetChatMember;
import com.pengrad.telegrambot.request.GetMe;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.EditMessageCaption;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.GetChatMemberResponse;
import com.pengrad.telegrambot.response.GetMeResponse;
import com.pengrad.telegrambot.response.SendResponse;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import uz.taskapp.config.TelegramProperties;
import uz.taskapp.task.TaskStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelegramTaskNotificationService {
    private static final Logger log = LoggerFactory.getLogger(TelegramTaskNotificationService.class);
    private static final DateTimeFormatter DEADLINE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Tashkent"));
    private static final long POSITIVE_CACHE_SECONDS = 300;
    private static final long NEGATIVE_CACHE_SECONDS = 5;

    private final TelegramProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final TelegramBot bot;
    private final Map<Long, CachedBotStatus> botStatusCache = new ConcurrentHashMap<>();
    private volatile Long botTelegramId;

    public TelegramTaskNotificationService(TelegramProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.bot = properties.botToken() == null || properties.botToken().isBlank()
                ? null : new TelegramBot(properties.botToken());
    }

    public boolean isBotMember(Long chatId) {
        if (bot == null || chatId == null) return false;
        CachedBotStatus cached = botStatusCache.get(chatId);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) return cached.member();
        boolean member = checkBotMembership(chatId);
        long ttl = member ? POSITIVE_CACHE_SECONDS : NEGATIVE_CACHE_SECONDS;
        botStatusCache.put(chatId, new CachedBotStatus(member, Instant.now().plusSeconds(ttl)));
        return member;
    }

    public String botUsername() {
        String value = properties.botUsername();
        return value == null || value.isBlank() ? null : value.replace("@", "").trim();
    }

    public void publishAsync(TaskTelegramMessage message) {
        if (bot == null || message.chatId() == null) return;
        Thread.startVirtualThread(() -> publish(message));
    }

    public void syncAsync(TaskTelegramMessage message) {
        if (bot == null || message.chatId() == null) return;
        Thread.startVirtualThread(() -> sync(message));
    }

    /**
     * Deletes a message that's been superseded - e.g. a "/task" draft preview once the user has
     * actually created the task from it, so the chat isn't left with both the draft and the task.
     */
    public void deleteMessageAsync(Long chatId, Integer messageId) {
        if (bot == null || chatId == null || messageId == null) return;
        Thread.startVirtualThread(() -> bot.execute(new DeleteMessage(chatId, messageId)));
    }

    public boolean sendMembershipInvite(Long groupId, Long chatId) {
        if (bot == null || groupId == null || chatId == null || !isBotMember(chatId)) return false;
        // Join tugmasi vaqtincha o'chirilgan: a'zolik endi guruhga yozilgan istalgan xabardan avtomatik aniqlanadi.
        String text = "<b>👥 Task App guruh a'zolari</b>\n\n"
                + "Vazifalarga mas'ul sifatida tanlanish uchun guruhga xabar yozishning o'zi yetarli.";
        SendResponse response = bot.execute(new SendMessage(chatId, text)
                .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML));
        if (!response.isOk()) {
            botStatusCache.remove(chatId);
            log.warn("Guruh {} uchun a'zolik xabari yuborilmadi: {}", groupId, response.description());
        }
        return response.isOk();
    }

    /**
     * Sends a reminder and returns the new message's id, or null if sending failed. If the message
     * carries a previousReminderMessageId (e.g. an earlier overdue reminder for the same task), that
     * message is deleted first so repeated reminders don't pile up in the chat.
     */
    public Long sendReminder(ReminderTelegramMessage message) {
        if (bot == null || message.chatId() == null) return null;
        if (message.groupMessage() && !isBotMember(message.chatId())) return null;
        if (message.previousReminderMessageId() != null) {
            bot.execute(new DeleteMessage(message.chatId(), message.previousReminderMessageId().intValue()));
        }
        StringBuilder text = new StringBuilder(message.overdue()
                ? "<b>⚠️ Vazifa muddati o'tib ketdi</b>\n\n"
                : "<b>⏰ Vazifa eslatmasi</b>\n\n")
                .append("<b>").append(escapeHtml(message.title())).append("</b>");
        if (message.dueAt() != null) {
            text.append("\nMuddat: ").append(DEADLINE_FORMAT.format(message.dueAt()));
        }
        if (message.groupMessage() && !message.assignees().isEmpty()) {
            text.append("\n👤 Mas'ul: ").append(assigneesText(message.assignees(), false));
        }
        SendMessage request = new SendMessage(message.chatId(), text.toString())
                .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML);
        if (message.groupMessage() && message.topicId() != null) {
            request = request.messageThreadId(message.topicId());
        }
        InlineKeyboardMarkup keyboard = reminderKeyboard(message);
        if (keyboard != null) request = request.replyMarkup(keyboard);
        SendResponse response = bot.execute(request);
        if (!response.isOk()) {
            log.warn("Vazifa {} eslatmasi yuborilmadi: {}", message.taskId(), response.description());
            return null;
        }
        return response.message() == null ? null : response.message().messageId().longValue();
    }

    private InlineKeyboardMarkup reminderKeyboard(ReminderTelegramMessage message) {
        if (message.groupMessage()) {
            if (message.messageId() == null) return null;
            String link = groupMessageLink(message.chatId(), message.topicId(), message.messageId());
            return link == null ? null : new InlineKeyboardMarkup(
                    new InlineKeyboardButton("📋 Vazifaga o'tish").url(link));
        }
        String miniAppUrl = properties.miniAppUrl();
        if (miniAppUrl == null || miniAppUrl.isBlank()) return null;
        String separator = miniAppUrl.contains("?") ? "&" : "?";
        return new InlineKeyboardMarkup(new InlineKeyboardButton("📋 Vazifaga o'tish")
                .webApp(new com.pengrad.telegrambot.model.WebAppInfo(
                        miniAppUrl + separator + "task=" + message.taskId())));
    }

    private String groupMessageLink(Long chatId, Long topicId, Long messageId) {
        String raw = String.valueOf(chatId);
        if (!raw.startsWith("-100")) return null;
        String internalId = raw.substring(4);
        return topicId == null
                ? "https://t.me/c/" + internalId + "/" + messageId
                : "https://t.me/c/" + internalId + "/" + topicId + "/" + messageId;
    }

    private void publish(TaskTelegramMessage message) {
        if (!isBotMember(message.chatId())) {
            log.warn("Vazifa {} Telegram guruhiga yuborilmadi: bot guruh a'zosi emas", message.taskId());
            return;
        }
        SendResponse response = sendTaskMessage(message);
        if (!response.isOk()) {
            Long migratedChatId = migratedChatId(response, message.chatId());
            if (migratedChatId != null) {
                message = message.withChatId(migratedChatId);
                response = sendTaskMessage(message);
            }
        }
        if (!response.isOk()) {
            botStatusCache.remove(message.chatId());
            log.warn("Vazifa {} Telegram guruhiga yuborilmadi: {}", message.taskId(), response.description());
        } else if (response.message() != null) {
            jdbcTemplate.update("UPDATE tasks SET telegram_message_id = ? WHERE id = ?",
                    response.message().messageId().longValue(), message.taskId());
        }
    }

    private Long migratedChatId(BaseResponse response, Long currentChatId) {
        Long newChatId = response.parameters() == null ? null : response.parameters().migrateToChatId();
        if (newChatId == null) return null;
        jdbcTemplate.update("UPDATE groups SET telegram_chat_id = ?, updated_at = now() WHERE telegram_chat_id = ?",
                newChatId, currentChatId);
        botStatusCache.remove(currentChatId);
        log.info("Guruh supergroupga ko'chirildi: {} -> {}", currentChatId, newChatId);
        return newChatId;
    }

    private void sync(TaskTelegramMessage message) {
        if (message.messageId() == null) {
            publish(message);
            return;
        }
        if (message.replaceMedia()) {
            replaceTaskMessage(message);
            return;
        }
        BaseResponse response = message.attachment() == null
                ? bot.execute(new EditMessageText(message.chatId(), message.messageId().intValue(), taskText(message))
                        .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML)
                        .replyMarkup(taskKeyboard(message.status(), message.taskId())))
                : bot.execute(new EditMessageCaption(message.chatId(), message.messageId().intValue())
                        .caption(taskText(message))
                        .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML)
                        .replyMarkup(taskKeyboard(message.status(), message.taskId())));
        String description = String.valueOf(response.description());
        if (!response.isOk() && !description.contains("message is not modified")) {
            Long migratedChatId = migratedChatId(response, message.chatId());
            replaceTaskMessage(migratedChatId != null ? message.withChatId(migratedChatId) : message);
        }
    }

    private void replaceTaskMessage(TaskTelegramMessage message) {
        SendResponse replacement = sendTaskMessage(message);
        if (replacement.isOk() && replacement.message() != null) {
            jdbcTemplate.update("UPDATE tasks SET telegram_message_id = ? WHERE id = ?",
                    replacement.message().messageId().longValue(), message.taskId());
            bot.execute(new DeleteMessage(message.chatId(), message.messageId().intValue()));
        } else {
            log.warn("Vazifa {} Telegram media xabari almashtirilmadi: {}",
                    message.taskId(), replacement.description());
        }
    }

    private SendResponse sendTaskMessage(TaskTelegramMessage message) {
        InlineKeyboardMarkup keyboard = taskKeyboard(message.status(), message.taskId());
        TaskTelegramAttachment attachment = message.attachment();
        if (attachment == null) {
            SendMessage request = new SendMessage(message.chatId(), taskText(message))
                    .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML)
                    .replyMarkup(keyboard);
            if (message.topicId() != null) request = request.messageThreadId(message.topicId());
            return bot.execute(request);
        }
        if (attachment.photo()) {
            SendPhoto request = new SendPhoto(message.chatId(), attachment.bytes())
                    .fileName(attachment.name())
                    .contentType(attachment.contentType())
                    .caption(taskText(message))
                    .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML)
                    .showCaptionAboveMedia(true)
                    .replyMarkup(keyboard);
            if (message.topicId() != null) request = request.messageThreadId(message.topicId());
            return bot.execute(request);
        }
        SendDocument request = new SendDocument(message.chatId(), attachment.bytes())
                .fileName(attachment.name())
                .contentType(attachment.contentType())
                .caption(taskText(message))
                .parseMode(com.pengrad.telegrambot.model.request.ParseMode.HTML)
                .replyMarkup(keyboard);
        if (message.topicId() != null) request = request.messageThreadId(message.topicId());
        return bot.execute(request);
    }

    private String taskText(TaskTelegramMessage message) {
        boolean media = message.attachment() != null;
        String title = shorten(message.title(), media ? 220 : 300);
        String description = message.description() == null ? null
                : shorten(message.description(), media ? 260 : 700);
        String assignees = assigneesText(message.assignees(), media);
        String author = media ? shorten(message.author(), 120) : message.author();
        StringBuilder text = new StringBuilder("<b>📋 Vazifa</b>\n\n")
                .append("<b>").append(escapeHtml(title)).append("</b>");
        if (description != null && !description.isBlank()) {
            text.append("\n").append(escapeHtml(description));
        }
        text.append("\n\n📌 <b>Status:</b> ").append(statusLabel(message.status()))
                .append("\n👤 <b>Mas'ul:</b> ")
                .append(assignees)
                .append("\n✍️ <b>Topshiriq beruvchi:</b> ")
                .append(escapeHtml(author));
        if (message.dueAt() != null) {
            text.append("\n⏰ <b>Muddat:</b> ").append(DEADLINE_FORMAT.format(message.dueAt()));
        }
        if (message.activity() != null && !message.activity().isBlank()) {
            String activity = media ? shorten(message.activity(), 160) : message.activity();
            text.append("\n\n📝 <b>Oxirgi amal:</b> ").append(escapeHtml(activity));
        }
        return text.toString();
    }

    private String assigneesText(List<Assignee> assignees, boolean media) {
        if (assignees.isEmpty()) return "Belgilanmagan";
        List<String> mentions = assignees.stream().map(this::mention).toList();
        if (!media) return String.join(", ", mentions);
        StringBuilder result = new StringBuilder();
        int shown = 0;
        for (String next : mentions) {
            String candidate = shown == 0 ? next : result + ", " + next;
            if (candidate.length() > 180 && shown > 0) break;
            result = new StringBuilder(candidate);
            shown++;
        }
        if (shown < mentions.size()) result.append(" +").append(mentions.size() - shown);
        return result.toString();
    }

    private String mention(Assignee assignee) {
        String name = escapeHtml(shorten(assignee.name(), 60));
        return assignee.telegramId() == null ? name
                : "<a href=\"tg://user?id=" + assignee.telegramId() + "\">" + name + "</a>";
    }

    private InlineKeyboardMarkup taskKeyboard(TaskStatus status, Long taskId) {
        return switch (status) {
            case NEW -> new InlineKeyboardMarkup(
                    new InlineKeyboardButton[]{
                            taskActionButton("▶️ Boshladim", "START", taskId),
                            taskActionButton("✅ Bajarildi", "COMPLETE", taskId)},
                    new InlineKeyboardButton[]{
                            taskActionButton("⚠️ Muammo bor", "PROBLEM", taskId),
                            taskActionButton("🔎 Tekshiruv", "REVIEW", taskId)});
            case IN_PROGRESS -> new InlineKeyboardMarkup(
                    new InlineKeyboardButton[]{
                            taskActionButton("⚠️ Muammo bor", "PROBLEM", taskId),
                            taskActionButton("🔎 Tekshiruvga yuborish", "REVIEW", taskId)});
            case BLOCKED -> new InlineKeyboardMarkup(taskActionButton("▶️ Davom ettirish", "RESUME", taskId));
            case REVIEW -> new InlineKeyboardMarkup(new InlineKeyboardButton[]{
                    taskActionButton("✅ Tasdiqlash", "APPROVE", taskId),
                    taskActionButton("↩️ Qayta ishlash", "RETURN", taskId)});
            case COMPLETED -> new InlineKeyboardMarkup(taskActionButton("🔄 Qayta ochish", "REOPEN", taskId));
            case CANCELLED -> new InlineKeyboardMarkup();
        };
    }

    private String statusLabel(TaskStatus status) {
        return switch (status) {
            case NEW -> "Yangi";
            case IN_PROGRESS -> "Jarayonda";
            case BLOCKED -> "Kutilmoqda";
            case REVIEW -> "Tekshiruvda";
            case COMPLETED -> "Bajarildi";
            case CANCELLED -> "Bekor qilindi";
        };
    }

    private InlineKeyboardButton taskActionButton(String text, String action, Long taskId) {
        return new InlineKeyboardButton(text).callbackData("task:action:" + action + ":" + taskId);
    }

    private boolean checkBotMembership(Long chatId) {
        try {
            Long telegramId = botTelegramId;
            if (telegramId == null) {
                GetMeResponse me = bot.execute(new GetMe());
                if (!me.isOk() || me.user() == null) return false;
                telegramId = me.user().id();
                botTelegramId = telegramId;
            }
            GetChatMemberResponse response = bot.execute(new GetChatMember(chatId, telegramId));
            if (!response.isOk() || response.chatMember() == null) return false;
            ChatMember.Status status = response.chatMember().status();
            return status != ChatMember.Status.left && status != ChatMember.Status.kicked;
        } catch (RuntimeException exception) {
            log.warn("Telegram guruhida bot holatini tekshirib bo'lmadi: {}", exception.getMessage());
            return false;
        }
    }

    private String shorten(String value, int maxLength) {
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 1) + "…";
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @PreDestroy
    void stop() {
        if (bot != null) bot.shutdown();
    }

    public record TaskTelegramMessage(Long taskId, Long chatId, Long topicId, Long messageId, String title,
                                      String description, String author, List<Assignee> assignees, Instant dueAt,
                                      TaskStatus status, String activity, TaskTelegramAttachment attachment,
                                      boolean replaceMedia) {
        TaskTelegramMessage withChatId(Long newChatId) {
            return new TaskTelegramMessage(taskId, newChatId, topicId, messageId, title, description, author,
                    assignees, dueAt, status, activity, attachment, replaceMedia);
        }
    }

    public record Assignee(String name, Long telegramId) {}

    public record TaskTelegramAttachment(String name, String contentType, byte[] bytes, boolean photo) {}

    public record ReminderTelegramMessage(Long taskId, Long chatId, String title, Instant dueAt,
                                          boolean groupMessage, boolean overdue, Long messageId, Long topicId,
                                          List<Assignee> assignees, Long previousReminderMessageId) {
        public ReminderTelegramMessage {
            if (assignees == null) assignees = List.of();
        }
    }

    private record CachedBotStatus(boolean member, Instant expiresAt) {
    }
}

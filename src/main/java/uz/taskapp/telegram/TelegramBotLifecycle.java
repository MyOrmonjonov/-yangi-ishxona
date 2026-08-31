package uz.taskapp.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.BotCommand;
import com.pengrad.telegrambot.model.Chat;
import com.pengrad.telegrambot.model.ChatMember;
import com.pengrad.telegrambot.model.ChatMemberUpdated;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.WebAppInfo;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.PinChatMessage;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetMyCommands;
import com.pengrad.telegrambot.request.AnswerCallbackQuery;
import com.pengrad.telegrambot.response.SendResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uz.taskapp.config.TelegramProperties;
import uz.taskapp.group.GroupService;
import uz.taskapp.group.TelegramKnownChatEntity;
import uz.taskapp.group.TelegramKnownChatRepository;
import uz.taskapp.user.UserEntity;
import uz.taskapp.user.UserRepository;
import uz.taskapp.task.TaskService;
import uz.taskapp.common.ApiException;

import java.util.List;

@Component
public class TelegramBotLifecycle {
    private static final Logger log = LoggerFactory.getLogger(TelegramBotLifecycle.class);

    private final TelegramProperties properties;
    private final UserRepository userRepository;
    private final GroupService groupService;
    private final TaskService taskService;
    private final TelegramKnownChatRepository knownChatRepository;
    private final TelegramCommandService commandService;
    private final TelegramFileDownloader fileDownloader;
    private TelegramBot bot;

    public TelegramBotLifecycle(TelegramProperties properties, UserRepository userRepository,
                                GroupService groupService, TaskService taskService,
                                TelegramKnownChatRepository knownChatRepository,
                                TelegramCommandService commandService, TelegramFileDownloader fileDownloader) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.taskService = taskService;
        this.knownChatRepository = knownChatRepository;
        this.commandService = commandService;
        this.fileDownloader = fileDownloader;
    }

    @PostConstruct
    void start() {
        if (properties.botToken() == null || properties.botToken().isBlank()) {
            log.info("TELEGRAM_BOT_TOKEN berilmagan, bot polling ishga tushirilmadi");
            return;
        }
        bot = new TelegramBot(properties.botToken());
        bot.setUpdatesListener(this::handleUpdates, exception ->
                log.error("Telegram polling xatosi", exception));
        bot.execute(new SetMyCommands(
                new BotCommand("start", "Ilova tugmasini qayta yuborish"),
                new BotCommand("task", "Yangi vazifa: /task @kimga bugun 17:00 matn"),
                new BotCommand("my", "Mening ochiq vazifalarim"),
                new BotCommand("due", "Muddati bor vazifalar"),
                new BotCommand("done", "Vazifani yopish: /done TASK-0001"),
                new BotCommand("members", "Ish maydoni a'zolari"),
                new BotCommand("help", "Yordam")));
        log.info("Telegram bot polling ishga tushdi");
    }

    private int handleUpdates(List<Update> updates) {
        for (Update update : updates) {
            try {
                handle(update);
            } catch (RuntimeException exception) {
                log.error("Telegram update {} qayta ishlanmadi", update.updateId(), exception);
            }
        }
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    private void handle(Update update) {
        if (update.myChatMember() != null) {
            handleMyChatMember(update.myChatMember());
            return;
        }
        if (update.callbackQuery() != null) {
            handleCallback(update.callbackQuery());
            return;
        }
        Message message = update.message();
        if (message == null) {
            return;
        }
        handleForumTopicEvent(message);
        if (message.chat() != null && message.from() != null && !Boolean.TRUE.equals(message.from().isBot())
                && (message.chat().type() == Chat.Type.group || message.chat().type() == Chat.Type.supergroup)) {
            User sender = message.from();
            groupService.syncMemberFromMessage(message.chat().id(), sender.id(), sender.firstName(),
                    sender.lastName(), sender.username(), sender.languageCode());
        }
        if (message.chatShared() != null && message.from() != null) {
            boolean accepted = groupService.acceptTelegramSelection(
                    message.from().id(),
                    message.chatShared().requestId(),
                    message.chatShared().chatId(),
                    message.chatShared().title());
            if (accepted) {
                bot.execute(new SendMessage(message.chat().id(),
                        "Guruh muvaffaqiyatli qo'shildi. Task App'ni qayta ochishingiz mumkin.")
                        .replyMarkup(new ReplyKeyboardRemove()));
            }
            return;
        }
        if (message.voice() != null && message.from() != null && !Boolean.TRUE.equals(message.from().isBot())) {
            handleVoiceMessage(message);
            return;
        }
        if (message.text() == null || !message.text().startsWith("/")) return;
        User telegramUser = message.from();
        if (telegramUser == null) {
            return;
        }
        String[] parts = message.text().substring(1).split("\\s+", 2);
        String command = parts[0].split("@", 2)[0].toLowerCase();
        String argument = parts.length > 1 ? parts[1].trim() : "";
        switch (command) {
            case "start" -> handleStart(message, telegramUser, argument);
            case "task" -> handleTaskCommand(message, telegramUser, argument);
            case "my" -> reply(message.chat().id(), commandService.handleMy(message.chat(), telegramUser));
            case "due" -> reply(message.chat().id(), commandService.handleDue(message.chat(), telegramUser));
            case "done" -> reply(message.chat().id(), commandService.handleDone(message.chat(), telegramUser, argument));
            case "members" -> reply(message.chat().id(), commandService.handleMembers(message.chat(), telegramUser));
            case "help" -> reply(message.chat().id(), commandService.handleHelp());
            default -> { }
        }
    }

    /**
     * A voice message sent directly to the bot in a private chat is transcribed right away.
     * In a group, a plain voice note is left alone - it only turns into a task when someone
     * replies to it with a bare "/task" (see {@link #handleTaskCommand}), so the bot doesn't
     * try to turn every voice note in a busy group chat into a task.
     */
    private void handleVoiceMessage(Message message) {
        if (message.chat() == null) return;
        boolean group = message.chat().type() == Chat.Type.group || message.chat().type() == Chat.Type.supergroup;
        if (group) return;
        processVoiceDraft(message.chat(), message.from(), message.voice().fileId(), message.voice().mimeType(), null);
    }

    /**
     * "/task some text", or a bare "/task" replying to someone's text/voice message - either way
     * this only stores a draft; the user reviews/edits and actually creates it in the Mini App
     * (see {@link #sendPreview}), exactly like a voice message typed directly to the bot.
     */
    private void handleTaskCommand(Message message, User telegramUser, String argument) {
        Long topicId = Boolean.TRUE.equals(message.isTopicMessage()) ? message.messageThreadId() : null;
        Message reference = message.replyToMessage();
        if (argument.isBlank() && reference != null && reference.voice() != null) {
            processVoiceDraft(message.chat(), telegramUser, reference.voice().fileId(), reference.voice().mimeType(), topicId);
            return;
        }
        String text = !argument.isBlank() ? argument
                : reference != null && reference.text() != null ? reference.text()
                : "";
        TelegramCommandService.VoicePreview preview = commandService.handleTextTask(message.chat(), telegramUser, text, topicId);
        sendPreview(message.chat(), preview, topicId);
    }

    private void processVoiceDraft(Chat chat, User telegramUser, String fileId, String mimeType, Long topicId) {
        byte[] audio;
        try {
            audio = fileDownloader.download(bot, fileId);
        } catch (RuntimeException exception) {
            log.warn("Telegram ovozli xabarini yuklab bo'lmadi", exception);
            bot.execute(new SendMessage(chat.id(), "Ovozli xabarni yuklab bo'lmadi, qayta urinib ko'ring."));
            return;
        }
        TelegramCommandService.VoicePreview preview = commandService.handleVoiceTask(
                chat, telegramUser, audio, mimeType == null ? "audio/ogg" : mimeType, topicId);
        sendPreview(chat, preview, topicId);
    }

    private void sendPreview(Chat chat, TelegramCommandService.VoicePreview preview, Long topicId) {
        switch (preview) {
            case TelegramCommandService.VoicePreview.Error error -> {
                SendMessage request = new SendMessage(chat.id(), error.message());
                if (topicId != null) request = request.messageThreadId(topicId);
                bot.execute(request);
            }
            case TelegramCommandService.VoicePreview.Pending pending -> {
                // Telegram only allows web_app buttons in private chats - in a group, "Formada
                // tekshirish va yaratish" opens a t.me/<bot>?start=... link instead, which hands
                // off to a DM where a second message (see handleStart) offers the actual web_app
                // button.
                boolean privateChat = chat.type() == Chat.Type.Private;
                InlineKeyboardButton confirm = privateChat
                        ? new InlineKeyboardButton("📝 Formada tekshirish va yaratish").webApp(new WebAppInfo(miniAppUrlWithDraft(pending.draftId())))
                        : new InlineKeyboardButton("📝 Formada tekshirish va yaratish").url(startVoiceDraftLink(pending.draftId()));
                InlineKeyboardButton cancel = new InlineKeyboardButton("Bekor qilish")
                        .callbackData("voice:cancel:" + pending.draftId());
                SendMessage request = new SendMessage(chat.id(), pending.previewText())
                        .replyMarkup(new InlineKeyboardMarkup(
                                new InlineKeyboardButton[]{confirm},
                                new InlineKeyboardButton[]{cancel}));
                if (topicId != null) request = request.messageThreadId(topicId);
                SendResponse response = bot.execute(request);
                if (response != null && response.isOk() && response.message() != null) {
                    commandService.attachDraftMessage(pending.draftId(), chat.id(), response.message().messageId());
                }
            }
        }
    }

    private String miniAppUrlWithDraft(long draftId) {
        String base = properties.miniAppUrl();
        String separator = base.contains("?") ? "&" : "?";
        return base + separator + "voiceDraftId=" + draftId;
    }

    /**
     * A "Direct Link Mini App" (see Telegram Bot API docs) - unlike a plain "?start=" deep link,
     * this opens the bot's Mini App straight away from wherever it's tapped (including a group),
     * with no DM hop through {@link #handleStart}. Requires the bot to have a Main Mini App
     * configured via @BotFather; if that's missing, Telegram falls back to the old chat-opening
     * behavior for this same link.
     */
    private String startVoiceDraftLink(long draftId) {
        String username = properties.botUsername() == null ? "" : properties.botUsername().replace("@", "").trim();
        return "https://t.me/" + username + "?startapp=voice_" + draftId;
    }

    private void reply(Long chatId, String text) {
        bot.execute(new SendMessage(chatId, text));
    }

    private void handleStart(Message message, User telegramUser, String argument) {
        UserEntity user = userRepository.findByTelegramId(telegramUser.id())
                .orElseGet(() -> new UserEntity(telegramUser.id(), telegramUser.firstName()));
        user.updateTelegramProfile(telegramUser.firstName(), telegramUser.lastName(), telegramUser.username(),
                null, telegramUser.languageCode());
        user.markBotConnected();
        userRepository.save(user);

        if (argument != null && argument.startsWith("voice_")) {
            try {
                long draftId = Long.parseLong(argument.substring("voice_".length()));
                InlineKeyboardButton openDraft = new InlineKeyboardButton("📝 Formada tekshirish va yaratish")
                        .webApp(new WebAppInfo(miniAppUrlWithDraft(draftId)));
                bot.execute(new SendMessage(message.chat().id(), "Vazifa drafti tayyor. Formada tekshirib, saqlang:")
                        .replyMarkup(new InlineKeyboardMarkup(openDraft)));
                return;
            } catch (NumberFormatException ignored) {
                // falls through to the regular welcome message below
            }
        }

        String language = telegramUser.languageCode() == null ? "uz" : telegramUser.languageCode();
        String text = switch (language) {
            case "ru" -> "Добро пожаловать! Откройте Task App кнопкой ниже.";
            case "en" -> "Welcome! Open Task App using the button below.";
            default -> "Xush kelibsiz! Quyidagi tugma orqali Task App'ni oching.";
        };
        String buttonText = switch (language) {
            case "ru" -> "Открыть приложение";
            case "en" -> "Open app";
            default -> "Ilovani ochish";
        };
        InlineKeyboardButton button = new InlineKeyboardButton(buttonText)
                .webApp(new WebAppInfo(properties.miniAppUrl()));
        SendResponse response = bot.execute(new SendMessage(message.chat().id(), text)
                .replyMarkup(new InlineKeyboardMarkup(button)));
        try {
            if (response != null && response.isOk() && response.message() != null) {
                bot.execute(new PinChatMessage(message.chat().id(), response.message().messageId()));
            }
        } catch (RuntimeException exception) {
            log.debug("Xabarni pin qilib bo'lmadi (chat={})", message.chat().id(), exception);
        }
    }

    private void handleCallback(CallbackQuery callback) {
        if (callback.data() == null) return;
        if (callback.data().startsWith("group:join:")) {
            handleGroupJoin(callback);
            return;
        }
        if (callback.data().startsWith("voice:cancel:")) {
            handleVoiceCancel(callback);
            return;
        }
        boolean legacyStart = callback.data().startsWith("task:start:");
        if (!legacyStart && !callback.data().startsWith("task:action:")) return;
        if (callback.from() == null || callback.message() == null || callback.message().chat() == null) {
            bot.execute(new AnswerCallbackQuery(callback.id()).text("Vazifani ochib bo'lmadi").showAlert(true));
            return;
        }
        try {
            TaskService.TelegramTaskAction action;
            Long taskId;
            if (legacyStart) {
                action = TaskService.TelegramTaskAction.START;
                taskId = Long.valueOf(callback.data().substring("task:start:".length()));
            } else {
                String[] parts = callback.data().split(":", 4);
                if (parts.length != 4) throw new NumberFormatException("callback format");
                action = TaskService.TelegramTaskAction.valueOf(parts[2]);
                taskId = Long.valueOf(parts[3]);
            }
            User telegramUser = callback.from();
            TaskService.TelegramActionResult result = taskService.actFromTelegram(
                    taskId, callback.message().chat().id(), telegramUser.id(), telegramUser.firstName(),
                    telegramUser.lastName(), telegramUser.username(), telegramUser.languageCode(),
                    callback.message().messageId().longValue(), action);
            String answer = switch (result.action()) {
                case START -> "Vazifa boshlandi";
                case RESUME -> "Vazifa davom ettirildi";
                case PROBLEM -> "Muammo qayd etildi";
                case REVIEW -> "Vazifa tekshiruvga yuborildi";
                case APPROVE -> "Vazifa tasdiqlandi";
                case COMPLETE -> "Vazifa bajarildi";
                case RETURN -> "Vazifa qayta ishlashga qaytarildi";
                case REOPEN -> "Vazifa qayta ochildi";
            };
            bot.execute(new AnswerCallbackQuery(callback.id()).text(answer));
        } catch (IllegalArgumentException exception) {
            bot.execute(new AnswerCallbackQuery(callback.id()).text("Vazifa raqami noto'g'ri").showAlert(true));
        } catch (ApiException exception) {
            bot.execute(new AnswerCallbackQuery(callback.id()).text(exception.getMessage()).showAlert(true));
        }
    }

    private void handleVoiceCancel(CallbackQuery callback) {
        if (callback.message() == null || callback.message().chat() == null) {
            bot.execute(new AnswerCallbackQuery(callback.id()).text("Amalni bajarib bo'lmadi").showAlert(true));
            return;
        }
        long draftId;
        try {
            draftId = Long.parseLong(callback.data().substring("voice:cancel:".length()));
        } catch (NumberFormatException exception) {
            bot.execute(new AnswerCallbackQuery(callback.id()).text("Noto'g'ri so'rov").showAlert(true));
            return;
        }
        String resultText = commandService.cancelVoiceTask(draftId);
        bot.execute(new EditMessageText(callback.message().chat().id(), callback.message().messageId(), resultText));
        bot.execute(new AnswerCallbackQuery(callback.id()));
    }

    private void handleForumTopicEvent(Message message) {
        if (message.chat() == null) return;
        if (message.forumTopicCreated() != null) {
            groupService.upsertTopic(message.chat().id(), message.messageThreadId(), message.forumTopicCreated().name());
        } else if (message.forumTopicEdited() != null && message.forumTopicEdited().name() != null) {
            groupService.upsertTopic(message.chat().id(), message.messageThreadId(), message.forumTopicEdited().name());
        } else if (message.forumTopicClosed() != null) {
            groupService.setTopicClosed(message.chat().id(), message.messageThreadId(), true);
        } else if (message.forumTopicReopened() != null) {
            groupService.setTopicClosed(message.chat().id(), message.messageThreadId(), false);
        } else if (Boolean.TRUE.equals(message.isTopicMessage()) && message.messageThreadId() != null) {
            groupService.upsertTopic(message.chat().id(), message.messageThreadId(), null);
        }
    }

    private void handleMyChatMember(ChatMemberUpdated update) {
        Chat chat = update.chat();
        ChatMember newStatus = update.newChatMember();
        if (chat == null || newStatus == null) return;
        if (chat.type() != Chat.Type.group && chat.type() != Chat.Type.supergroup) return;
        boolean active = newStatus.status() != ChatMember.Status.left
                && newStatus.status() != ChatMember.Status.kicked;
        String title = chat.title() == null || chat.title().isBlank() ? "Telegram guruhi" : chat.title();
        TelegramKnownChatEntity known = knownChatRepository.findById(chat.id())
                .orElseGet(() -> new TelegramKnownChatEntity(chat.id(), title));
        known.refresh(title, active);
        knownChatRepository.save(known);
    }

    private void handleGroupJoin(CallbackQuery callback) {
        if (callback.from() == null || callback.message() == null || callback.message().chat() == null) {
            bot.execute(new AnswerCallbackQuery(callback.id()).text("Guruhga qo'shib bo'lmadi").showAlert(true));
            return;
        }
        try {
            Long groupId = Long.valueOf(callback.data().substring("group:join:".length()));
            User telegramUser = callback.from();
            GroupService.GroupJoinResult result = groupService.joinFromTelegram(
                    groupId, callback.message().chat().id(), telegramUser.id(), telegramUser.firstName(),
                    telegramUser.lastName(), telegramUser.username(), telegramUser.languageCode());
            String answer = result.added()
                    ? "Siz Task App guruhiga qo'shildingiz"
                    : "Siz allaqachon Task App guruhidasiz";
            bot.execute(new AnswerCallbackQuery(callback.id()).text(answer).showAlert(true));
        } catch (NumberFormatException exception) {
            bot.execute(new AnswerCallbackQuery(callback.id()).text("Guruh raqami noto'g'ri").showAlert(true));
        } catch (ApiException exception) {
            bot.execute(new AnswerCallbackQuery(callback.id()).text(exception.getMessage()).showAlert(true));
        }
    }

    @PreDestroy
    void stop() {
        if (bot != null) {
            bot.shutdown();
        }
    }
}

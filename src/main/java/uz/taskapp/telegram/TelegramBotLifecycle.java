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
    private TelegramBot bot;

    public TelegramBotLifecycle(TelegramProperties properties, UserRepository userRepository,
                                GroupService groupService, TaskService taskService,
                                TelegramKnownChatRepository knownChatRepository,
                                TelegramCommandService commandService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.groupService = groupService;
        this.taskService = taskService;
        this.knownChatRepository = knownChatRepository;
        this.commandService = commandService;
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
            case "my" -> reply(message.chat().id(), commandService.handleMy(message.chat(), telegramUser));
            case "due" -> reply(message.chat().id(), commandService.handleDue(message.chat(), telegramUser));
            case "done" -> reply(message.chat().id(), commandService.handleDone(message.chat(), telegramUser, argument));
            case "members" -> reply(message.chat().id(), commandService.handleMembers(message.chat(), telegramUser));
            case "help" -> reply(message.chat().id(), commandService.handleHelp());
            default -> { }
        }
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

package org.example.yangi_ishxona.bot;

import lombok.extern.slf4j.Slf4j;
import org.example.yangi_ishxona.domain.*;
import org.example.yangi_ishxona.service.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.DefaultLongPollingUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Document;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.photo.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Telegram bot per ТЗ 3.1-3.6: registration, project/sprint/task creation wizards,
 * /mytasks with inline status buttons, mandatory attachment before "На проверке",
 * deadline-postpone approval flow, role management, and (per later request) a
 * 3-language interface (UZ/RU/EN) plus a Mini App entry point.
 */
@Slf4j
@Component
public class YangiIshxonaBot extends DefaultLongPollingUpdateConsumer implements TelegramNotifier {

    private final TelegramClient client;
    private final UserService userService;
    private final ProjectService projectService;
    private final SprintService sprintService;
    private final TaskService taskService;
    private final Messages messages;
    private final String miniAppBaseUrl;

    private final java.util.Map<Long, ChatSession> sessions = new ConcurrentHashMap<>();

    public YangiIshxonaBot(@Value("${telegram.bot.token}") String botToken,
                            @Value("${app.miniapp.base-url}") String miniAppBaseUrl,
                            UserService userService,
                            ProjectService projectService,
                            SprintService sprintService,
                            TaskService taskService,
                            Messages messages) {
        this.client = new OkHttpTelegramClient(botToken);
        this.miniAppBaseUrl = miniAppBaseUrl;
        this.userService = userService;
        this.projectService = projectService;
        this.sprintService = sprintService;
        this.taskService = taskService;
        this.messages = messages;
    }

    // ------------------------------------------------------------------ //
    // Update dispatch
    // ------------------------------------------------------------------ //

    @Override
    public void consume(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallback(update.getCallbackQuery());
            } else if (update.hasMessage()) {
                handleMessage(update.getMessage());
            }
        } catch (Exception e) {
            log.error("Ошибка обработки обновления Telegram", e);
        }
    }

    private void handleMessage(Message message) {
        User from = message.getFrom();
        if (from == null || Boolean.TRUE.equals(from.getIsBot())) {
            return;
        }
        Long chatId = message.getChatId();
        Long telegramUserId = from.getId();
        ChatSession session = sessions.computeIfAbsent(chatId, id -> new ChatSession());

        Optional<AppUser> userOpt = userService.findByTelegramId(telegramUserId);
        if (userOpt.isEmpty()) {
            handleRegistration(message, chatId, telegramUserId, from, session);
            return;
        }
        AppUser user = userOpt.get();

        if (message.hasDocument() || message.hasPhoto()) {
            handleAttachmentUpload(message, chatId, user, session);
            return;
        }
        if (!message.hasText()) {
            return;
        }
        String text = message.getText().trim();
        if (text.startsWith("/")) {
            handleCommand(text, chatId, user, session);
        } else {
            handleTextInState(text, chatId, user, session);
        }
    }

    // ------------------------------------------------------------------ //
    // Registration (ФИО -> должность -> язык интерфейса)
    // ------------------------------------------------------------------ //

    private void handleRegistration(Message message, Long chatId, Long telegramUserId, User from, ChatSession session) {
        Language lang = session.getRegLanguage() == null ? Language.RU : session.getRegLanguage();
        if (!message.hasText()) {
            send(chatId, lang, "bot.pleaseReplyText");
            return;
        }
        String text = message.getText().trim();

        if (session.getState() == ChatState.REG_FULLNAME) {
            if (text.isBlank()) {
                send(chatId, lang, "bot.reg.fullNameEmpty");
                return;
            }
            session.setRegFullName(text);
            session.setState(ChatState.REG_POSITION);
            send(chatId, lang, "bot.reg.askPosition");
            return;
        }
        if (session.getState() == ChatState.REG_POSITION) {
            if (text.isBlank()) {
                send(chatId, lang, "bot.reg.positionEmpty");
                return;
            }
            session.setRegPosition(text);
            session.setState(ChatState.REG_LANGUAGE);
            sendLanguageButtons(chatId, lang, "bot.reg.askLanguage", "reglang");
            return;
        }

        session.setState(ChatState.REG_FULLNAME);
        send(chatId, lang, "bot.reg.askFullName");
    }

    private void sendLanguageButtons(Long chatId, Language lang, String promptKey, String callbackPrefix) {
        List<NotifyButton> buttons = List.of(
                new NotifyButton("🇺🇿 " + messages.t(lang, "common.language.UZ"), callbackPrefix + ":UZ"),
                new NotifyButton("🇷🇺 " + messages.t(lang, "common.language.RU"), callbackPrefix + ":RU"),
                new NotifyButton("🇬🇧 " + messages.t(lang, "common.language.EN"), callbackPrefix + ":EN")
        );
        sendMessage(chatId, messages.t(lang, promptKey), Keyboards.singleColumn(buttons));
    }

    // ------------------------------------------------------------------ //
    // Commands
    // ------------------------------------------------------------------ //

    private void handleCommand(String text, Long chatId, AppUser user, ChatSession session) {
        Language lang = user.getLanguage();
        String command = text.split("\\s+")[0].toLowerCase();
        switch (command) {
            case "/start" -> {
                session.reset();
                sendMessage(chatId, messages.t(lang, "bot.greeting", user.getFullName()) + "\n\n" + messages.t(lang, "bot.menu"),
                        mainMenuKeyboard(lang));
            }
            case "/cancel" -> {
                session.reset();
                send(chatId, lang, "bot.cancelled");
            }
            case "/newproject" -> {
                session.reset();
                session.setState(ChatState.NEW_PROJECT_NAME);
                send(chatId, lang, "bot.newproject.askName");
            }
            case "/newsprint" -> startNewSprint(chatId, user, session);
            case "/newtask" -> startNewTask(chatId, user, session);
            case "/mytasks" -> showMyTasks(chatId, user);
            case "/setrole" -> startSetRole(chatId, user, session);
            case "/language" -> sendLanguageButtons(chatId, lang, "bot.reg.askLanguage", "picklang");
            default -> send(chatId, lang, "bot.unknownCommand");
        }
    }

    private InlineKeyboardMarkup mainMenuKeyboard(Language lang) {
        // Telegram rejects web_app buttons whose url isn't https:// - skip the button rather
        // than let the whole message silently fail to send (matters for local http:// dev).
        if (miniAppBaseUrl == null || !miniAppBaseUrl.startsWith("https://")) {
            return null;
        }
        InlineKeyboardRow row = new InlineKeyboardRow();
        row.add(InlineKeyboardButton.builder()
                .text(messages.t(lang, "bot.button.miniapp"))
                .webApp(WebAppInfo.builder().url(miniAppBaseUrl).build())
                .build());
        return InlineKeyboardMarkup.builder().keyboardRow(row).build();
    }

    // ------------------------------------------------------------------ //
    // New project / sprint / task wizards - text steps
    // ------------------------------------------------------------------ //

    private void handleTextInState(String text, Long chatId, AppUser user, ChatSession session) {
        Language lang = user.getLanguage();
        switch (session.getState()) {
            case NEW_PROJECT_NAME -> {
                session.setDraftName(text);
                session.setState(ChatState.NEW_PROJECT_DEADLINE);
                send(chatId, lang, "bot.newproject.askDeadline");
            }
            case NEW_PROJECT_DEADLINE -> {
                LocalDate deadline = DeadlineParser.parse(text);
                if (deadline == null) {
                    send(chatId, lang, "bot.deadline.invalid");
                    return;
                }
                session.setDraftDeadline(deadline);
                session.setState(ChatState.NEW_PROJECT_DESCRIPTION);
                send(chatId, lang, "bot.newproject.askDescription");
            }
            case NEW_PROJECT_DESCRIPTION -> {
                try {
                    Project project = projectService.create(user, session.getDraftName(),
                            "-".equals(text) ? null : text, null, session.getDraftDeadline());
                    session.reset();
                    send(chatId, lang, "bot.newproject.created", project.getName(), project.getId(),
                            DeadlineParser.format(project.getDeadline()));
                } catch (DomainException e) {
                    sendError(chatId, lang, e);
                }
            }
            case NEW_SPRINT_NAME -> {
                session.setDraftName(text);
                session.setState(ChatState.NEW_SPRINT_DEADLINE);
                send(chatId, lang, "bot.newsprint.askDeadline");
            }
            case NEW_SPRINT_DEADLINE -> {
                LocalDate deadline = DeadlineParser.parse(text);
                if (deadline == null) {
                    send(chatId, lang, "bot.deadline.invalid");
                    return;
                }
                try {
                    Project project = projectService.findById(session.getSelectedProjectId())
                            .orElseThrow(() -> new DomainException("error.project.notFound"));
                    Sprint sprint = sprintService.create(user, project, session.getDraftName(), deadline);
                    session.reset();
                    send(chatId, lang, "bot.newsprint.created", sprint.getName(), sprint.getId(),
                            DeadlineParser.format(sprint.getDeadline()));
                } catch (DomainException e) {
                    sendError(chatId, lang, e);
                }
            }
            case NEW_TASK_NAME -> {
                session.setDraftName(text);
                session.setState(ChatState.NEW_TASK_SELECT_EXECUTOR);
                sendExecutorSelection(chatId, lang);
            }
            case NEW_TASK_DEADLINE -> {
                LocalDate deadline = DeadlineParser.parse(text);
                if (deadline == null) {
                    send(chatId, lang, "bot.deadline.invalid");
                    return;
                }
                session.setDraftDeadline(deadline);
                session.setState(ChatState.NEW_TASK_DESCRIPTION);
                send(chatId, lang, "bot.newtask.askDescription");
            }
            case NEW_TASK_DESCRIPTION -> {
                try {
                    Sprint sprint = sprintService.findById(session.getSelectedSprintId())
                            .orElseThrow(() -> new DomainException("error.sprint.notFound"));
                    AppUser executor = userService.findById(session.getSelectedUserId())
                            .orElseThrow(() -> new DomainException("error.executor.notFound"));
                    Task task = taskService.create(sprint, session.getDraftName(),
                            "-".equals(text) ? null : text, executor, session.getDraftDeadline());
                    session.reset();
                    send(chatId, lang, "bot.newtask.created", task.getName(), task.getId(),
                            executor.getFullName(), DeadlineParser.format(task.getDeadline()));
                    if (!executor.getTelegramUserId().equals(user.getTelegramUserId())) {
                        send(executor.getTelegramUserId(), executor.getLanguage(), "bot.newtask.assigned",
                                task.getName(), DeadlineParser.format(task.getDeadline()));
                    }
                } catch (DomainException e) {
                    sendError(chatId, lang, e);
                }
            }
            case AWAIT_COMMENT -> {
                try {
                    Task task = requireTask(session);
                    taskService.addComment(user, task, text);
                    session.reset();
                    send(chatId, lang, "bot.comment.added");
                } catch (DomainException e) {
                    sendError(chatId, lang, e);
                }
            }
            case AWAIT_CANCEL_REASON -> {
                try {
                    Task task = requireTask(session);
                    Task updated = taskService.changeStatus(user, task, TaskStatus.CANCELLED, text);
                    session.reset();
                    send(chatId, lang, "bot.task.cancelled");
                    notifyOther(updated.getExecutor(), user, "bot.task.cancelledNotify", updated.getName(), text);
                } catch (DomainException e) {
                    sendError(chatId, lang, e);
                }
            }
            case AWAIT_POSTPONE_DEADLINE -> {
                LocalDate deadline = DeadlineParser.parse(text);
                if (deadline == null) {
                    send(chatId, lang, "bot.deadline.invalid");
                    return;
                }
                try {
                    Task task = requireTask(session);
                    DeadlineChangeRequest request = taskService.requestDeadlineChange(user, task, deadline);
                    session.reset();
                    send(chatId, lang, "bot.postpone.sent");
                    AppUser manager = task.getSprint().getProject().getResponsible();
                    String prompt = messages.t(manager.getLanguage(), "bot.postpone.managerPrompt",
                            user.getFullName(), task.getName(), DeadlineParser.format(deadline));
                    sendMessage(manager.getTelegramUserId(), prompt, Keyboards.singleColumn(List.of(
                            new NotifyButton(messages.t(manager.getLanguage(), "bot.postpone.approve"), "postpone_approve:" + request.getId()),
                            new NotifyButton(messages.t(manager.getLanguage(), "bot.postpone.reject"), "postpone_reject:" + request.getId())
                    )));
                } catch (DomainException e) {
                    sendError(chatId, lang, e);
                }
            }
            default -> send(chatId, lang, "bot.fallback");
        }
    }

    private Task requireTask(ChatSession session) {
        return taskService.findById(session.getSelectedTaskId())
                .orElseThrow(() -> new DomainException("error.task.notFound"));
    }

    // ------------------------------------------------------------------ //
    // Sprint / task creation - selection steps
    // ------------------------------------------------------------------ //

    private void startNewSprint(Long chatId, AppUser user, ChatSession session) {
        Language lang = user.getLanguage();
        List<Project> projects = projectService.allForSelection();
        if (projects.isEmpty()) {
            send(chatId, lang, "bot.newsprint.noProjects");
            return;
        }
        session.reset();
        session.setState(ChatState.NEW_SPRINT_SELECT_PROJECT);
        sendProjectSelection(chatId, lang, projects);
    }

    private void startNewTask(Long chatId, AppUser user, ChatSession session) {
        Language lang = user.getLanguage();
        List<Project> projects = projectService.allForSelection();
        if (projects.isEmpty()) {
            send(chatId, lang, "bot.newtask.noProjects");
            return;
        }
        session.reset();
        session.setState(ChatState.NEW_TASK_SELECT_PROJECT);
        sendProjectSelection(chatId, lang, projects);
    }

    private void sendProjectSelection(Long chatId, Language lang, List<Project> projects) {
        List<NotifyButton> buttons = projects.stream()
                .map(p -> new NotifyButton(p.getName() + " (" + DeadlineParser.format(p.getDeadline()) + ")",
                        "selproj:" + p.getId()))
                .toList();
        sendMessage(chatId, messages.t(lang, "bot.selectProject"), Keyboards.singleColumn(buttons));
    }

    private void sendSprintSelection(Long chatId, Language lang, List<Sprint> sprints) {
        List<NotifyButton> buttons = sprints.stream()
                .map(s -> new NotifyButton(s.getName() + " (" + DeadlineParser.format(s.getDeadline()) + ")",
                        "selsprint:" + s.getId()))
                .toList();
        sendMessage(chatId, messages.t(lang, "bot.selectSprint"), Keyboards.singleColumn(buttons));
    }

    private void sendExecutorSelection(Long chatId, Language lang) {
        List<NotifyButton> buttons = userService.allUsers().stream()
                .map(u -> new NotifyButton(u.getFullName() + " — " + u.getPosition(), "selexec:" + u.getId()))
                .toList();
        sendMessage(chatId, messages.t(lang, "bot.selectExecutor"), Keyboards.singleColumn(buttons));
    }

    // ------------------------------------------------------------------ //
    // /mytasks
    // ------------------------------------------------------------------ //

    private void showMyTasks(Long chatId, AppUser user) {
        Language lang = user.getLanguage();
        List<Task> tasks = taskService.myTasks(user);
        if (tasks.isEmpty()) {
            send(chatId, lang, "bot.mytasks.empty");
            return;
        }
        sendMessage(chatId, messages.t(lang, "bot.mytasks.header", tasks.size()), null);
        for (Task task : tasks) {
            String text = messages.t(lang, "bot.mytasks.taskLine", task.getName(),
                    task.getSprint().getProject().getName(), task.getSprint().getName(),
                    messages.t(lang, "common.taskStatus." + task.getStatus().name()),
                    DeadlineParser.format(task.getDeadline()));
            List<NotifyButton> row = new ArrayList<>();
            if (task.getStatus() == TaskStatus.NEW) {
                row.add(new NotifyButton(messages.t(lang, "bot.button.inProgress"), "status:IN_PROGRESS:" + task.getId()));
            } else if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                row.add(new NotifyButton(messages.t(lang, "bot.button.review"), "status:REVIEW:" + task.getId()));
                row.add(new NotifyButton(messages.t(lang, "bot.button.postpone"), "postpone:" + task.getId()));
            }
            if (task.getStatus() != TaskStatus.DONE && task.getStatus() != TaskStatus.CANCELLED) {
                row.add(new NotifyButton(messages.t(lang, "bot.button.comment"), "comment:" + task.getId()));
            }
            if (row.isEmpty()) {
                sendMessage(chatId, text, null);
            } else {
                sendMessage(chatId, text, Keyboards.fromRows(List.of(row)));
            }
        }
    }

    // ------------------------------------------------------------------ //
    // /setrole
    // ------------------------------------------------------------------ //

    private void startSetRole(Long chatId, AppUser actor, ChatSession session) {
        Language lang = actor.getLanguage();
        if (actor.getRole() != Role.DIRECTOR) {
            send(chatId, lang, "bot.setrole.onlyDirector");
            return;
        }
        session.reset();
        List<NotifyButton> buttons = userService.allUsers().stream()
                .map(u -> new NotifyButton(u.getFullName() + " (" + messages.t(lang, "common.role." + u.getRole()) + ")",
                        "selrole_user:" + u.getId()))
                .toList();
        sendMessage(chatId, messages.t(lang, "bot.setrole.selectUser"), Keyboards.singleColumn(buttons));
    }

    // ------------------------------------------------------------------ //
    // Callback queries
    // ------------------------------------------------------------------ //

    private void handleCallback(CallbackQuery cb) {
        String data = cb.getData();
        Long chatId = cb.getMessage().getChatId();
        Long telegramUserId = cb.getFrom().getId();
        answerCallback(cb.getId());

        String[] parts = data.split(":");
        String action = parts[0];

        if (action.equals("reglang")) {
            // language chosen during registration - user doesn't exist yet
            ChatSession session = sessions.computeIfAbsent(chatId, id -> new ChatSession());
            Language lang = Language.valueOf(parts[1]);
            session.setRegLanguage(lang);
            AppUser user = userService.registerOrGet(telegramUserId, cb.getFrom().getUserName(),
                    session.getRegFullName(), session.getRegPosition());
            userService.updateLanguage(user, lang);
            session.reset();
            sendMessage(chatId, messages.t(lang, "bot.reg.done", user.getFullName(), messages.t(lang, "common.role." + user.getRole()))
                    + "\n\n" + messages.t(lang, "bot.menu"), mainMenuKeyboard(lang));
            return;
        }

        Optional<AppUser> userOpt = userService.findByTelegramId(telegramUserId);
        if (userOpt.isEmpty()) {
            sendMessage(chatId, messages.t(Language.RU, "error.session.invalid"), null);
            return;
        }
        AppUser user = userOpt.get();
        Language lang = user.getLanguage();
        ChatSession session = sessions.computeIfAbsent(chatId, id -> new ChatSession());

        try {
            switch (action) {
                case "picklang" -> {
                    Language newLang = Language.valueOf(parts[1]);
                    userService.updateLanguage(user, newLang);
                    send(chatId, newLang, "bot.language.changed");
                }
                case "selproj" -> onSelectProject(chatId, user, session, Long.parseLong(parts[1]));
                case "selsprint" -> onSelectSprint(chatId, lang, session, Long.parseLong(parts[1]));
                case "selexec" -> onSelectExecutor(chatId, lang, session, Long.parseLong(parts[1]));
                case "status" -> onStatusChange(chatId, user, session, parts[1], Long.parseLong(parts[2]));
                case "cancel_task" -> {
                    session.setSelectedTaskId(Long.parseLong(parts[1]));
                    session.setState(ChatState.AWAIT_CANCEL_REASON);
                    send(chatId, lang, "bot.cancelReason.ask");
                }
                case "comment", "report" -> {
                    session.setSelectedTaskId(Long.parseLong(parts[1]));
                    session.setState(ChatState.AWAIT_COMMENT);
                    send(chatId, lang, "bot.comment.ask");
                }
                case "review" -> {
                    session.setSelectedTaskId(Long.parseLong(parts[1]));
                    session.setState(ChatState.AWAIT_REVIEW_ATTACHMENT);
                    send(chatId, lang, "bot.review.askAttachment");
                }
                case "postpone" -> {
                    session.setSelectedTaskId(Long.parseLong(parts[1]));
                    session.setState(ChatState.AWAIT_POSTPONE_DEADLINE);
                    send(chatId, lang, "bot.postpone.ask");
                }
                case "postpone_approve" -> onPostponeResolve(chatId, user, Long.parseLong(parts[1]), true);
                case "postpone_reject" -> onPostponeResolve(chatId, user, Long.parseLong(parts[1]), false);
                case "selrole_user" -> {
                    session.setSelectedUserId(Long.parseLong(parts[1]));
                    sendMessage(chatId, messages.t(lang, "bot.setrole.selectRole"), Keyboards.singleColumn(List.of(
                            new NotifyButton(messages.t(lang, "common.role.EMPLOYEE"), "setrole_apply:EMPLOYEE"),
                            new NotifyButton(messages.t(lang, "common.role.PROJECT_MANAGER"), "setrole_apply:PROJECT_MANAGER"),
                            new NotifyButton(messages.t(lang, "common.role.DIRECTOR"), "setrole_apply:DIRECTOR")
                    )));
                }
                case "setrole_apply" -> {
                    AppUser target = userService.findById(session.getSelectedUserId())
                            .orElseThrow(() -> new DomainException("error.user.notFound"));
                    Role newRole = Role.valueOf(parts[1]);
                    userService.setRole(user, target, newRole);
                    session.reset();
                    send(chatId, lang, "bot.setrole.updated", target.getFullName(), messages.t(lang, "common.role." + newRole));
                }
                default -> log.warn("Неизвестный callback: {}", data);
            }
        } catch (DomainException e) {
            sendError(chatId, lang, e);
        }
    }

    private void onSelectProject(Long chatId, AppUser user, ChatSession session, Long projectId) {
        Language lang = user.getLanguage();
        session.setSelectedProjectId(projectId);
        if (session.getState() == ChatState.NEW_SPRINT_SELECT_PROJECT) {
            session.setState(ChatState.NEW_SPRINT_NAME);
            send(chatId, lang, "bot.newsprint.askName");
        } else if (session.getState() == ChatState.NEW_TASK_SELECT_PROJECT) {
            Project project = projectService.findById(projectId).orElseThrow(() -> new DomainException("error.project.notFound"));
            List<Sprint> sprints = sprintService.findByProject(project);
            if (sprints.isEmpty()) {
                send(chatId, lang, "bot.newtask.noSprints");
                session.reset();
                return;
            }
            session.setState(ChatState.NEW_TASK_SELECT_SPRINT);
            sendSprintSelection(chatId, lang, sprints);
        }
    }

    private void onSelectSprint(Long chatId, Language lang, ChatSession session, Long sprintId) {
        session.setSelectedSprintId(sprintId);
        session.setState(ChatState.NEW_TASK_NAME);
        send(chatId, lang, "bot.newtask.askName");
    }

    private void onSelectExecutor(Long chatId, Language lang, ChatSession session, Long userId) {
        session.setSelectedUserId(userId);
        session.setState(ChatState.NEW_TASK_DEADLINE);
        send(chatId, lang, "bot.newtask.askDeadline");
    }

    private void onStatusChange(Long chatId, AppUser user, ChatSession session, String statusName, Long taskId) {
        Language lang = user.getLanguage();
        if ("REVIEW".equals(statusName)) {
            session.setSelectedTaskId(taskId);
            session.setState(ChatState.AWAIT_REVIEW_ATTACHMENT);
            send(chatId, lang, "bot.review.askAttachment");
            return;
        }
        Task task = taskService.findById(taskId).orElseThrow(() -> new DomainException("error.task.notFound"));
        TaskStatus newStatus = TaskStatus.valueOf(statusName);
        Task updated = taskService.changeStatus(user, task, newStatus, null);
        send(chatId, lang, "bot.status.updated", messages.t(lang, "common.taskStatus." + updated.getStatus().name()));
        if (newStatus == TaskStatus.DONE) {
            notifyOther(updated.getExecutor(), user, "bot.status.acceptedNotify", updated.getName());
        }
    }

    private void onPostponeResolve(Long chatId, AppUser actor, Long requestId, boolean approve) {
        Language lang = actor.getLanguage();
        DeadlineChangeRequest request = taskService.findDeadlineChangeRequest(requestId)
                .orElseThrow(() -> new DomainException("error.deadlineChange.notFound"));
        Task task = taskService.resolveDeadlineChange(actor, request, approve);
        send(chatId, lang, approve ? "bot.postpone.approved" : "bot.postpone.rejected");
        AppUser requester = request.getRequestedBy();
        if (approve) {
            notifyOther(requester, actor, "bot.postpone.outcomeApproved", task.getName(), DeadlineParser.format(task.getDeadline()));
        } else {
            notifyOther(requester, actor, "bot.postpone.outcomeRejected", task.getName());
        }
    }

    // ------------------------------------------------------------------ //
    // File attachment upload (mandatory before "На проверке")
    // ------------------------------------------------------------------ //

    private void handleAttachmentUpload(Message message, Long chatId, AppUser user, ChatSession session) {
        Language lang = user.getLanguage();
        if (session.getState() != ChatState.AWAIT_REVIEW_ATTACHMENT) {
            send(chatId, lang, "bot.review.notNow");
            return;
        }
        String fileId;
        String fileName;
        Long fileSize;
        if (message.hasDocument()) {
            Document doc = message.getDocument();
            fileId = doc.getFileId();
            fileName = doc.getFileName();
            fileSize = doc.getFileSize();
        } else {
            List<PhotoSize> photos = message.getPhoto();
            PhotoSize best = photos.get(photos.size() - 1);
            fileId = best.getFileId();
            fileName = "photo.jpg";
            fileSize = best.getFileSize() == null ? null : best.getFileSize().longValue();
        }
        try {
            Task task = requireTask(session);
            taskService.addAttachment(user, task, fileId, fileName, fileSize);
            Task updated = taskService.changeStatus(user, task, TaskStatus.REVIEW, null);
            session.reset();
            send(chatId, lang, "bot.review.done", updated.getName());
            AppUser manager = updated.getSprint().getProject().getResponsible();
            notifyOther(manager, user, "bot.review.managerNotify", updated.getName(), user.getFullName());
        } catch (DomainException e) {
            sendError(chatId, lang, e);
        }
    }

    // ------------------------------------------------------------------ //
    // Low-level Telegram calls / TelegramNotifier implementation
    // ------------------------------------------------------------------ //

    private void send(Long chatId, Language lang, String key, Object... args) {
        sendMessage(chatId, messages.t(lang, key, args), null);
    }

    private void sendError(Long chatId, Language lang, DomainException e) {
        sendMessage(chatId, "⚠️ " + messages.t(lang, e.getMessageKey(), (Object[]) e.getArgs()), null);
    }

    private void notifyOther(AppUser target, AppUser actor, String key, Object... args) {
        if (!target.getTelegramUserId().equals(actor.getTelegramUserId())) {
            send(target.getTelegramUserId(), target.getLanguage(), key, args);
        }
    }

    private void sendMessage(Long chatId, String text, InlineKeyboardMarkup markup) {
        try {
            SendMessage sendMessage = SendMessage.builder()
                    .chatId(String.valueOf(chatId))
                    .text(text)
                    .replyMarkup(markup)
                    .build();
            client.execute(sendMessage);
        } catch (TelegramApiException e) {
            log.error("Не удалось отправить сообщение chatId={}", chatId, e);
        }
    }

    private void answerCallback(String callbackQueryId) {
        try {
            client.execute(AnswerCallbackQuery.builder().callbackQueryId(callbackQueryId).build());
        } catch (TelegramApiException e) {
            log.error("Не удалось ответить на callback {}", callbackQueryId, e);
        }
    }

    @Override
    public void sendMessage(Long telegramUserId, String text) {
        sendMessage(telegramUserId, text, null);
    }

    @Override
    public void sendMessageWithButtons(Long telegramUserId, String text, List<List<NotifyButton>> buttonRows) {
        sendMessage(telegramUserId, text, Keyboards.fromRows(buttonRows));
    }
}

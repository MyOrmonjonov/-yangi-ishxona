package org.example.yangi_ishxona.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.*;
import org.example.yangi_ishxona.service.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * JSON API for the Telegram Mini App (added per later request, alongside the chat commands
 * which stay the primary interface). Every call is authenticated via the
 * {@code X-Telegram-Init-Data} header (see {@link TelegramWebAppAuthService}) - no session/cookies,
 * since the Mini App runs inside Telegram's WebView. Reuses the same Project/Sprint/TaskService
 * methods as the bot and the dashboard - no duplicated business logic.
 */
@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
public class MiniAppController {

    private static final String INIT_DATA_HEADER = "X-Telegram-Init-Data";

    private final TelegramWebAppAuthService webAppAuthService;
    private final UserService userService;
    private final ProjectService projectService;
    private final SprintService sprintService;
    private final TaskService taskService;
    private final Messages messages;
    private final RowMapper rowMapper;

    private AppUser resolveUser(HttpServletRequest request) {
        String initData = request.getHeader(INIT_DATA_HEADER);
        Long telegramUserId = webAppAuthService.verifyAndExtractUserId(initData)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Telegram init data"));
        return userService.findByTelegramId(telegramUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Not registered - send /start to the bot first"));
    }

    @GetMapping("/me")
    public MeResponse me(HttpServletRequest request) {
        AppUser user = resolveUser(request);
        return new MeResponse(user.getId(), user.getFullName(), user.getPosition(), user.getRole().name(),
                messages.t(user.getLanguage(), "common.role." + user.getRole()), user.getLanguage().name());
    }

    @PostMapping("/language")
    public MeResponse setLanguage(HttpServletRequest request, @RequestBody LanguageRequest body) {
        AppUser user = resolveUser(request);
        Language language;
        try {
            language = Language.valueOf(body.language().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown language");
        }
        AppUser updated = userService.updateLanguage(user, language);
        return new MeResponse(updated.getId(), updated.getFullName(), updated.getPosition(), updated.getRole().name(),
                messages.t(updated.getLanguage(), "common.role." + updated.getRole()), updated.getLanguage().name());
    }

    @GetMapping("/users")
    public List<UserOption> users(HttpServletRequest request) {
        resolveUser(request);
        return userService.allUsers().stream()
                .map(u -> new UserOption(u.getId(), u.getFullName(), u.getPosition()))
                .toList();
    }

    @GetMapping("/projects")
    public List<ProjectRow> projects(HttpServletRequest request) {
        AppUser user = resolveUser(request);
        return projectService.visibleProjectsFor(user).stream()
                .map(p -> rowMapper.toProjectRow(p, user.getLanguage()))
                .toList();
    }

    @PostMapping("/projects")
    public ProjectRow createProject(@RequestBody NewProjectRequest body, HttpServletRequest request) {
        AppUser user = resolveUser(request);
        LocalDate deadline = DeadlineParser.parse(body.deadline());
        if (deadline == null) {
            throw new DomainException("bot.deadline.invalid");
        }
        Project project = projectService.create(user, body.name(), body.description(), null, deadline);
        return rowMapper.toProjectRow(project, user.getLanguage());
    }

    @PostMapping("/projects/{projectId}/sprints")
    public SprintRow createSprint(@PathVariable Long projectId, @RequestBody NewSprintRequest body, HttpServletRequest request) {
        AppUser user = resolveUser(request);
        Project project = projectService.getForView(user, projectId);
        LocalDate deadline = DeadlineParser.parse(body.deadline());
        if (deadline == null) {
            throw new DomainException("bot.deadline.invalid");
        }
        Sprint sprint = sprintService.create(user, project, body.name(), deadline);
        return rowMapper.toSprintRow(sprint, user.getLanguage());
    }

    @PostMapping("/projects/{projectId}/sprints/{sprintId}/tasks")
    public TaskRow createTask(@PathVariable Long projectId, @PathVariable Long sprintId,
                               @RequestBody NewTaskRequest body, HttpServletRequest request) {
        AppUser user = resolveUser(request);
        Project project = projectService.getForView(user, projectId);
        Sprint sprint = sprintService.findById(sprintId)
                .filter(s -> s.getProject().getId().equals(project.getId()))
                .orElseThrow(() -> new DomainException("error.sprint.notFound"));
        AppUser executor = userService.findById(body.executorId())
                .orElseThrow(() -> new DomainException("error.executor.notFound"));
        LocalDate deadline = DeadlineParser.parse(body.deadline());
        if (deadline == null) {
            throw new DomainException("bot.deadline.invalid");
        }
        Task task = taskService.create(sprint, body.name(), body.description(), executor, deadline);
        return rowMapper.toTaskRow(task, user.getLanguage());
    }

    @GetMapping("/projects/{id}")
    public Map<String, Object> projectDetail(@PathVariable Long id, HttpServletRequest request) {
        AppUser user = resolveUser(request);
        Project project = projectService.getForView(user, id);
        List<SprintRow> sprints = sprintService.findByProject(project).stream()
                .map(s -> rowMapper.toSprintRow(s, user.getLanguage()))
                .toList();
        return Map.of(
                "id", project.getId(),
                "name", project.getName(),
                "description", project.getDescription() == null ? "" : project.getDescription(),
                "responsibleName", project.getResponsible().getFullName(),
                "deadlineLabel", DeadlineParser.format(project.getDeadline()),
                "statusLabel", messages.t(user.getLanguage(), "common.rollupStatus." + project.getStatus().name()),
                "sprints", sprints
        );
    }

    @GetMapping("/projects/{projectId}/sprints/{sprintId}")
    public Map<String, Object> sprintDetail(@PathVariable Long projectId, @PathVariable Long sprintId,
                                             @RequestParam(required = false) Long executorId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
                                             HttpServletRequest request) {
        AppUser user = resolveUser(request);
        Project project = projectService.getForView(user, projectId);
        Sprint sprint = sprintService.findById(sprintId)
                .filter(s -> s.getProject().getId().equals(project.getId()))
                .orElseThrow(() -> new DomainException("error.sprint.notFound"));

        List<TaskRow> tasks = taskService.forSprint(sprint).stream()
                .filter(t -> executorId == null || t.getExecutor().getId().equals(executorId))
                .filter(t -> status == null || status.isBlank() || t.getStatus().name().equals(status))
                .filter(t -> !overdueOnly || rowMapper.isOverdue(t))
                .map(t -> rowMapper.toTaskRow(t, user.getLanguage()))
                .toList();

        return Map.of(
                "id", sprint.getId(),
                "name", sprint.getName(),
                "responsibleName", sprint.getResponsible().getFullName(),
                "deadlineLabel", DeadlineParser.format(sprint.getDeadline()),
                "statusLabel", messages.t(user.getLanguage(), "common.rollupStatus." + sprint.getStatus().name()),
                "tasks", tasks
        );
    }

    @GetMapping("/tasks/{id}")
    public TaskDetailResponse taskDetail(@PathVariable Long id, HttpServletRequest request) {
        AppUser user = resolveUser(request);
        Task task = taskService.findById(id).orElseThrow(() -> new DomainException("error.task.notFound"));
        if (!projectService.canView(user, task.getSprint().getProject())) {
            throw new DomainException("error.project.noAccess");
        }
        return toTaskDetail(task, user);
    }

    @PostMapping("/tasks/{id}/status")
    public TaskDetailResponse changeStatus(@PathVariable Long id, @RequestBody StatusRequest body, HttpServletRequest request) {
        AppUser user = resolveUser(request);
        Task task = taskService.findById(id).orElseThrow(() -> new DomainException("error.task.notFound"));
        TaskStatus newStatus;
        try {
            newStatus = TaskStatus.valueOf(body.status());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status");
        }
        Task updated = taskService.changeStatus(user, task, newStatus, body.comment());
        return toTaskDetail(updated, user);
    }

    @PostMapping("/tasks/{id}/comment")
    public TaskDetailResponse addComment(@PathVariable Long id, @RequestBody CommentRequest body, HttpServletRequest request) {
        AppUser user = resolveUser(request);
        Task task = taskService.findById(id).orElseThrow(() -> new DomainException("error.task.notFound"));
        taskService.addComment(user, task, body.text());
        return toTaskDetail(task, user);
    }

    @PostMapping("/tasks/{id}/postpone")
    public Map<String, String> requestPostpone(@PathVariable Long id, @RequestBody PostponeRequest body, HttpServletRequest request) {
        AppUser user = resolveUser(request);
        Task task = taskService.findById(id).orElseThrow(() -> new DomainException("error.task.notFound"));
        LocalDate newDeadline = DeadlineParser.parse(body.newDeadline());
        if (newDeadline == null) {
            throw new DomainException("bot.deadline.invalid");
        }
        taskService.requestDeadlineChange(user, task, newDeadline);
        return Map.of("status", "ok");
    }

    private TaskDetailResponse toTaskDetail(Task task, AppUser user) {
        Language lang = user.getLanguage();
        boolean isExecutor = task.getExecutor().getId().equals(user.getId());
        boolean isManager = user.getRole() == Role.DIRECTOR
                || task.getSprint().getProject().getResponsible().getId().equals(user.getId());
        boolean terminal = task.getStatus() == TaskStatus.DONE || task.getStatus() == TaskStatus.CANCELLED;

        TaskActionsDto actions = new TaskActionsDto(
                isExecutor && task.getStatus() == TaskStatus.NEW,
                isManager && task.getStatus() == TaskStatus.REVIEW,
                isManager && !terminal,
                !terminal,
                isExecutor && !terminal
        );

        List<CommentRow> comments = taskService.commentsOf(task).stream()
                .map(c -> new CommentRow(c.getText(), c.getAuthor().getFullName(), DashboardController.formatInstant(c.getCreatedAt())))
                .toList();
        List<AttachmentDto> attachments = taskService.attachmentsOf(task).stream()
                .map(a -> new AttachmentDto(a.getId(), a.getOriginalFileName(), a.getUploadedBy().getFullName()))
                .toList();
        List<HistoryRow> history = taskService.historyOf(task).stream()
                .map(h -> new HistoryRow(
                        (h.getOldStatus() != null ? h.getOldStatus() + " → " : "") + h.getNewStatus(),
                        h.getChangedBy().getFullName(), DashboardController.formatInstant(h.getChangedAt()), h.getComment()))
                .toList();

        boolean finished = task.getStatus() == TaskStatus.DONE || task.getStatus() == TaskStatus.CANCELLED;
        return new TaskDetailResponse(task.getId(), task.getName(), task.getDescription(),
                task.getExecutor().getFullName(), task.getStatus().name(),
                messages.t(lang, "common.taskStatus." + task.getStatus().name()),
                DeadlineParser.format(task.getDeadline()), DeadlineColor.cssClass(task.getDeadline(), finished),
                comments, attachments, history, actions);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<Map<String, String>> handleDomainException(DomainException e, HttpServletRequest request) {
        Language lang = Language.RU;
        try {
            lang = resolveUser(request).getLanguage();
        } catch (Exception ignored) {
            // fall back to RU if we can't resolve the user for the error message itself
        }
        return ResponseEntity.badRequest().body(Map.of("error", messages.t(lang, e.getMessageKey(), (Object[]) e.getArgs())));
    }
}

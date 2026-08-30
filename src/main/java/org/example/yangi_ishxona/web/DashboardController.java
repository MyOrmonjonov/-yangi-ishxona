package org.example.yangi_ishxona.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.*;
import org.example.yangi_ishxona.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final ProjectService projectService;
    private final SprintService sprintService;
    private final TaskService taskService;
    private final UserService userService;
    private final Messages messages;
    private final RowMapper rowMapper;

    private AppUser currentUser(HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        return userService.findById(userId)
                .orElseThrow(() -> new DomainException("error.session.invalid"));
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/projects";
    }

    @GetMapping("/projects")
    public String projects(HttpSession session, Model model) {
        AppUser user = currentUser(session);
        List<ProjectRow> rows = projectService.visibleProjectsFor(user).stream()
                .map(p -> rowMapper.toProjectRow(p, user.getLanguage()))
                .toList();
        model.addAttribute("user", user);
        model.addAttribute("projects", rows);
        return "projects";
    }

    @GetMapping("/projects/{id}")
    public String projectDetail(@PathVariable Long id, HttpSession session, Model model) {
        AppUser user = currentUser(session);
        Project project = projectService.getForView(user, id);
        List<SprintRow> sprints = sprintService.findByProject(project).stream()
                .map(s -> rowMapper.toSprintRow(s, user.getLanguage()))
                .toList();
        model.addAttribute("user", user);
        model.addAttribute("project", project);
        model.addAttribute("sprints", sprints);
        model.addAttribute("projectStatusLabel", messages.t(user.getLanguage(), "common.rollupStatus." + project.getStatus().name()));
        return "project-detail";
    }

    @GetMapping("/projects/{projectId}/sprints/{sprintId}")
    public String sprintDetail(@PathVariable Long projectId, @PathVariable Long sprintId,
                                @RequestParam(required = false) Long executorId,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false, defaultValue = "false") boolean overdueOnly,
                                HttpSession session, Model model) {
        AppUser user = currentUser(session);
        Project project = projectService.getForView(user, projectId);
        Sprint sprint = sprintService.findById(sprintId)
                .filter(s -> s.getProject().getId().equals(project.getId()))
                .orElseThrow(() -> new DomainException("error.sprint.notFound"));

        List<Task> tasks = taskService.forSprint(sprint);
        List<TaskRow> rows = tasks.stream()
                .filter(t -> executorId == null || t.getExecutor().getId().equals(executorId))
                .filter(t -> status == null || status.isBlank() || t.getStatus().name().equals(status))
                .filter(t -> !overdueOnly || rowMapper.isOverdue(t))
                .map(t -> rowMapper.toTaskRow(t, user.getLanguage()))
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("project", project);
        model.addAttribute("sprint", sprint);
        model.addAttribute("sprintStatusLabel", messages.t(user.getLanguage(), "common.rollupStatus." + sprint.getStatus().name()));
        model.addAttribute("tasks", rows);
        model.addAttribute("allUsers", userService.allUsers());
        model.addAttribute("allStatuses", TaskStatus.values());
        model.addAttribute("selectedExecutorId", executorId);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("overdueOnly", overdueOnly);
        return "sprint-detail";
    }

    @GetMapping("/tasks/{id}")
    public String taskDetail(@PathVariable Long id, HttpSession session, Model model) {
        AppUser user = currentUser(session);
        Task task = taskService.findById(id).orElseThrow(() -> new DomainException("error.task.notFound"));
        if (!projectService.canView(user, task.getSprint().getProject())) {
            throw new DomainException("error.project.noAccess");
        }
        List<CommentRow> comments = taskService.commentsOf(task).stream()
                .map(c -> new CommentRow(c.getText(), c.getAuthor().getFullName(), formatInstant(c.getCreatedAt())))
                .toList();
        List<HistoryRow> history = taskService.historyOf(task).stream()
                .map(h -> new HistoryRow(
                        (h.getOldStatus() != null ? h.getOldStatus() + " → " : "") + h.getNewStatus(),
                        h.getChangedBy().getFullName(), formatInstant(h.getChangedAt()), h.getComment()))
                .toList();

        model.addAttribute("user", user);
        model.addAttribute("task", task);
        model.addAttribute("statusLabel", messages.t(user.getLanguage(), "common.taskStatus." + task.getStatus().name()));
        model.addAttribute("deadlineLabel", DeadlineParser.format(task.getDeadline()));
        model.addAttribute("comments", comments);
        model.addAttribute("attachments", taskService.attachmentsOf(task));
        model.addAttribute("history", history);
        return "task-detail";
    }

    // ------------------------------------------------------------------ //

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    static String formatInstant(Instant instant) {
        return instant == null ? "-" : TIMESTAMP_FORMAT.format(instant.atZone(TASHKENT));
    }
}

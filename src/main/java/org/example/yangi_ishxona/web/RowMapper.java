package org.example.yangi_ishxona.web;

import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.*;
import org.example.yangi_ishxona.service.DeadlineParser;
import org.example.yangi_ishxona.service.Messages;
import org.example.yangi_ishxona.service.TaskService;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Shared Project/Sprint/Task -> view-row mapping, used by both the dashboard and the Mini App API. */
@Component
@RequiredArgsConstructor
public class RowMapper {

    private final TaskService taskService;
    private final Messages messages;

    public boolean isOverdue(Task task) {
        return task.getStatus() != TaskStatus.DONE && task.getStatus() != TaskStatus.CANCELLED
                && task.getDeadline().isBefore(LocalDate.now());
    }

    public ProjectRow toProjectRow(Project project, Language lang) {
        List<Task> tasks = taskService.forProject(project.getId());
        List<Task> active = tasks.stream().filter(t -> t.getStatus() != TaskStatus.CANCELLED).toList();
        long doneCount = active.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        int percent = active.isEmpty() ? 0 : (int) Math.round(doneCount * 100.0 / active.size());
        long overdueCount = tasks.stream().filter(this::isOverdue).count();

        Optional<LocalDate> nearestActive = tasks.stream()
                .filter(t -> t.getStatus() != TaskStatus.DONE && t.getStatus() != TaskStatus.CANCELLED)
                .map(Task::getDeadline)
                .min(Comparator.naturalOrder());
        LocalDate shownDeadline = nearestActive.orElse(project.getDeadline());
        boolean finished = project.getStatus() == RollupStatus.DONE || project.getStatus() == RollupStatus.CANCELLED;

        return new ProjectRow(project.getId(), project.getName(), project.getResponsible().getFullName(),
                DeadlineParser.format(shownDeadline), DeadlineColor.cssClass(shownDeadline, finished),
                percent, overdueCount, messages.t(lang, "common.rollupStatus." + project.getStatus().name()));
    }

    public SprintRow toSprintRow(Sprint sprint, Language lang) {
        List<Task> tasks = taskService.forSprint(sprint);
        List<Task> active = tasks.stream().filter(t -> t.getStatus() != TaskStatus.CANCELLED).toList();
        long doneCount = active.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        int percent = active.isEmpty() ? 0 : (int) Math.round(doneCount * 100.0 / active.size());
        boolean finished = sprint.getStatus() == RollupStatus.DONE || sprint.getStatus() == RollupStatus.CANCELLED;

        return new SprintRow(sprint.getId(), sprint.getProject().getId(), sprint.getName(),
                sprint.getResponsible().getFullName(), DeadlineParser.format(sprint.getDeadline()),
                DeadlineColor.cssClass(sprint.getDeadline(), finished), percent,
                messages.t(lang, "common.rollupStatus." + sprint.getStatus().name()));
    }

    public TaskRow toTaskRow(Task task, Language lang) {
        boolean finished = task.getStatus() == TaskStatus.DONE || task.getStatus() == TaskStatus.CANCELLED;
        return new TaskRow(task.getId(), task.getName(), task.getExecutor().getFullName(),
                DeadlineParser.format(task.getDeadline()), DeadlineColor.cssClass(task.getDeadline(), finished),
                messages.t(lang, "common.taskStatus." + task.getStatus().name()), task.getStatus().name(), isOverdue(task));
    }
}

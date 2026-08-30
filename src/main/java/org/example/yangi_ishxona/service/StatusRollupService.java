package org.example.yangi_ishxona.service;

import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.*;
import org.example.yangi_ishxona.repository.ProjectRepository;
import org.example.yangi_ishxona.repository.SprintRepository;
import org.example.yangi_ishxona.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Recomputes Sprint/Project.status from their children, per ТЗ 3.4:
 * "Статус спринта и проекта рассчитывается автоматически из вложенных элементов
 * (например: «Выполнен», когда все задачи внутри выполнены)".
 *
 * Rule (same shape at both levels): ignore cancelled children; if every remaining
 * child is CANCELLED -> CANCELLED; if all remaining are done -> DONE; if any has
 * been started -> IN_PROGRESS; otherwise -> NOT_STARTED.
 */
@Service
@RequiredArgsConstructor
public class StatusRollupService {

    private final SprintRepository sprintRepository;
    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;

    @Transactional
    public void recalculateForTask(Task task) {
        recalculateSprint(task.getSprint());
    }

    @Transactional
    public void recalculateSprint(Sprint sprint) {
        List<Task> tasks = taskRepository.findBySprintOrderByDeadlineAsc(sprint);
        RollupStatus newStatus = computeFromTasks(tasks);
        if (sprint.getStatus() != newStatus) {
            sprint.setStatus(newStatus);
            sprintRepository.save(sprint);
        }
        recalculateProject(sprint.getProject());
    }

    @Transactional
    public void recalculateProject(Project project) {
        List<Sprint> sprints = sprintRepository.findByProjectOrderByDeadlineAsc(project);
        RollupStatus newStatus = computeFromSprints(sprints);
        if (project.getStatus() != newStatus) {
            project.setStatus(newStatus);
            projectRepository.save(project);
        }
    }

    private RollupStatus computeFromTasks(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return RollupStatus.NOT_STARTED;
        }
        List<Task> active = tasks.stream().filter(t -> t.getStatus() != TaskStatus.CANCELLED).toList();
        if (active.isEmpty()) {
            return RollupStatus.CANCELLED;
        }
        boolean allDone = active.stream().allMatch(t -> t.getStatus() == TaskStatus.DONE);
        if (allDone) {
            return RollupStatus.DONE;
        }
        boolean anyStarted = active.stream().anyMatch(t -> t.getStatus() != TaskStatus.NEW);
        return anyStarted ? RollupStatus.IN_PROGRESS : RollupStatus.NOT_STARTED;
    }

    private RollupStatus computeFromSprints(List<Sprint> sprints) {
        if (sprints.isEmpty()) {
            return RollupStatus.NOT_STARTED;
        }
        List<Sprint> active = sprints.stream().filter(s -> s.getStatus() != RollupStatus.CANCELLED).toList();
        if (active.isEmpty()) {
            return RollupStatus.CANCELLED;
        }
        boolean allDone = active.stream().allMatch(s -> s.getStatus() == RollupStatus.DONE);
        if (allDone) {
            return RollupStatus.DONE;
        }
        boolean anyStarted = active.stream().anyMatch(s -> s.getStatus() != RollupStatus.NOT_STARTED);
        return anyStarted ? RollupStatus.IN_PROGRESS : RollupStatus.NOT_STARTED;
    }
}

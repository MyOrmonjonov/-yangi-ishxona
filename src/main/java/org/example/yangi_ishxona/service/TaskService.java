package org.example.yangi_ishxona.service;

import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.*;
import org.example.yangi_ishxona.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private static final long MAX_FILE_SIZE_BYTES = 20L * 1024 * 1024;
    private static final int MAX_FILES_PER_TASK = 10;

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final DeadlineChangeRequestRepository deadlineChangeRequestRepository;
    private final StatusRollupService statusRollupService;

    @Transactional
    public Task create(Sprint sprint, String name, String description, AppUser executor, LocalDate deadline) {
        if (name == null || name.isBlank()) {
            throw new DomainException("error.task.nameRequired");
        }
        if (deadline == null) {
            throw new DomainException("error.task.deadlineRequired");
        }
        if (deadline.isAfter(sprint.getDeadline())) {
            throw new DomainException("error.task.deadlineAfterSprint", DeadlineParser.format(sprint.getDeadline()));
        }
        Task task = new Task();
        task.setSprint(sprint);
        task.setName(name.trim());
        task.setDescription(description);
        task.setExecutor(executor);
        task.setDeadline(deadline);
        task.setStatus(TaskStatus.NEW);
        Task saved = taskRepository.save(task);
        statusRollupService.recalculateForTask(saved);
        return saved;
    }

    public Optional<Task> findById(Long id) {
        return taskRepository.findById(id);
    }

    public List<Task> myTasks(AppUser user) {
        return taskRepository.findByExecutorOrderByDeadlineAsc(user);
    }

    public List<Task> forSprint(Sprint sprint) {
        return taskRepository.findBySprintOrderByDeadlineAsc(sprint);
    }

    public List<Task> forProject(Long projectId) {
        return taskRepository.findBySprintProjectIdOrderByDeadlineAsc(projectId);
    }

    public List<Task> activeTasks() {
        return taskRepository.findByStatusNotIn(List.of(TaskStatus.DONE, TaskStatus.CANCELLED));
    }

    private boolean isProjectManagerOf(AppUser actor, Task task) {
        return actor.getRole() == Role.DIRECTOR
                || task.getSprint().getProject().getResponsible().getId().equals(actor.getId());
    }

    @Transactional
    public TaskAttachment addAttachment(AppUser actor, Task task, String telegramFileId,
                                         String originalFileName, Long fileSize) {
        if (!task.getExecutor().getId().equals(actor.getId())) {
            throw new DomainException("error.task.onlyExecutorAttach");
        }
        if (fileSize != null && fileSize > MAX_FILE_SIZE_BYTES) {
            throw new DomainException("error.task.fileTooLarge");
        }
        if (taskAttachmentRepository.countByTask(task) >= MAX_FILES_PER_TASK) {
            throw new DomainException("error.task.tooManyFiles");
        }
        TaskAttachment attachment = new TaskAttachment();
        attachment.setTask(task);
        attachment.setTelegramFileId(telegramFileId);
        attachment.setOriginalFileName(originalFileName);
        attachment.setFileSize(fileSize);
        attachment.setUploadedBy(actor);
        return taskAttachmentRepository.save(attachment);
    }

    public List<TaskAttachment> attachmentsOf(Task task) {
        return taskAttachmentRepository.findByTaskOrderByCreatedAtAsc(task);
    }

    @Transactional
    public TaskComment addComment(AppUser author, Task task, String text) {
        if (text == null || text.isBlank()) {
            throw new DomainException("error.comment.empty");
        }
        return taskCommentRepository.save(new TaskComment(task, author, text.trim()));
    }

    public List<TaskComment> commentsOf(Task task) {
        return taskCommentRepository.findByTaskOrderByCreatedAtAsc(task);
    }

    public List<StatusHistory> historyOf(Task task) {
        return statusHistoryRepository.findByEntityTypeAndEntityIdOrderByChangedAtAsc(EntityType.TASK, task.getId());
    }

    @Transactional
    public Task changeStatus(AppUser actor, Task task, TaskStatus newStatus, String comment) {
        TaskStatus current = task.getStatus();
        boolean isExecutor = task.getExecutor().getId().equals(actor.getId());
        boolean isManager = isProjectManagerOf(actor, task);

        switch (newStatus) {
            case IN_PROGRESS -> {
                if (current != TaskStatus.NEW) {
                    throw new DomainException("error.status.toInProgressOnlyFromNew");
                }
                if (!isExecutor) {
                    throw new DomainException("error.status.onlyExecutor");
                }
            }
            case REVIEW -> {
                if (current != TaskStatus.IN_PROGRESS) {
                    throw new DomainException("error.status.toReviewOnlyFromInProgress");
                }
                if (!isExecutor) {
                    throw new DomainException("error.status.onlyExecutor");
                }
                if (taskAttachmentRepository.countByTask(task) == 0) {
                    throw new DomainException("error.status.reviewNeedsAttachment");
                }
            }
            case DONE -> {
                if (current != TaskStatus.REVIEW) {
                    throw new DomainException("error.status.toDoneOnlyFromReview");
                }
                if (!isManager) {
                    throw new DomainException("error.status.onlyManagerDone");
                }
            }
            case CANCELLED -> {
                if (current == TaskStatus.DONE || current == TaskStatus.CANCELLED) {
                    throw new DomainException("error.status.cannotCancel");
                }
                if (!isManager) {
                    throw new DomainException("error.status.onlyManagerCancel");
                }
                if (comment == null || comment.isBlank()) {
                    throw new DomainException("error.status.cancelReasonRequired");
                }
            }
            case NEW -> throw new DomainException("error.status.cannotReturnToNew");
        }

        task.setStatus(newStatus);
        Task saved = taskRepository.save(task);
        statusHistoryRepository.save(new StatusHistory(EntityType.TASK, task.getId(),
                current.name(), newStatus.name(), actor, comment));
        statusRollupService.recalculateForTask(saved);
        return saved;
    }

    @Transactional
    public DeadlineChangeRequest requestDeadlineChange(AppUser actor, Task task, LocalDate newDeadline) {
        if (!task.getExecutor().getId().equals(actor.getId())) {
            throw new DomainException("error.deadlineChange.onlyExecutorRequest");
        }
        if (newDeadline == null) {
            throw new DomainException("error.deadlineChange.newDeadlineRequired");
        }
        DeadlineChangeRequest request = new DeadlineChangeRequest(task, actor, task.getDeadline(), newDeadline);
        return deadlineChangeRequestRepository.save(request);
    }

    public Optional<DeadlineChangeRequest> findDeadlineChangeRequest(Long id) {
        return deadlineChangeRequestRepository.findById(id);
    }

    @Transactional
    public Task resolveDeadlineChange(AppUser actor, DeadlineChangeRequest request, boolean approve) {
        Task task = request.getTask();
        if (!isProjectManagerOf(actor, task)) {
            throw new DomainException("error.deadlineChange.onlyManagerResolve");
        }
        if (request.getStatus() != DeadlineChangeStatus.PENDING) {
            throw new DomainException("error.deadlineChange.alreadyResolved");
        }
        request.setStatus(approve ? DeadlineChangeStatus.APPROVED : DeadlineChangeStatus.REJECTED);
        request.setResolvedBy(actor);
        request.setResolvedAt(java.time.Instant.now());
        deadlineChangeRequestRepository.save(request);

        if (approve) {
            if (request.getNewDeadline().isAfter(task.getSprint().getDeadline())) {
                throw new DomainException("error.deadlineChange.newDeadlineAfterSprint");
            }
            LocalDate old = task.getDeadline();
            task.setDeadline(request.getNewDeadline());
            taskRepository.save(task);
            statusHistoryRepository.save(new StatusHistory(EntityType.TASK, task.getId(),
                    "deadline=" + DeadlineParser.format(old), "deadline=" + DeadlineParser.format(request.getNewDeadline()),
                    actor, "deadlineChangeApproved"));
        }
        return task;
    }

    public Optional<DeadlineChangeRequest> pendingDeadlineChange(Task task) {
        return deadlineChangeRequestRepository.findFirstByTaskAndStatusOrderByCreatedAtDesc(task, DeadlineChangeStatus.PENDING);
    }
}

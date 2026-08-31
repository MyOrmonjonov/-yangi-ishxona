package uz.taskapp.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "tasks")
public class TaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "sequence_number", nullable = false)
    private Long sequenceNumber;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "topic_id")
    private Long topicId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskVisibility visibility;

    @Column(name = "due_at")
    private Instant dueAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "telegram_message_id")
    private Long telegramMessageId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TaskEntity() {
    }

    public TaskEntity(Long workspaceId, Long groupId, Long topicId, Long authorId, String title,
                      String description, TaskStatus status, TaskPriority priority,
                      TaskVisibility visibility, Instant dueAt) {
        this.workspaceId = workspaceId;
        this.groupId = groupId;
        this.topicId = topicId;
        this.authorId = authorId;
        this.title = title.trim();
        this.description = description;
        this.status = status;
        this.priority = priority;
        this.visibility = visibility;
        this.dueAt = dueAt;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void assignSequence(long sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void changeStatus(TaskStatus status) {
        this.status = status;
        this.completedAt = status == TaskStatus.COMPLETED ? Instant.now() : null;
        this.updatedAt = Instant.now();
    }

    public void linkTelegramMessage(Long telegramMessageId) {
        this.telegramMessageId = telegramMessageId;
        this.updatedAt = Instant.now();
    }

    public void updateDetails(String title, String description, TaskStatus status,
                              TaskPriority priority, Instant dueAt, boolean dueAtProvided) {
        this.title = title.trim();
        this.description = description == null || description.isBlank() ? null : description.trim();
        if (priority != null) this.priority = priority;
        if (dueAtProvided) this.dueAt = dueAt;
        if (this.status != status) changeStatus(status);
        else this.updatedAt = Instant.now();
    }

    public void changePlacement(TaskVisibility visibility, Long groupId, Long topicId) {
        this.visibility = visibility;
        this.groupId = groupId;
        this.topicId = topicId;
        this.telegramMessageId = null;
        this.updatedAt = Instant.now();
    }

    public void changeAuthor(Long authorId) {
        this.authorId = authorId;
        this.updatedAt = Instant.now();
    }

    public void archive(Long byUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = byUserId;
        this.updatedAt = Instant.now();
    }

    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getWorkspaceId() { return workspaceId; }
    public Long getSequenceNumber() { return sequenceNumber; }
    public Long getGroupId() { return groupId; }
    public Long getTopicId() { return topicId; }
    public Long getAuthorId() { return authorId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public TaskVisibility getVisibility() { return visibility; }
    public Instant getDueAt() { return dueAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public Long getDeletedBy() { return deletedBy; }
    public Long getTelegramMessageId() { return telegramMessageId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package uz.taskapp.task;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "task_assignees")
public class TaskAssigneeEntity {
    @EmbeddedId
    private TaskAssigneeId id;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected TaskAssigneeEntity() {
    }

    public TaskAssigneeEntity(Long taskId, Long userId) {
        this.id = new TaskAssigneeId(taskId, userId);
        this.assignedAt = Instant.now();
    }

    public Long getTaskId() { return id.taskId(); }
    public Long getUserId() { return id.userId(); }
}

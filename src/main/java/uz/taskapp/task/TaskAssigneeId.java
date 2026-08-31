package uz.taskapp.task;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public record TaskAssigneeId(
        @Column(name = "task_id") Long taskId,
        @Column(name = "user_id") Long userId
) implements Serializable {
    public TaskAssigneeId() {
        this(null, null);
    }
}

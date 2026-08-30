package org.example.yangi_ishxona.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "deadline_notification_log", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"task_id", "notification_type"})
})
@Getter
@Setter
@NoArgsConstructor
public class DeadlineNotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    private NotificationType notificationType;

    /** When the notification became due (may fall in the quiet window). */
    @Column(name = "scheduled_for", nullable = false)
    private Instant scheduledFor;

    /** Null while queued (e.g. waiting out the 20:00-09:00 quiet window); set once actually sent. */
    @Column(name = "sent_at")
    private Instant sentAt;

    public DeadlineNotificationLog(Task task, NotificationType notificationType, Instant scheduledFor) {
        this.task = task;
        this.notificationType = notificationType;
        this.scheduledFor = scheduledFor;
    }
}

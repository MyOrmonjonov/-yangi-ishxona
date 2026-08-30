package org.example.yangi_ishxona.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "deadline_change_request")
@Getter
@Setter
@NoArgsConstructor
public class DeadlineChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private AppUser requestedBy;

    @Column(name = "old_deadline", nullable = false)
    private LocalDate oldDeadline;

    @Column(name = "new_deadline", nullable = false)
    private LocalDate newDeadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeadlineChangeStatus status = DeadlineChangeStatus.PENDING;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "resolved_by_id")
    private AppUser resolvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public DeadlineChangeRequest(Task task, AppUser requestedBy, LocalDate oldDeadline, LocalDate newDeadline) {
        this.task = task;
        this.requestedBy = requestedBy;
        this.oldDeadline = oldDeadline;
        this.newDeadline = newDeadline;
    }
}

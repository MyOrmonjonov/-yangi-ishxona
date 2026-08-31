package uz.taskapp.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "telegram_group_requests")
public class TelegramGroupRequestEntity {
    @Id
    @Column(name = "request_id")
    private Integer requestId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TelegramGroupRequestEntity() {
    }

    public TelegramGroupRequestEntity(Integer requestId, Long userId, Long workspaceId, Instant expiresAt) {
        this.requestId = requestId;
        this.userId = userId;
        this.workspaceId = workspaceId;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public Integer getRequestId() { return requestId; }
    public Long getUserId() { return userId; }
    public Long getWorkspaceId() { return workspaceId; }
    public Instant getExpiresAt() { return expiresAt; }
}

package uz.taskapp.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "workspace_members")
public class WorkspaceMemberEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "temporarily_blocked", nullable = false)
    private boolean temporarilyBlocked;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected WorkspaceMemberEntity() {
    }

    public WorkspaceMemberEntity(Long workspaceId, Long userId, String roleCode) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.roleCode = roleCode;
        this.active = true;
        this.joinedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getWorkspaceId() { return workspaceId; }
    public Long getUserId() { return userId; }
    public String getRoleCode() { return roleCode; }
    public boolean isActive() { return active; }
    public boolean isTemporarilyBlocked() { return temporarilyBlocked; }

    public void activate() {
        this.active = true;
        this.temporarilyBlocked = false;
    }
}

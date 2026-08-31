package uz.taskapp.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "workspaces")
public class WorkspaceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "default_language", nullable = false, length = 8)
    private String defaultLanguage;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceEntity() {
    }

    public WorkspaceEntity(String name, String defaultLanguage, Long ownerId) {
        this.name = name;
        this.defaultLanguage = defaultLanguage;
        this.ownerId = ownerId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getDefaultLanguage() { return defaultLanguage; }
    public boolean isArchived() { return archived; }
    public Long getOwnerId() { return ownerId; }
}

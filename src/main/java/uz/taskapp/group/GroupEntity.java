package uz.taskapp.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "groups")
public class GroupEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workspace_id", nullable = false)
    private Long workspaceId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(name = "telegram_chat_id", unique = true)
    private Long telegramChatId;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "bot_is_admin", nullable = false)
    private boolean botIsAdmin;

    @Column(name = "can_pin_messages", nullable = false)
    private boolean canPinMessages;

    @Column(name = "telegram_synced_at")
    private Instant telegramSyncedAt;

    @Column(name = "post_tasks_to_telegram", nullable = false)
    private boolean postTasksToTelegram;

    @Column(name = "auto_pin_tasks", nullable = false)
    private boolean autoPinTasks;

    @Column(name = "default_priority", nullable = false, length = 16)
    private String defaultPriority;

    @Column(name = "task_creation_policy", nullable = false, length = 24)
    private String taskCreationPolicy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GroupEntity() {
    }

    public GroupEntity(Long workspaceId, String name, Long telegramChatId) {
        this.workspaceId = workspaceId;
        this.name = name;
        this.telegramChatId = telegramChatId;
        this.type = "TELEGRAM";
        this.active = true;
        this.telegramSyncedAt = Instant.now();
        this.postTasksToTelegram = true;
        this.defaultPriority = "NORMAL";
        this.taskCreationPolicy = "EVERYONE";
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void refreshTelegramData(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.active = true;
        this.telegramSyncedAt = Instant.now();
        this.updatedAt = this.telegramSyncedAt;
    }

    public void updateTaskCreationPolicy(String policy) {
        this.taskCreationPolicy = policy;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getWorkspaceId() { return workspaceId; }
    public String getName() { return name; }
    public Long getTelegramChatId() { return telegramChatId; }
    public boolean isActive() { return active; }
    public String getTaskCreationPolicy() { return taskCreationPolicy; }
}

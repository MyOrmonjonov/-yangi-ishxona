package uz.taskapp.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", nullable = false, unique = true)
    private Long telegramId;

    @Column(name = "first_name", nullable = false, length = 128)
    private String firstName;

    @Column(name = "last_name", length = 128)
    private String lastName;

    @Column(length = 64)
    private String username;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "language_code", length = 8)
    private String languageCode;

    @Column(name = "bot_connected", nullable = false)
    private boolean botConnected;

    @Column(nullable = false)
    private boolean blocked;

    @Column(name = "ui_language", nullable = false, length = 8)
    private String uiLanguage = "uz";

    @Column(nullable = false, length = 10)
    private String theme = "system";

    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = true;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "last_bot_workspace_id")
    private Long lastBotWorkspaceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserEntity() {
    }

    public UserEntity(Long telegramId, String firstName) {
        this.telegramId = telegramId;
        this.firstName = firstName;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void updateTelegramProfile(String firstName, String lastName, String username,
                                      String photoUrl, String languageCode) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.photoUrl = photoUrl;
        this.languageCode = languageCode;
        this.lastSeenAt = Instant.now();
        this.updatedAt = this.lastSeenAt;
    }

    public void markBotConnected() {
        this.botConnected = true;
        this.updatedAt = Instant.now();
    }

    public void updateLastBotWorkspace(Long workspaceId) {
        this.lastBotWorkspaceId = workspaceId;
        this.updatedAt = Instant.now();
    }

    public void updatePreferences(String uiLanguage, String theme, Boolean remindersEnabled) {
        if (uiLanguage != null) this.uiLanguage = uiLanguage;
        if (theme != null) this.theme = theme;
        if (remindersEnabled != null) this.remindersEnabled = remindersEnabled;
        this.updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTelegramId() { return telegramId; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getUsername() { return username; }
    public String getPhotoUrl() { return photoUrl; }
    public String getLanguageCode() { return languageCode; }
    public boolean isBlocked() { return blocked; }
    public String getUiLanguage() { return uiLanguage; }
    public String getTheme() { return theme; }
    public boolean isRemindersEnabled() { return remindersEnabled; }
    public Long getLastBotWorkspaceId() { return lastBotWorkspaceId; }
}

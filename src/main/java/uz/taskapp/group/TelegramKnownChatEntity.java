package uz.taskapp.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "telegram_known_chats")
public class TelegramKnownChatEntity {
    @Id
    @Column(name = "chat_id")
    private Long chatId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TelegramKnownChatEntity() {
    }

    public TelegramKnownChatEntity(Long chatId, String title) {
        this.chatId = chatId;
        this.title = title;
        this.active = true;
        this.updatedAt = Instant.now();
    }

    public void refresh(String title, boolean active) {
        if (title != null && !title.isBlank()) {
            this.title = title;
        }
        this.active = active;
        this.updatedAt = Instant.now();
    }

    public Long getChatId() { return chatId; }
    public String getTitle() { return title; }
    public boolean isActive() { return active; }
}

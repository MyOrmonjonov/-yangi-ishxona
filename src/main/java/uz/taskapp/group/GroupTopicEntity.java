package uz.taskapp.group;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "group_topics")
public class GroupTopicEntity {
    @EmbeddedId
    private GroupTopicId id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private boolean closed;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GroupTopicEntity() {
    }

    public GroupTopicEntity(Long groupId, Long topicId, String name) {
        this.id = new GroupTopicId(groupId, topicId);
        this.name = name;
        this.closed = false;
        this.updatedAt = Instant.now();
    }

    public void refresh(String name, boolean closed) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
        this.closed = closed;
        this.updatedAt = Instant.now();
    }

    public GroupTopicId getId() { return id; }
    public String getName() { return name; }
    public boolean isClosed() { return closed; }
}

package uz.taskapp.group;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public record GroupTopicId(
        @Column(name = "group_id") Long groupId,
        @Column(name = "topic_id") Long topicId
) implements Serializable {
    public GroupTopicId() {
        this(null, null);
    }
}

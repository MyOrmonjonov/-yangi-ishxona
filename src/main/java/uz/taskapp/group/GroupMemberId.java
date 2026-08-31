package uz.taskapp.group;

import java.io.Serializable;

public record GroupMemberId(Long groupId, Long userId) implements Serializable {
    public GroupMemberId() {
        this(null, null);
    }
}

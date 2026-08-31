package uz.taskapp.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "group_members")
@IdClass(GroupMemberId.class)
public class GroupMemberEntity {
    @Id
    @Column(name = "group_id")
    private Long groupId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "synced_from_telegram", nullable = false)
    private boolean syncedFromTelegram;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    protected GroupMemberEntity() {
    }

    public GroupMemberEntity(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
        this.syncedFromTelegram = true;
        this.joinedAt = Instant.now();
    }

    public Long getGroupId() { return groupId; }
    public Long getUserId() { return userId; }
    public Instant getJoinedAt() { return joinedAt; }
}

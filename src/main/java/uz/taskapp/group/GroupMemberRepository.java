package uz.taskapp.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMemberEntity, GroupMemberId> {
    long countByGroupId(Long groupId);
    List<GroupMemberEntity> findAllByGroupIdOrderByJoinedAtAsc(Long groupId);
}

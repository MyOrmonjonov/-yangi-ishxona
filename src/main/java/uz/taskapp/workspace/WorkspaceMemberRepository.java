package uz.taskapp.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMemberEntity, Long> {
    List<WorkspaceMemberEntity> findAllByUserIdAndActiveTrue(Long userId);
    List<WorkspaceMemberEntity> findAllByWorkspaceIdAndActiveTrue(Long workspaceId);
    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserIdAndActiveTrue(Long workspaceId, Long userId);
    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserId(Long workspaceId, Long userId);
    boolean existsByWorkspaceIdAndUserIdAndActiveTrue(Long workspaceId, Long userId);
    Optional<WorkspaceMemberEntity> findByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(Long workspaceId, Long userId);
    boolean existsByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(Long workspaceId, Long userId);
}

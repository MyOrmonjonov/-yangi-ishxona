package uz.taskapp.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<TaskEntity, Long> {
    List<TaskEntity> findAllByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long workspaceId);
    Optional<TaskEntity> findByIdAndDeletedAtIsNull(Long id);
    List<TaskEntity> findAllByWorkspaceIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(Long workspaceId);
    Optional<TaskEntity> findByIdAndDeletedAtIsNotNull(Long id);
    Optional<TaskEntity> findByWorkspaceIdAndSequenceNumberAndDeletedAtIsNull(Long workspaceId, Long sequenceNumber);
}

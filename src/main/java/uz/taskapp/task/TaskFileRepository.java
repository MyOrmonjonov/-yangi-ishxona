package uz.taskapp.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskFileRepository extends JpaRepository<TaskFileEntity, Long> {
    List<TaskFileEntity> findAllByTaskIdOrderByCreatedAtAsc(Long taskId);
    Optional<TaskFileEntity> findByIdAndTaskId(Long id, Long taskId);
    boolean existsByTaskId(Long taskId);
}

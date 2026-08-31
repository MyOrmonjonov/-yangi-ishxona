package uz.taskapp.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TaskAssigneeRepository extends JpaRepository<TaskAssigneeEntity, TaskAssigneeId> {
    List<TaskAssigneeEntity> findAllByIdTaskIdIn(Collection<Long> taskIds);
    boolean existsByIdTaskIdAndIdUserId(Long taskId, Long userId);
    boolean existsByIdTaskId(Long taskId);
    void deleteAllByIdTaskId(Long taskId);
}

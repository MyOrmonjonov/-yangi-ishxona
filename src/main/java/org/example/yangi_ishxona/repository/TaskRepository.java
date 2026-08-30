package org.example.yangi_ishxona.repository;

import org.example.yangi_ishxona.domain.AppUser;
import org.example.yangi_ishxona.domain.Sprint;
import org.example.yangi_ishxona.domain.Task;
import org.example.yangi_ishxona.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findBySprintOrderByDeadlineAsc(Sprint sprint);
    List<Task> findBySprintProjectIdOrderByDeadlineAsc(Long projectId);
    List<Task> findByExecutorOrderByDeadlineAsc(AppUser executor);
    List<Task> findByStatusNotIn(Collection<TaskStatus> statuses);
}

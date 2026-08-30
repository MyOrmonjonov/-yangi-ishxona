package org.example.yangi_ishxona.repository;

import org.example.yangi_ishxona.domain.Task;
import org.example.yangi_ishxona.domain.TaskComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment, Long> {
    List<TaskComment> findByTaskOrderByCreatedAtAsc(Task task);
}

package org.example.yangi_ishxona.repository;

import org.example.yangi_ishxona.domain.Task;
import org.example.yangi_ishxona.domain.TaskAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTaskOrderByCreatedAtAsc(Task task);
    int countByTask(Task task);
}

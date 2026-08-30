package org.example.yangi_ishxona.repository;

import org.example.yangi_ishxona.domain.DeadlineChangeRequest;
import org.example.yangi_ishxona.domain.DeadlineChangeStatus;
import org.example.yangi_ishxona.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeadlineChangeRequestRepository extends JpaRepository<DeadlineChangeRequest, Long> {
    Optional<DeadlineChangeRequest> findFirstByTaskAndStatusOrderByCreatedAtDesc(Task task, DeadlineChangeStatus status);
    List<DeadlineChangeRequest> findByStatus(DeadlineChangeStatus status);
}

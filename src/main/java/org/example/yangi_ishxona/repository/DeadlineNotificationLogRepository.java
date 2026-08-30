package org.example.yangi_ishxona.repository;

import org.example.yangi_ishxona.domain.DeadlineNotificationLog;
import org.example.yangi_ishxona.domain.NotificationType;
import org.example.yangi_ishxona.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeadlineNotificationLogRepository extends JpaRepository<DeadlineNotificationLog, Long> {
    Optional<DeadlineNotificationLog> findByTaskAndNotificationType(Task task, NotificationType notificationType);
    List<DeadlineNotificationLog> findBySentAtIsNull();
}

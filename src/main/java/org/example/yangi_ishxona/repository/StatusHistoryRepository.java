package org.example.yangi_ishxona.repository;

import org.example.yangi_ishxona.domain.EntityType;
import org.example.yangi_ishxona.domain.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findByEntityTypeAndEntityIdOrderByChangedAtAsc(EntityType entityType, Long entityId);
}

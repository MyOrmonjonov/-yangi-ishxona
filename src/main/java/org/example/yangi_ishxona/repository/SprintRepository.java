package org.example.yangi_ishxona.repository;

import org.example.yangi_ishxona.domain.Project;
import org.example.yangi_ishxona.domain.Sprint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SprintRepository extends JpaRepository<Sprint, Long> {
    List<Sprint> findByProjectOrderByDeadlineAsc(Project project);
    List<Sprint> findByProjectIdOrderByDeadlineAsc(Long projectId);
}

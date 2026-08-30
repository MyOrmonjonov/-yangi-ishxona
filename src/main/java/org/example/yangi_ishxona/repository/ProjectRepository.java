package org.example.yangi_ishxona.repository;

import org.example.yangi_ishxona.domain.AppUser;
import org.example.yangi_ishxona.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByResponsibleOrderByDeadlineAsc(AppUser responsible);
    List<Project> findAllByOrderByDeadlineAsc();
}

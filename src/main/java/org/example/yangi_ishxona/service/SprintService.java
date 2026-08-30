package org.example.yangi_ishxona.service;

import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.AppUser;
import org.example.yangi_ishxona.domain.Project;
import org.example.yangi_ishxona.domain.RollupStatus;
import org.example.yangi_ishxona.domain.Sprint;
import org.example.yangi_ishxona.repository.SprintRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository sprintRepository;
    private final StatusRollupService statusRollupService;

    @Transactional
    public Sprint create(AppUser creator, Project project, String name, LocalDate deadline) {
        if (name == null || name.isBlank()) {
            throw new DomainException("error.sprint.nameRequired");
        }
        if (deadline == null) {
            throw new DomainException("error.sprint.deadlineRequired");
        }
        if (deadline.isAfter(project.getDeadline())) {
            throw new DomainException("error.sprint.deadlineAfterProject", DeadlineParser.format(project.getDeadline()));
        }
        Sprint sprint = new Sprint();
        sprint.setProject(project);
        sprint.setName(name.trim());
        sprint.setResponsible(creator);
        sprint.setDeadline(deadline);
        sprint.setStatus(RollupStatus.NOT_STARTED);
        Sprint saved = sprintRepository.save(sprint);
        statusRollupService.recalculateProject(project);
        return saved;
    }

    public Optional<Sprint> findById(Long id) {
        return sprintRepository.findById(id);
    }

    public List<Sprint> findByProject(Project project) {
        return sprintRepository.findByProjectOrderByDeadlineAsc(project);
    }
}

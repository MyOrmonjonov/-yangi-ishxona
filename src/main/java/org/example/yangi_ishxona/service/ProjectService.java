package org.example.yangi_ishxona.service;

import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.*;
import org.example.yangi_ishxona.repository.ProjectRepository;
import org.example.yangi_ishxona.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserService userService;

    @Transactional
    public Project create(AppUser creator, String name, String description, String customer, LocalDate deadline) {
        if (name == null || name.isBlank()) {
            throw new DomainException("error.project.nameRequired");
        }
        if (deadline == null) {
            throw new DomainException("error.project.deadlineRequired");
        }
        Project project = new Project();
        project.setName(name.trim());
        project.setDescription(description);
        project.setCustomer(customer);
        project.setResponsible(creator);
        project.setDeadline(deadline);
        project.setStatus(RollupStatus.NOT_STARTED);
        Project saved = projectRepository.save(project);
        userService.promoteToProjectManagerIfNeeded(creator);
        return saved;
    }

    public Optional<Project> findById(Long id) {
        return projectRepository.findById(id);
    }

    public boolean canView(AppUser user, Project project) {
        if (user.getRole() == Role.DIRECTOR) {
            return true;
        }
        if (project.getResponsible().getId().equals(user.getId())) {
            return true;
        }
        return !taskRepository.findByExecutorOrderByDeadlineAsc(user).stream()
                .filter(t -> t.getSprint().getProject().getId().equals(project.getId()))
                .toList().isEmpty();
    }

    public Project getForView(AppUser user, Long projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new DomainException("error.project.notFound"));
        if (!canView(user, project)) {
            throw new DomainException("error.project.noAccess");
        }
        return project;
    }

    public List<Project> visibleProjectsFor(AppUser user) {
        if (user.getRole() == Role.DIRECTOR) {
            return projectRepository.findAllByOrderByDeadlineAsc();
        }
        List<Project> managed = projectRepository.findByResponsibleOrderByDeadlineAsc(user);
        List<Project> viaTasks = taskRepository.findByExecutorOrderByDeadlineAsc(user).stream()
                .map(t -> t.getSprint().getProject())
                .distinct()
                .toList();
        return java.util.stream.Stream.concat(managed.stream(), viaTasks.stream())
                .distinct()
                .sorted(Comparator.comparing(Project::getDeadline))
                .toList();
    }

    public List<Project> allForSelection() {
        return projectRepository.findAllByOrderByDeadlineAsc();
    }

    public List<Project> allProjectsManagedBy(AppUser user) {
        return projectRepository.findByResponsibleOrderByDeadlineAsc(user);
    }
}

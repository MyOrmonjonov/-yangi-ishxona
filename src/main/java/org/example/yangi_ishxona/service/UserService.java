package org.example.yangi_ishxona.service;

import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.AppUser;
import org.example.yangi_ishxona.domain.Role;
import org.example.yangi_ishxona.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository userRepository;

    /**
     * Telegram user_id of the person who should become DIRECTOR on first /start.
     * If unset, the very first person ever to register becomes DIRECTOR instead
     * (see {@link #resolveInitialRole}) - documented in README as an accepted assumption.
     */
    @Value("${app.bootstrap.director-telegram-id:}")
    private String bootstrapDirectorTelegramId;

    @Transactional
    public AppUser registerOrGet(Long telegramUserId, String telegramUsername, String fullName, String position) {
        return userRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> {
                    AppUser user = new AppUser(telegramUserId, fullName, position, resolveInitialRole(telegramUserId));
                    user.setTelegramUsername(telegramUsername);
                    return userRepository.save(user);
                });
    }

    private Role resolveInitialRole(Long telegramUserId) {
        String configured = bootstrapDirectorTelegramId == null ? "" : bootstrapDirectorTelegramId.trim();
        if (!configured.isEmpty()) {
            try {
                return Long.parseLong(configured) == telegramUserId ? Role.DIRECTOR : Role.EMPLOYEE;
            } catch (NumberFormatException ignored) {
                // fall through to "first user" bootstrap
            }
        }
        return userRepository.count() == 0 ? Role.DIRECTOR : Role.EMPLOYEE;
    }

    public Optional<AppUser> findByTelegramId(Long telegramUserId) {
        return userRepository.findByTelegramUserId(telegramUserId);
    }

    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<AppUser> allUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public AppUser promoteToProjectManagerIfNeeded(AppUser user) {
        if (user.getRole() == Role.EMPLOYEE) {
            user.setRole(Role.PROJECT_MANAGER);
            return userRepository.save(user);
        }
        return user;
    }

    @Transactional
    public AppUser updateLanguage(AppUser user, org.example.yangi_ishxona.domain.Language language) {
        user.setLanguage(language);
        return userRepository.save(user);
    }

    @Transactional
    public void setRole(AppUser actor, AppUser target, Role newRole) {
        if (actor.getRole() != Role.DIRECTOR) {
            throw new DomainException("error.role.onlyDirector");
        }
        target.setRole(newRole);
        userRepository.save(target);
    }
}

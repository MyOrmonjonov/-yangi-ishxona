package org.example.yangi_ishxona.repository;

import org.example.yangi_ishxona.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByTelegramUserId(Long telegramUserId);
    boolean existsByTelegramUserId(Long telegramUserId);
    Optional<AppUser> findByTelegramUsernameIgnoreCase(String telegramUsername);
}

package uz.taskapp.group;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TelegramKnownChatRepository extends JpaRepository<TelegramKnownChatEntity, Long> {
    List<TelegramKnownChatEntity> findAllByActiveTrue();
}
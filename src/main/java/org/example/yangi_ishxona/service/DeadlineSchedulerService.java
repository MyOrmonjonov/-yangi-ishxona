package org.example.yangi_ishxona.service;

import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.*;
import org.example.yangi_ishxona.repository.DeadlineNotificationLogRepository;
import org.example.yangi_ishxona.repository.TaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Deadline watchdog per ТЗ 3.6. Two-phase design so the "no notifications at night"
 * rule (3.6, окно 09:00-20:00 Asia/Tashkent) is just "queue now, flush when the
 * window opens" - the {@link DeadlineNotificationLog} row is what makes both phases
 * idempotent across repeated scheduler runs.
 */
@Service
@RequiredArgsConstructor
public class DeadlineSchedulerService {

    private static final ZoneId TASHKENT = ZoneId.of("Asia/Tashkent");
    private static final LocalTime WINDOW_START = LocalTime.of(9, 0);
    private static final LocalTime WINDOW_END = LocalTime.of(20, 0);

    private final TaskRepository taskRepository;
    private final DeadlineNotificationLogRepository logRepository;
    private final TelegramNotifier notifier;

    @Scheduled(fixedRateString = "${app.scheduler.deadline-check-interval-ms:600000}")
    @Transactional
    public void checkDeadlines() {
        Instant now = Instant.now();
        List<Task> activeTasks = taskRepository.findByStatusNotIn(List.of(TaskStatus.DONE, TaskStatus.CANCELLED));

        for (Task task : activeTasks) {
            Instant deadlineInstant = task.getDeadline().atTime(LocalTime.MAX).atZone(TASHKENT).toInstant();
            queueIfDue(task, NotificationType.T_MINUS_24H, deadlineInstant.minus(24, ChronoUnit.HOURS), now);
            if (task.getStatus() != TaskStatus.REVIEW) {
                queueIfDue(task, NotificationType.AT_DEADLINE, deadlineInstant, now);
            }
            queueIfDue(task, NotificationType.OVERDUE_24H, deadlineInstant.plus(24, ChronoUnit.HOURS), now);
        }

        if (isWithinSendWindow(now)) {
            dispatchQueued(now);
        }
    }

    private void queueIfDue(Task task, NotificationType type, Instant triggerAt, Instant now) {
        if (now.isBefore(triggerAt)) {
            return;
        }
        if (logRepository.findByTaskAndNotificationType(task, type).isPresent()) {
            return;
        }
        logRepository.save(new DeadlineNotificationLog(task, type, triggerAt));
    }

    private boolean isWithinSendWindow(Instant now) {
        LocalTime local = now.atZone(TASHKENT).toLocalTime();
        return !local.isBefore(WINDOW_START) && local.isBefore(WINDOW_END);
    }

    private void dispatchQueued(Instant now) {
        for (DeadlineNotificationLog log : logRepository.findBySentAtIsNull()) {
            if (log.getScheduledFor().isAfter(now)) {
                continue;
            }
            send(log);
            log.setSentAt(now);
            logRepository.save(log);
        }
    }

    private void send(DeadlineNotificationLog log) {
        Task task = log.getTask();
        AppUser executor = task.getExecutor();
        switch (log.getNotificationType()) {
            case T_MINUS_24H -> notifier.sendMessageWithButtons(executor.getTelegramUserId(),
                    "⚠️ До дедлайна по задаче «" + task.getName() + "» осталось 24 часа. Что сделано по задаче?",
                    List.of(List.of(
                            new NotifyButton("Отчитаться", "report:" + task.getId()),
                            new NotifyButton("Уже готово", "review:" + task.getId()),
                            new NotifyButton("Прошу перенести срок", "postpone:" + task.getId())
                    )));
            case AT_DEADLINE -> notifier.sendMessage(executor.getTelegramUserId(),
                    "⏰ Дедлайн по задаче «" + task.getName() + "» наступил, но она ещё не закрыта. Пожалуйста, обновите статус.");
            case OVERDUE_24H -> {
                AppUser manager = task.getSprint().getProject().getResponsible();
                notifier.sendMessage(manager.getTelegramUserId(),
                        "❌ Задача «" + task.getName() + "» просрочена более чем на 24 часа (исполнитель: "
                                + executor.getFullName() + ").");
            }
        }
    }
}

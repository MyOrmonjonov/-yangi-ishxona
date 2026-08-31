package uz.taskapp.task;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import uz.taskapp.telegram.TelegramTaskNotificationService;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;

@Service
public class TaskReminderScheduler {
    private final JdbcTemplate jdbcTemplate;
    private final TelegramTaskNotificationService notificationService;

    public TaskReminderScheduler(JdbcTemplate jdbcTemplate,
                                 TelegramTaskNotificationService notificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationService = notificationService;
    }

    @Scheduled(initialDelay = 15_000, fixedDelay = 30_000)
    public void deliverPendingReminders() {
        List<PendingReminder> reminders = jdbcTemplate.query("""
                SELECT r.id, t.id AS task_id, t.title, t.due_at, t.visibility,
                       t.telegram_message_id, t.topic_id, g.telegram_chat_id, u.telegram_id
                FROM task_reminders r
                JOIN tasks t ON t.id = r.task_id
                LEFT JOIN groups g ON g.id = t.group_id
                LEFT JOIN users u ON u.id = r.user_id
                WHERE r.sent_at IS NULL
                  AND r.remind_at <= CURRENT_TIMESTAMP
                  AND t.deleted_at IS NULL
                  AND t.status NOT IN ('COMPLETED', 'CANCELLED')
                  AND (t.visibility = 'GROUP' OR u.reminders_enabled = TRUE)
                ORDER BY r.remind_at
                LIMIT 25
                """, (rs, rowNum) -> {
            boolean group = "GROUP".equals(rs.getString("visibility"));
            Long chatId = rs.getObject(group ? "telegram_chat_id" : "telegram_id", Long.class);
            return new PendingReminder(rs.getLong("id"), rs.getLong("task_id"), rs.getString("title"),
                    rs.getTimestamp("due_at") == null ? null : rs.getTimestamp("due_at").toInstant(),
                    chatId, group, rs.getObject("telegram_message_id", Long.class),
                    rs.getObject("topic_id", Long.class));
        });
        for (PendingReminder reminder : reminders) {
            Long sentMessageId = notificationService.sendReminder(
                    new TelegramTaskNotificationService.ReminderTelegramMessage(
                            reminder.taskId(), reminder.chatId(), reminder.title(), reminder.dueAt(),
                            reminder.groupMessage(), false, reminder.messageId(), reminder.topicId(),
                            reminder.groupMessage() ? taskAssignees(reminder.taskId()) : List.of(), null));
            if (sentMessageId != null) {
                jdbcTemplate.update("UPDATE task_reminders SET sent_at = ? WHERE id = ? AND sent_at IS NULL",
                        Timestamp.from(Instant.now()), reminder.id());
            }
        }
    }

    @Scheduled(initialDelay = 20_000, fixedDelay = 60_000)
    public void deliverOverdueReminders() {
        List<OverdueTask> tasks = jdbcTemplate.query("""
                SELECT t.id, t.title, t.due_at, t.visibility, t.telegram_message_id, t.topic_id,
                       t.overdue_reminder_message_id, g.telegram_chat_id
                FROM tasks t
                LEFT JOIN groups g ON g.id = t.group_id
                WHERE t.deleted_at IS NULL
                  AND t.due_at IS NOT NULL
                  AND t.due_at < CURRENT_TIMESTAMP
                  AND t.status NOT IN ('COMPLETED', 'CANCELLED')
                  AND (t.overdue_notified_at IS NULL
                       OR t.overdue_notified_at <= CURRENT_TIMESTAMP - INTERVAL '30 minutes')
                ORDER BY t.due_at
                LIMIT 25
                """, (rs, rowNum) -> new OverdueTask(rs.getLong("id"), rs.getString("title"),
                rs.getTimestamp("due_at").toInstant(),
                "GROUP".equals(rs.getString("visibility")),
                rs.getObject("telegram_chat_id", Long.class),
                rs.getObject("telegram_message_id", Long.class),
                rs.getObject("topic_id", Long.class),
                rs.getObject("overdue_reminder_message_id", Long.class)));
        for (OverdueTask task : tasks) {
            if (task.groupTask() && task.chatId() != null) {
                Long sentMessageId = notificationService.sendReminder(
                        new TelegramTaskNotificationService.ReminderTelegramMessage(
                                task.id(), task.chatId(), task.title(), task.dueAt(), true, true,
                                task.messageId(), task.topicId(), taskAssignees(task.id()),
                                task.reminderMessageId()));
                if (sentMessageId != null) {
                    jdbcTemplate.update("UPDATE tasks SET overdue_reminder_message_id = ? WHERE id = ?",
                            sentMessageId, task.id());
                }
            }
            List<Long> assigneeChatIds = jdbcTemplate.query("""
                    SELECT u.telegram_id
                    FROM task_assignees ta
                    JOIN users u ON u.id = ta.user_id
                    WHERE ta.task_id = ? AND u.reminders_enabled = TRUE AND u.telegram_id IS NOT NULL
                    """, (rs, rowNum) -> rs.getLong("telegram_id"), task.id());
            for (Long chatId : assigneeChatIds) {
                notificationService.sendReminder(new TelegramTaskNotificationService.ReminderTelegramMessage(
                        task.id(), chatId, task.title(), task.dueAt(), false, true,
                        task.messageId(), task.topicId(), List.of(), null));
            }
            jdbcTemplate.update("UPDATE tasks SET overdue_notified_at = ? WHERE id = ?",
                    Timestamp.from(Instant.now()), task.id());
        }
    }

    private List<TelegramTaskNotificationService.Assignee> taskAssignees(Long taskId) {
        return jdbcTemplate.query("""
                SELECT u.first_name, u.last_name, u.telegram_id
                FROM task_assignees ta
                JOIN users u ON u.id = ta.user_id
                WHERE ta.task_id = ?
                """, (rs, rowNum) -> {
            String firstName = rs.getString("first_name");
            String lastName = rs.getString("last_name");
            String name = lastName == null || lastName.isBlank() ? firstName : firstName + " " + lastName;
            return new TelegramTaskNotificationService.Assignee(name, rs.getObject("telegram_id", Long.class));
        }, taskId);
    }

    private record PendingReminder(Long id, Long taskId, String title, Instant dueAt,
                                   Long chatId, boolean groupMessage, Long messageId, Long topicId) {}

    private record OverdueTask(Long id, String title, Instant dueAt, boolean groupTask, Long chatId,
                               Long messageId, Long topicId, Long reminderMessageId) {}
}

package uz.taskapp.statistics;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.taskapp.common.ApiException;
import uz.taskapp.group.GroupEntity;
import uz.taskapp.group.GroupRepository;
import uz.taskapp.user.UserEntity;
import uz.taskapp.user.UserRepository;
import uz.taskapp.workspace.WorkspaceMemberEntity;
import uz.taskapp.workspace.WorkspaceMemberRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatisticsService {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Tashkent");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final JdbcTemplate jdbcTemplate;
    private final WorkspaceMemberRepository memberRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;

    public StatisticsService(JdbcTemplate jdbcTemplate, WorkspaceMemberRepository memberRepository,
                             GroupRepository groupRepository, UserRepository userRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public StatisticsResponse compute(Long userId, Long workspaceId) {
        if (!memberRepository.existsByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(workspaceId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WORKSPACE_ACCESS_DENIED", "Ish maydoniga kirishga ruxsat yo'q");
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        Instant todayStart = today.atStartOfDay(APP_ZONE).toInstant();
        Instant todayEnd = today.plusDays(1).atStartOfDay(APP_ZONE).toInstant();
        LocalDate weekStartDate = today.minusDays(today.getDayOfWeek().getValue() - 1);
        Instant weekStart = weekStartDate.atStartOfDay(APP_ZONE).toInstant();
        Instant weekEnd = weekStartDate.plusDays(7).atStartOfDay(APP_ZONE).toInstant();
        Instant now = Instant.now();
        Instant thirtyDaysAgo = today.minusDays(29).atStartOfDay(APP_ZONE).toInstant();

        int totalTasks = count("SELECT COUNT(*) FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL", workspaceId);
        int openCount = count("SELECT COUNT(*) FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL " +
                "AND status NOT IN ('COMPLETED','CANCELLED')", workspaceId);
        int todayCount = count("SELECT COUNT(*) FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL " +
                "AND due_at >= ? AND due_at < ?", workspaceId, Timestamp.from(todayStart), Timestamp.from(todayEnd));
        int weekCount = count("SELECT COUNT(*) FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL " +
                "AND due_at >= ? AND due_at < ?", workspaceId, Timestamp.from(weekStart), Timestamp.from(weekEnd));
        int overdueCount = count("SELECT COUNT(*) FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL " +
                "AND due_at < ? AND status NOT IN ('COMPLETED','CANCELLED')", workspaceId, Timestamp.from(now));
        int last30Created = count("SELECT COUNT(*) FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL " +
                "AND created_at >= ?", workspaceId, Timestamp.from(thirtyDaysAgo));
        int unassignedCount = count("SELECT COUNT(*) FROM tasks t WHERE t.workspace_id = ? AND t.deleted_at IS NULL " +
                "AND t.status NOT IN ('COMPLETED','CANCELLED') " +
                "AND NOT EXISTS (SELECT 1 FROM task_assignees a WHERE a.task_id = t.id)", workspaceId);
        int groupsCount = groupRepository.findAllByWorkspaceIdAndActiveTrueOrderByNameAsc(workspaceId).size();

        int completedToday = count("SELECT COUNT(*) FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL " +
                "AND completed_at >= ? AND completed_at < ?", workspaceId, Timestamp.from(todayStart), Timestamp.from(todayEnd));
        int focusRate = todayCount == 0 ? 100 : Math.round(Math.min(1f, completedToday / (float) todayCount) * 100);
        String focusTitle = jdbcTemplate.query(
                "SELECT title FROM tasks t WHERE t.workspace_id = ? AND t.deleted_at IS NULL " +
                        "AND t.status NOT IN ('COMPLETED','CANCELLED') " +
                        "AND (t.visibility <> 'ONE_TO_ONE' OR t.author_id = ? " +
                        "OR EXISTS (SELECT 1 FROM task_assignees a WHERE a.task_id = t.id AND a.user_id = ?)) " +
                        "ORDER BY (t.due_at IS NULL), t.due_at ASC, " +
                        "CASE t.priority WHEN 'URGENT' THEN 0 WHEN 'IMPORTANT' THEN 1 WHEN 'NORMAL' THEN 2 ELSE 3 END " +
                        "LIMIT 1",
                (rs, rowNum) -> rs.getString("title"), workspaceId, userId, userId)
                .stream().findFirst().orElse(null);

        List<QuickStat> quickStats = List.of(
                new QuickStat("OVERDUE", "Kechikkan", String.valueOf(overdueCount)),
                new QuickStat("TODAY", "Bugun", String.valueOf(todayCount)),
                new QuickStat("UNASSIGNED", "Mas'ulsiz", String.valueOf(unassignedCount)),
                new QuickStat("GROUPS", "Guruhlar", String.valueOf(groupsCount)));

        List<RhythmPoint> rhythm = buildRhythm(workspaceId, today);
        List<GroupBreakdown> groupBreakdown = buildGroupBreakdown(workspaceId);
        List<WorkloadItem> workloadSummary = buildWorkloadSummary(groupBreakdown, unassignedCount, overdueCount);
        List<StatusCount> statusBreakdown = buildStatusBreakdown(workspaceId);
        List<PriorityCount> priorityBreakdown = buildPriorityBreakdown(workspaceId);
        List<AssigneeWorkload> assigneeWorkload = buildAssigneeWorkload(workspaceId);

        return new StatisticsResponse(totalTasks, openCount, todayCount, weekCount, overdueCount,
                overdueCount == 0 ? "Risk yo'q" : overdueCount + " ta xavfli",
                last30Created, new FocusInfo(focusTitle, focusRate, completedToday),
                quickStats, rhythm, workloadSummary, statusBreakdown, priorityBreakdown,
                assigneeWorkload, groupBreakdown);
    }

    private List<RhythmPoint> buildRhythm(Long workspaceId, LocalDate today) {
        LocalDate start = today.minusDays(29);
        Instant rangeStart = start.atStartOfDay(APP_ZONE).toInstant();
        Map<String, int[]> byDay = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            byDay.put(day.format(DAY_FORMAT), new int[]{0, 0});
        }
        jdbcTemplate.query("SELECT created_at FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL AND created_at >= ?",
                rs -> {
                    String day = toAppDate(rs.getTimestamp("created_at").toInstant());
                    int[] bucket = byDay.get(day);
                    if (bucket != null) bucket[0]++;
                }, workspaceId, Timestamp.from(rangeStart));
        jdbcTemplate.query("SELECT completed_at FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL " +
                        "AND completed_at IS NOT NULL AND completed_at >= ?",
                rs -> {
                    String day = toAppDate(rs.getTimestamp("completed_at").toInstant());
                    int[] bucket = byDay.get(day);
                    if (bucket != null) bucket[1]++;
                }, workspaceId, Timestamp.from(rangeStart));
        List<RhythmPoint> points = new ArrayList<>();
        byDay.forEach((day, counts) -> points.add(new RhythmPoint(day, counts[0], counts[1])));
        return points;
    }

    private String toAppDate(Instant instant) {
        return instant.atZone(APP_ZONE).toLocalDate().format(DAY_FORMAT);
    }

    private List<GroupBreakdown> buildGroupBreakdown(Long workspaceId) {
        List<GroupEntity> groups = groupRepository.findAllByWorkspaceIdAndActiveTrueOrderByNameAsc(workspaceId);
        List<GroupBreakdown> result = new ArrayList<>();
        for (GroupEntity group : groups) {
            int total = count("SELECT COUNT(*) FROM tasks WHERE group_id = ? AND deleted_at IS NULL", group.getId());
            int active = count("SELECT COUNT(*) FROM tasks WHERE group_id = ? AND deleted_at IS NULL " +
                    "AND status NOT IN ('COMPLETED','CANCELLED')", group.getId());
            result.add(new GroupBreakdown(group.getName(), active, total));
        }
        return result;
    }

    private List<WorkloadItem> buildWorkloadSummary(List<GroupBreakdown> groupBreakdown, int unassignedCount,
                                                     int overdueCount) {
        List<WorkloadItem> items = new ArrayList<>();
        groupBreakdown.stream()
                .sorted((left, right) -> Integer.compare(right.active(), left.active()))
                .limit(3)
                .forEach(group -> items.add(new WorkloadItem(group.name(), group.active() + "/" + group.total() + " faol")));
        items.add(new WorkloadItem("Mas'ulsiz", unassignedCount == 0 ? "Mas'ullar kesimi bo'sh" : unassignedCount + " ta vazifa"));
        items.add(new WorkloadItem("Yuqori prioritet", overdueCount == 0 ? "0 shoshilinch · 0 yuqori" : overdueCount + " ta e'tibor talab qiladi"));
        return items;
    }

    private List<StatusCount> buildStatusBreakdown(Long workspaceId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String status : List.of("NEW", "IN_PROGRESS", "BLOCKED", "REVIEW", "COMPLETED", "CANCELLED")) {
            counts.put(status, 0);
        }
        jdbcTemplate.query("SELECT status, COUNT(*) AS total FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL " +
                        "GROUP BY status",
                rs -> { counts.put(rs.getString("status"), rs.getInt("total")); }, workspaceId);
        List<StatusCount> result = new ArrayList<>();
        counts.forEach((status, total) -> result.add(new StatusCount(status, total)));
        return result;
    }

    private List<PriorityCount> buildPriorityBreakdown(Long workspaceId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String priority : List.of("URGENT", "IMPORTANT", "NORMAL", "LOW")) {
            counts.put(priority, 0);
        }
        jdbcTemplate.query("SELECT priority, COUNT(*) AS total FROM tasks WHERE workspace_id = ? AND deleted_at IS NULL " +
                        "AND status NOT IN ('COMPLETED','CANCELLED') GROUP BY priority",
                rs -> { counts.put(rs.getString("priority"), rs.getInt("total")); }, workspaceId);
        List<PriorityCount> result = new ArrayList<>();
        counts.forEach((priority, total) -> result.add(new PriorityCount(priority, total)));
        return result;
    }

    private List<AssigneeWorkload> buildAssigneeWorkload(Long workspaceId) {
        List<WorkspaceMemberEntity> memberships = memberRepository.findAllByWorkspaceIdAndActiveTrue(workspaceId);
        List<AssigneeWorkload> result = new ArrayList<>();
        for (WorkspaceMemberEntity membership : memberships) {
            int active = count("SELECT COUNT(*) FROM task_assignees a JOIN tasks t ON t.id = a.task_id " +
                    "WHERE a.user_id = ? AND t.workspace_id = ? AND t.deleted_at IS NULL " +
                    "AND t.status NOT IN ('COMPLETED','CANCELLED')", membership.getUserId(), workspaceId);
            int overdue = count("SELECT COUNT(*) FROM task_assignees a JOIN tasks t ON t.id = a.task_id " +
                    "WHERE a.user_id = ? AND t.workspace_id = ? AND t.deleted_at IS NULL " +
                    "AND t.status NOT IN ('COMPLETED','CANCELLED') AND t.due_at < CURRENT_TIMESTAMP",
                    membership.getUserId(), workspaceId);
            if (active == 0 && overdue == 0) continue;
            String name = userRepository.findById(membership.getUserId()).map(this::displayName).orElse("Foydalanuvchi");
            result.add(new AssigneeWorkload(name, active, overdue));
        }
        result.sort((left, right) -> Integer.compare(right.active(), left.active()));
        return result;
    }

    private String displayName(UserEntity user) {
        return user.getLastName() == null || user.getLastName().isBlank()
                ? user.getFirstName()
                : user.getFirstName() + " " + user.getLastName();
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    public record StatisticsResponse(
            int totalTasks,
            int openCount,
            int todayCount,
            int weekCount,
            int overdueCount,
            String overdueRiskLabel,
            int last30DaysCreated,
            FocusInfo focus,
            List<QuickStat> quickStats,
            List<RhythmPoint> rhythm,
            List<WorkloadItem> workloadSummary,
            List<StatusCount> statusBreakdown,
            List<PriorityCount> priorityBreakdown,
            List<AssigneeWorkload> assigneeWorkload,
            List<GroupBreakdown> groupBreakdown) {
    }

    public record FocusInfo(String taskTitle, int completionRate, int completedToday) {
    }

    public record QuickStat(String key, String label, String value) {
    }

    public record RhythmPoint(String date, int created, int completed) {
    }

    public record WorkloadItem(String label, String value) {
    }

    public record StatusCount(String status, int count) {
    }

    public record PriorityCount(String priority, int count) {
    }

    public record AssigneeWorkload(String name, int active, int overdue) {
    }

    public record GroupBreakdown(String name, int active, int total) {
    }
}

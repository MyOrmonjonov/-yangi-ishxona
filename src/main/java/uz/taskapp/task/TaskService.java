package uz.taskapp.task;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.AppProperties;
import uz.taskapp.group.GroupEntity;
import uz.taskapp.group.GroupMemberId;
import uz.taskapp.group.GroupMemberRepository;
import uz.taskapp.group.GroupRepository;
import uz.taskapp.group.GroupTopicEntity;
import uz.taskapp.group.GroupTopicId;
import uz.taskapp.group.GroupTopicRepository;
import uz.taskapp.user.UserEntity;
import uz.taskapp.user.UserRepository;
import uz.taskapp.workspace.WorkspaceMemberRepository;
import uz.taskapp.workspace.WorkspaceMemberEntity;
import uz.taskapp.realtime.WorkspaceBroadcastService;
import uz.taskapp.telegram.TelegramTaskNotificationService;
import uz.taskapp.voice.VoiceDraftService;

import java.io.IOException;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
public class TaskService {
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Tashkent");
    private static final long MAX_FILE_SIZE_BYTES = 8L * 1024 * 1024;
    private static final long MAX_TASK_UPLOAD_BYTES = 20L * 1024 * 1024;

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository assigneeRepository;
    private final TaskFileRepository fileRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupTopicRepository groupTopicRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TelegramTaskNotificationService taskNotificationService;
    private final WorkspaceBroadcastService broadcastService;
    private final VoiceDraftService voiceDraftService;
    private final Path uploadDirectory;
    private final Clock clock = Clock.systemUTC();

    public TaskService(TaskRepository taskRepository,
                       TaskAssigneeRepository assigneeRepository,
                       TaskFileRepository fileRepository,
                       WorkspaceMemberRepository memberRepository,
                       UserRepository userRepository,
                       GroupRepository groupRepository,
                       GroupMemberRepository groupMemberRepository,
                       GroupTopicRepository groupTopicRepository,
                       AppProperties appProperties,
                       JdbcTemplate jdbcTemplate,
                       TelegramTaskNotificationService taskNotificationService,
                       WorkspaceBroadcastService broadcastService,
                       VoiceDraftService voiceDraftService) {
        this.taskRepository = taskRepository;
        this.assigneeRepository = assigneeRepository;
        this.fileRepository = fileRepository;
        this.memberRepository = memberRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupTopicRepository = groupTopicRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.taskNotificationService = taskNotificationService;
        this.broadcastService = broadcastService;
        this.voiceDraftService = voiceDraftService;
        this.uploadDirectory = Paths.get(appProperties.uploadDirectory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException("Upload directory yaratib bo'lmadi", exception);
        }
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(Long currentUserId, Long workspaceId, TaskScope scope) {
        requireMembership(workspaceId, currentUserId);
        List<TaskEntity> tasks = taskRepository.findAllByWorkspaceIdAndDeletedAtIsNullOrderByCreatedAtDesc(workspaceId);
        Map<Long, List<Long>> assignees = assigneesByTask(tasks.stream().map(TaskEntity::getId).toList());
        Map<Long, ChecklistSummary> checklistSummaries = checklistSummaries(tasks.stream().map(TaskEntity::getId).toList());
        Map<Long, Integer> fileCounts = fileCounts(tasks.stream().map(TaskEntity::getId).toList());
        Map<Long, String> authorNames = authorNames(tasks.stream().map(TaskEntity::getAuthorId).collect(Collectors.toSet()));
        Instant now = clock.instant();
        LocalDate today = LocalDate.now(clock.withZone(APP_ZONE));
        return tasks.stream()
                .filter(task -> visibleTo(task, assignees.getOrDefault(task.getId(), List.of()), currentUserId))
                .filter(task -> matchesScope(task, assignees.getOrDefault(task.getId(), List.of()), currentUserId,
                        scope == null ? TaskScope.ACTIVE : scope, now, today))
                .map(task -> toResponse(task,
                        assignees.getOrDefault(task.getId(), List.of()),
                        checklistSummaries.getOrDefault(task.getId(), ChecklistSummary.EMPTY),
                        fileCounts.getOrDefault(task.getId(), 0),
                        authorNames.getOrDefault(task.getAuthorId(), "Siz"), false))
                .toList();
    }

    @Transactional
    public TaskResponse create(Long currentUserId, CreateTaskRequest request) {
        return create(currentUserId, request, List.of());
    }

    @Transactional
    public TaskResponse create(Long currentUserId, CreateTaskRequest request, List<MultipartFile> files) {
        requireMembership(request.workspaceId(), currentUserId);
        if (request.dueAt() != null && !request.dueAt().isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DUE_DATE_IN_PAST", "O'tgan vaqtni muddat sifatida tanlab bo'lmaydi");
        }
        TaskVisibility visibility = request.visibility() == null ? TaskVisibility.WORKSPACE : request.visibility();
        GroupEntity selectedGroup = resolveGroup(request.workspaceId(), visibility, request.groupId(), currentUserId);
        Long groupId = selectedGroup == null ? null : selectedGroup.getId();
        Long topicId = resolveTopicId(groupId, request.topicId());
        Set<Long> assigneeIds = resolveAssignees(request.workspaceId(), visibility, groupId, currentUserId,
                request.assigneeIds());

        TaskEntity newTask = new TaskEntity(
                request.workspaceId(), groupId, topicId, currentUserId, request.title(), request.description(),
                request.status() == null ? TaskStatus.NEW : request.status(),
                request.priority() == null ? TaskPriority.NORMAL : request.priority(),
                visibility,
                request.dueAt());
        newTask.assignSequence(nextTaskSequence(request.workspaceId()));
        final TaskEntity task = taskRepository.save(newTask);
        assigneeRepository.saveAll(assigneeIds.stream()
                .map(userId -> new TaskAssigneeEntity(task.getId(), userId)).toList());
        persistChecklistItems(task.getId(), currentUserId, request.checklist());
        replaceReminder(task, currentUserId, request.reminderMinutes());
        persistFiles(task.getId(), currentUserId, files);
        insertHistory(task.getId(), currentUserId, "CREATED", "{\"title\":\"" + jsonEscape(task.getTitle()) + "\"}");
        if (selectedGroup != null) {
            TelegramTaskNotificationService.TaskTelegramMessage notification =
                    new TelegramTaskNotificationService.TaskTelegramMessage(
                            task.getId(), selectedGroup.getTelegramChatId(), topicId, null, task.getTitle(), task.getDescription(),
                            displayName(currentUserId), assigneeMentions(assigneeIds), task.getDueAt(), task.getStatus(), null,
                            telegramAttachment(task.getId()), false);
            publishAfterCommit(notification);
        }
        if (request.voiceDraftId() != null) {
            Long draftId = request.voiceDraftId();
            runAfterCommit(() -> deleteDraftPreviewMessage(draftId));
        }
        runAfterCommit(() -> broadcastService.notifyTaskChanged(request.workspaceId(), task.getId(), currentUserId));
        return toResponse(task,
                new ArrayList<>(assigneeIds),
                checklistSummaryFromRequest(request.checklist()),
                files == null ? 0 : files.size(),
                displayName(currentUserId), true, request.reminderMinutes());
    }

    @Transactional
    public TelegramActionResult actFromTelegram(Long taskId, Long telegramChatId, Long telegramUserId,
                                                 String firstName, String lastName, String username,
                                                 String languageCode, Long telegramMessageId,
                                                 TelegramTaskAction action) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Vazifa topilmadi"));
        if (task.getVisibility() != TaskVisibility.GROUP || task.getGroupId() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_NOT_GROUP", "Bu guruh vazifasi emas");
        }
        GroupEntity group = groupRepository.findById(task.getGroupId())
                .filter(item -> item.isActive() && item.getTelegramChatId().equals(telegramChatId))
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TELEGRAM_GROUP_MISMATCH",
                        "Vazifa boshqa guruhga tegishli"));

        UserEntity user = userRepository.findByTelegramId(telegramUserId)
                .orElseGet(() -> new UserEntity(telegramUserId,
                        firstName == null || firstName.isBlank() ? "Foydalanuvchi" : firstName));
        user.updateTelegramProfile(firstName == null || firstName.isBlank() ? "Foydalanuvchi" : firstName,
                lastName, username, user.getPhotoUrl(), languageCode);
        user.markBotConnected();
        user = userRepository.save(user);
        Long taskAppUserId = user.getId();

        WorkspaceMemberEntity workspaceMember = memberRepository
                .findByWorkspaceIdAndUserId(task.getWorkspaceId(), taskAppUserId)
                .orElseGet(() -> new WorkspaceMemberEntity(task.getWorkspaceId(), taskAppUserId, "MEMBER"));
        if (!workspaceMember.isActive()) workspaceMember.activate();
        memberRepository.save(workspaceMember);

        GroupMemberId groupMemberId = new GroupMemberId(group.getId(), taskAppUserId);
        if (!groupMemberRepository.existsById(groupMemberId)) {
            groupMemberRepository.save(new uz.taskapp.group.GroupMemberEntity(group.getId(), taskAppUserId));
        }

        boolean author = task.getAuthorId().equals(taskAppUserId);
        boolean assigned = assigneeRepository.existsByIdTaskIdAndIdUserId(taskId, taskAppUserId);
        boolean hasAnyAssignee = assigneeRepository.existsByIdTaskId(taskId);
        validateTelegramAction(task.getStatus(), action, author, assigned, hasAnyAssignee);
        boolean newlyAssigned = action == TelegramTaskAction.START && !assigned;
        if (newlyAssigned) {
            assigneeRepository.save(new TaskAssigneeEntity(taskId, taskAppUserId));
            assigned = true;
        }
        if (action != TelegramTaskAction.START && !author && !assigned) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ACTION_FORBIDDEN",
                    "Avval “Boshladim” tugmasini bosing");
        }
        TaskStatus previous = task.getStatus();
        TaskStatus next = switch (action) {
            case START, RESUME, RETURN, REOPEN -> TaskStatus.IN_PROGRESS;
            case PROBLEM -> TaskStatus.BLOCKED;
            case REVIEW -> TaskStatus.REVIEW;
            case APPROVE, COMPLETE -> TaskStatus.COMPLETED;
        };
        boolean statusChanged = previous != next;
        if (statusChanged) task.changeStatus(next);
        task.linkTelegramMessage(telegramMessageId);
        insertHistory(taskId, taskAppUserId, "TELEGRAM_ACTION",
                "{\"action\":\"" + action + "\",\"from\":\"" + previous +
                        "\",\"to\":\"" + next + "\",\"telegram_chat_id\":" + telegramChatId + "}");
        String actorName = displayName(user);
        syncTelegramAfterCommit(task, statusActivity(actorName, previous, next), false);
        runAfterCommit(() -> broadcastService.notifyTaskChanged(task.getWorkspaceId(), task.getId(), taskAppUserId));
        return new TelegramActionResult(taskAppUserId, actorName, action, next,
                newlyAssigned, statusChanged);
    }

    @Transactional
    public TaskResponse changeStatus(Long currentUserId, Long taskId, TaskStatus status) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Vazifa topilmadi"));
        requireMembership(task.getWorkspaceId(), currentUserId);
        boolean canChange = task.getAuthorId().equals(currentUserId)
                || assigneeRepository.existsByIdTaskIdAndIdUserId(taskId, currentUserId);
        if (!canChange) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_UPDATE_FORBIDDEN", "Vazifa statusini o'zgartirishga ruxsat yo'q");
        }
        TaskStatus previous = task.getStatus();
        task.changeStatus(status);
        insertHistory(taskId, currentUserId, "STATUS_CHANGED",
                "{\"from\":\"" + previous + "\",\"to\":\"" + status + "\"}");
        syncTelegramAfterCommit(task, statusActivity(displayName(currentUserId), previous, status), false);
        runAfterCommit(() -> broadcastService.notifyTaskChanged(task.getWorkspaceId(), task.getId(), currentUserId));
        return detail(currentUserId, taskId);
    }

    @Transactional
    public TaskResponse update(Long currentUserId, Long taskId, UpdateTaskRequest request) {
        return update(currentUserId, taskId, request, List.of());
    }

    @Transactional
    public TaskResponse update(Long currentUserId, Long taskId, UpdateTaskRequest request,
                               List<MultipartFile> files) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Vazifa topilmadi"));
        requireMembership(task.getWorkspaceId(), currentUserId);
        boolean isAuthor = task.getAuthorId().equals(currentUserId);
        boolean canEdit = isAuthor || assigneeRepository.existsByIdTaskIdAndIdUserId(taskId, currentUserId);
        if (!canEdit) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_UPDATE_FORBIDDEN",
                    "Vazifani tahrirlashga ruxsat yo'q");
        }
        TaskStatus previousStatus = task.getStatus();
        String previousTitle = task.getTitle();
        task.updateDetails(request.title(), request.description(), request.status(), request.priority(), request.dueAt(),
                Boolean.TRUE.equals(request.dueAtProvided()));

        if (request.visibility() != null) {
            Long requestedGroupId = request.visibility() == TaskVisibility.GROUP ? request.groupId() : null;
            Long requestedTopicId = resolveTopicId(requestedGroupId, request.topicId());
            boolean placeChanged = task.getVisibility() != request.visibility()
                    || !java.util.Objects.equals(task.getGroupId(), requestedGroupId)
                    || !java.util.Objects.equals(task.getTopicId(), requestedTopicId);
            if (placeChanged) {
                if (!isAuthor) {
                    throw new ApiException(HttpStatus.FORBIDDEN, "TASK_PLACE_CHANGE_FORBIDDEN",
                            "Faqat vazifa muallifi uni boshqa joyga ko'chira oladi");
                }
                GroupEntity newGroup = resolveGroup(task.getWorkspaceId(), request.visibility(), requestedGroupId,
                        currentUserId);
                task.changePlacement(request.visibility(), newGroup == null ? null : newGroup.getId(), requestedTopicId);
                insertHistory(taskId, currentUserId, "PLACE_CHANGED",
                        "{\"visibility\":\"" + task.getVisibility() + "\"}");
                if (request.assigneeIds() == null) {
                    List<Long> currentAssigneeIds = assigneeRepository.findAllByIdTaskIdIn(List.of(taskId)).stream()
                            .map(TaskAssigneeEntity::getUserId).toList();
                    resolveAssignees(task.getWorkspaceId(), task.getVisibility(), task.getGroupId(),
                            currentUserId, currentAssigneeIds);
                }
            }
        }

        if (request.authorId() != null && !request.authorId().equals(task.getAuthorId())) {
            if (!isAuthor) {
                throw new ApiException(HttpStatus.FORBIDDEN, "TASK_AUTHOR_CHANGE_FORBIDDEN",
                        "Faqat vazifa muallifi topshiriq beruvchini almashtira oladi");
            }
            requireMembership(task.getWorkspaceId(), request.authorId());
            task.changeAuthor(request.authorId());
            insertHistory(taskId, currentUserId, "AUTHOR_CHANGED", "{\"author_id\":" + request.authorId() + "}");
        }

        if (request.assigneeIds() != null) {
            Set<Long> assigneeIds = resolveAssignees(task.getWorkspaceId(), task.getVisibility(), task.getGroupId(),
                    currentUserId, request.assigneeIds());
            assigneeRepository.deleteAllByIdTaskId(taskId);
            assigneeRepository.saveAll(assigneeIds.stream()
                    .map(userId -> new TaskAssigneeEntity(taskId, userId)).toList());
            insertHistory(taskId, currentUserId, "ASSIGNEES_CHANGED", "{\"count\":" + assigneeIds.size() + "}");
            purgeReminderForRemovedAssignees(taskId, assigneeIds);
        }

        if (request.checklist() != null) {
            replaceChecklistItems(taskId, currentUserId, request.checklist());
        }
        if (Boolean.TRUE.equals(request.reminderProvided())) {
            replaceReminder(task, currentUserId, request.reminderMinutes());
        }
        persistFiles(taskId, currentUserId, files);
        insertHistory(taskId, currentUserId, "UPDATED",
                "{\"title_from\":\"" + jsonEscape(previousTitle) + "\",\"title_to\":\""
                        + jsonEscape(task.getTitle()) + "\",\"status_from\":\"" + previousStatus
                        + "\",\"status_to\":\"" + task.getStatus() + "\"}");
        String activity = previousStatus == task.getStatus() ? null
                : statusActivity(displayName(currentUserId), previousStatus, task.getStatus());
        syncTelegramAfterCommit(task, activity, files != null && !files.isEmpty());
        runAfterCommit(() -> broadcastService.notifyTaskChanged(task.getWorkspaceId(), task.getId(), currentUserId));
        List<Long> updatedAssignees = assigneeRepository.findAllByIdTaskIdIn(List.of(taskId)).stream()
                .map(TaskAssigneeEntity::getUserId).toList();
        ChecklistSummary updatedChecklist = checklistSummaries(List.of(taskId)).getOrDefault(taskId, ChecklistSummary.EMPTY);
        int updatedFileCount = fileCounts(List.of(taskId)).getOrDefault(taskId, 0);
        return toResponse(task, updatedAssignees, updatedChecklist, updatedFileCount,
                displayName(task.getAuthorId()), true, reminderMinutes(taskId));
    }

    @Transactional(readOnly = true)
    public TaskResponse detail(Long currentUserId, Long taskId) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Vazifa topilmadi"));
        requireMembership(task.getWorkspaceId(), currentUserId);
        List<Long> assignees = assigneeRepository.findAllByIdTaskIdIn(List.of(taskId)).stream()
                .map(TaskAssigneeEntity::getUserId).toList();
        if (!visibleTo(task, assignees, currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ACCESS_DENIED", "Vazifani ko'rishga ruxsat yo'q");
        }
        ChecklistSummary checklist = checklistSummaries(List.of(taskId)).getOrDefault(taskId, ChecklistSummary.EMPTY);
        int files = fileCounts(List.of(taskId)).getOrDefault(taskId, 0);
        return toResponse(task, assignees, checklist, files, displayName(task.getAuthorId()), true,
                reminderMinutes(taskId));
    }

    @Transactional(readOnly = true)
    public TaskFileContent fileContent(Long currentUserId, Long taskId, Long fileId) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Vazifa topilmadi"));
        requireMembership(task.getWorkspaceId(), currentUserId);
        List<Long> assignees = assigneeRepository.findAllByIdTaskIdIn(List.of(taskId)).stream()
                .map(TaskAssigneeEntity::getUserId).toList();
        if (!visibleTo(task, assignees, currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_FILE_ACCESS_DENIED", "Faylni ko'rishga ruxsat yo'q");
        }
        TaskFileEntity file = fileRepository.findByIdAndTaskId(fileId, taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_FILE_NOT_FOUND", "Fayl topilmadi"));
        byte[] content = file.getContentData();
        if (content == null || content.length == 0) {
            Path localFile = uploadDirectory.resolve(file.getStoredName()).normalize();
            if (!localFile.startsWith(uploadDirectory) || !Files.isRegularFile(localFile)) {
                throw new ApiException(HttpStatus.NOT_FOUND, "TASK_FILE_CONTENT_NOT_FOUND",
                        "Eski fayl serverda saqlanmagan. Uni vazifaga qayta biriktiring");
            }
            try {
                content = Files.readAllBytes(localFile);
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "TASK_FILE_READ_FAILED", "Fayl ochilmadi");
            }
        }
        return new TaskFileContent(file.getOriginalName(), file.getContentType(), content);
    }

    @Transactional
    public TaskResponse deleteFile(Long currentUserId, Long taskId, Long fileId) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Vazifa topilmadi"));
        requireMembership(task.getWorkspaceId(), currentUserId);
        boolean canEdit = task.getAuthorId().equals(currentUserId)
                || assigneeRepository.existsByIdTaskIdAndIdUserId(taskId, currentUserId);
        if (!canEdit) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_UPDATE_FORBIDDEN", "Faylni o'chirishga ruxsat yo'q");
        }
        TaskFileEntity file = fileRepository.findByIdAndTaskId(fileId, taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_FILE_NOT_FOUND", "Fayl topilmadi"));
        fileRepository.delete(file);
        Path localFile = uploadDirectory.resolve(file.getStoredName()).normalize();
        if (localFile.startsWith(uploadDirectory)) {
            try {
                Files.deleteIfExists(localFile);
            } catch (IOException ignored) {
                // Fayl kontenti bazadan o'chirildi; eski vaqtinchalik disk nusxasi kritik emas.
            }
        }
        insertHistory(taskId, currentUserId, "FILE_DELETED", "{\"file_id\":" + fileId + "}");
        syncTelegramAfterCommit(task, null, true);
        runAfterCommit(() -> broadcastService.notifyTaskChanged(task.getWorkspaceId(), task.getId(), currentUserId));
        return detail(currentUserId, taskId);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> listArchived(Long currentUserId, Long workspaceId) {
        requireMembership(workspaceId, currentUserId);
        boolean owner = isWorkspaceOwner(workspaceId, currentUserId);
        List<TaskEntity> tasks = taskRepository.findAllByWorkspaceIdAndDeletedAtIsNotNullOrderByDeletedAtDesc(workspaceId);
        Map<Long, List<Long>> assignees = assigneesByTask(tasks.stream().map(TaskEntity::getId).toList());
        Map<Long, ChecklistSummary> checklistSummaries = checklistSummaries(tasks.stream().map(TaskEntity::getId).toList());
        Map<Long, Integer> fileCounts = fileCounts(tasks.stream().map(TaskEntity::getId).toList());
        Map<Long, String> authorNames = authorNames(tasks.stream().map(TaskEntity::getAuthorId).collect(Collectors.toSet()));
        return tasks.stream()
                .filter(task -> owner || visibleTo(task, assignees.getOrDefault(task.getId(), List.of()), currentUserId))
                .map(task -> toResponse(task,
                        assignees.getOrDefault(task.getId(), List.of()),
                        checklistSummaries.getOrDefault(task.getId(), ChecklistSummary.EMPTY),
                        fileCounts.getOrDefault(task.getId(), 0),
                        authorNames.getOrDefault(task.getAuthorId(), "Siz"), false))
                .toList();
    }

    @Transactional
    public void archive(Long currentUserId, Long taskId) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Vazifa topilmadi"));
        requireMembership(task.getWorkspaceId(), currentUserId);
        if (!task.getAuthorId().equals(currentUserId) && !isWorkspaceOwner(task.getWorkspaceId(), currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ARCHIVE_FORBIDDEN",
                    "Faqat vazifa muallifi yoki ish maydoni egasi uni arxivlashi mumkin");
        }
        task.archive(currentUserId);
        insertHistory(taskId, currentUserId, "ARCHIVED", "{}");
        Long workspaceId = task.getWorkspaceId();
        runAfterCommit(() -> broadcastService.notifyTaskChanged(workspaceId, taskId, currentUserId));
    }

    @Transactional
    public TaskResponse restore(Long currentUserId, Long taskId) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNotNull(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "Vazifa topilmadi"));
        requireMembership(task.getWorkspaceId(), currentUserId);
        if (!task.getAuthorId().equals(currentUserId) && !isWorkspaceOwner(task.getWorkspaceId(), currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ARCHIVE_FORBIDDEN",
                    "Faqat vazifa muallifi yoki ish maydoni egasi uni arxivdan qaytara oladi");
        }
        task.restore();
        insertHistory(taskId, currentUserId, "RESTORED", "{}");
        runAfterCommit(() -> broadcastService.notifyTaskChanged(task.getWorkspaceId(), task.getId(), currentUserId));
        List<Long> assignees = assigneeRepository.findAllByIdTaskIdIn(List.of(taskId)).stream()
                .map(TaskAssigneeEntity::getUserId).toList();
        ChecklistSummary checklist = checklistSummaries(List.of(taskId)).getOrDefault(taskId, ChecklistSummary.EMPTY);
        int files = fileCounts(List.of(taskId)).getOrDefault(taskId, 0);
        return toResponse(task, assignees, checklist, files, displayName(task.getAuthorId()), true, reminderMinutes(taskId));
    }

    @Transactional
    public void hardDelete(Long currentUserId, Long taskId) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNotNull(taskId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND",
                        "Vazifa topilmadi yoki hali arxivlanmagan"));
        requireMembership(task.getWorkspaceId(), currentUserId);
        if (!task.getAuthorId().equals(currentUserId) && !isWorkspaceOwner(task.getWorkspaceId(), currentUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_DELETE_FORBIDDEN",
                    "Faqat vazifa muallifi yoki ish maydoni egasi uni butunlay o'chira oladi");
        }
        Long workspaceId = task.getWorkspaceId();
        taskRepository.delete(task);
        runAfterCommit(() -> broadcastService.notifyTaskChanged(workspaceId, taskId, currentUserId));
    }

    private boolean visibleTo(TaskEntity task, List<Long> assignees, Long userId) {
        return task.getVisibility() != TaskVisibility.ONE_TO_ONE
                || task.getAuthorId().equals(userId)
                || assignees.contains(userId);
    }

    private boolean matchesScope(TaskEntity task, List<Long> assignees, Long userId, TaskScope scope,
                                 Instant now, LocalDate today) {
        return switch (scope) {
            case ALL -> true;
            case MINE -> assignees.contains(userId) || task.getAuthorId().equals(userId);
            case ACTIVE -> task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELLED;
            case COMPLETED -> task.getStatus() == TaskStatus.COMPLETED;
            case OVERDUE -> task.getDueAt() != null && task.getDueAt().isBefore(now)
                    && task.getStatus() != TaskStatus.COMPLETED && task.getStatus() != TaskStatus.CANCELLED;
            case TODAY -> task.getDueAt() != null && task.getDueAt().atZone(APP_ZONE).toLocalDate().equals(today);
            case UPCOMING -> task.getDueAt() != null && task.getDueAt().isAfter(now)
                    && task.getDueAt().isBefore(now.plusSeconds(3 * 24 * 3600));
        };
    }

    private Map<Long, List<Long>> assigneesByTask(Collection<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return assigneeRepository.findAllByIdTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(TaskAssigneeEntity::getTaskId,
                        Collectors.mapping(TaskAssigneeEntity::getUserId, Collectors.toList())));
    }

    private Map<Long, ChecklistSummary> checklistSummaries(Collection<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = taskIds.stream().map(taskId -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT task_id, COUNT(*) AS total, COALESCE(SUM(CASE WHEN completed THEN 1 ELSE 0 END), 0) AS completed " +
                "FROM task_checklists WHERE task_id IN (" + placeholders + ") GROUP BY task_id";
        return jdbcTemplate.query(sql, rs -> {
            Map<Long, ChecklistSummary> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("task_id"), new ChecklistSummary(rs.getInt("completed"), rs.getInt("total")));
            }
            return result;
        }, taskIds.toArray());
    }

    private Map<Long, Integer> fileCounts(Collection<Long> taskIds) {
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = taskIds.stream().map(taskId -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT task_id, COUNT(*) AS total FROM task_files WHERE task_id IN (" + placeholders + ") GROUP BY task_id";
        return jdbcTemplate.query(sql, rs -> {
            Map<Long, Integer> result = new HashMap<>();
            while (rs.next()) {
                result.put(rs.getLong("task_id"), rs.getInt("total"));
            }
            return result;
        }, taskIds.toArray());
    }

    private Map<Long, String> authorNames(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, this::displayName));
    }

    private void requireMembership(Long workspaceId, Long userId) {
        if (!memberRepository.existsByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(workspaceId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WORKSPACE_ACCESS_DENIED", "Ish maydoniga kirishga ruxsat yo'q");
        }
    }

    private boolean isWorkspaceOwner(Long workspaceId, Long userId) {
        return memberRepository.findByWorkspaceIdAndUserIdAndActiveTrue(workspaceId, userId)
                .map(member -> "OWNER".equals(member.getRoleCode()))
                .orElse(false);
    }

    private GroupEntity resolveGroup(Long workspaceId, TaskVisibility visibility, Long groupId, Long actingUserId) {
        if (visibility != TaskVisibility.GROUP) return null;
        if (groupId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_REQUIRED", "Vazifa uchun guruh tanlang");
        }
        GroupEntity group = groupRepository.findById(groupId)
                .filter(item -> item.isActive() && item.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "GROUP_NOT_FOUND",
                        "Tanlangan guruh topilmadi"));
        if ("OWNER_ONLY".equals(group.getTaskCreationPolicy()) && !isWorkspaceOwner(workspaceId, actingUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_CREATION_RESTRICTED",
                    "Bu guruhda faqat ish maydoni egasi vazifa yarata oladi");
        }
        return group;
    }

    private Long resolveTopicId(Long groupId, Long topicId) {
        if (groupId == null || topicId == null) return null;
        if (!groupTopicRepository.existsByIdGroupIdAndIdTopicId(groupId, topicId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOPIC_NOT_FOUND", "Tanlangan mavzu topilmadi");
        }
        return topicId;
    }

    private Set<Long> resolveAssignees(Long workspaceId, TaskVisibility visibility, Long groupId,
                                       Long fallbackUserId, List<Long> requestedAssigneeIds) {
        Set<Long> assigneeIds = new LinkedHashSet<>(requestedAssigneeIds == null
                ? Collections.emptyList() : requestedAssigneeIds);
        if (visibility == TaskVisibility.PERSONAL && assigneeIds.isEmpty()) {
            assigneeIds.add(fallbackUserId);
        }
        if (visibility == TaskVisibility.ONE_TO_ONE && assigneeIds.size() != 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ONE_TO_ONE_ASSIGNEE_REQUIRED",
                    "1:1 vazifa uchun aynan bitta mas'ul tanlang");
        }
        if (visibility == TaskVisibility.GROUP && assigneeIds.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_ASSIGNEE_REQUIRED",
                    "Guruh vazifasi uchun kamida bitta mas'ul tanlang");
        }
        for (Long assigneeId : assigneeIds) {
            requireMembership(workspaceId, assigneeId);
            if (visibility == TaskVisibility.GROUP
                    && !groupMemberRepository.existsById(new GroupMemberId(groupId, assigneeId))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_ASSIGNEE_INVALID",
                        "Mas'ul tanlangan guruh a'zosi emas");
            }
        }
        return assigneeIds;
    }

    private TaskResponse toResponse(TaskEntity task, List<Long> assignees, ChecklistSummary checklist,
                                    int fileCount, String authorName, boolean includeDetails) {
        return toResponse(task, assignees, checklist, fileCount, authorName, includeDetails,
                includeDetails ? reminderMinutes(task.getId()) : null);
    }

    private TaskResponse toResponse(TaskEntity task, List<Long> assignees, ChecklistSummary checklist,
                                    int fileCount, String authorName, boolean includeDetails,
                                    Integer reminderMinutes) {
        return new TaskResponse(task.getId(), task.getWorkspaceId(), task.getGroupId(), task.getTopicId(),
                task.getAuthorId(),
                task.getTitle(), task.getDescription(), task.getStatus(), task.getPriority(), task.getVisibility(),
                task.getDueAt(), task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt(), assignees,
                checklist.formatted(), fileCount, 0, authorName, groupName(task), topicName(task),
                includeDetails ? assigneeDetails(assignees) : List.of(),
                includeDetails ? checklistItems(task.getId()) : List.of(),
                includeDetails ? attachmentDetails(task.getId()) : List.of(), reminderMinutes, task.getDeletedAt(),
                task.getSequenceNumber());
    }

    private List<TaskPersonResponse> assigneeDetails(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        Map<Long, UserEntity> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return userIds.stream().map(users::get).filter(java.util.Objects::nonNull)
                .map(user -> new TaskPersonResponse(user.getId(), displayName(user), user.getUsername(), user.getPhotoUrl()))
                .toList();
    }

    private List<TaskChecklistItemResponse> checklistItems(Long taskId) {
        return jdbcTemplate.query(
                "SELECT id, text, completed FROM task_checklists WHERE task_id = ? ORDER BY position, id",
                (rs, rowNum) -> new TaskChecklistItemResponse(
                        rs.getLong("id"), rs.getString("text"), rs.getBoolean("completed")), taskId);
    }

    private List<TaskAttachmentResponse> attachmentDetails(Long taskId) {
        return fileRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId).stream()
                .map(file -> new TaskAttachmentResponse(file.getId(), file.getOriginalName(), file.getContentType(),
                        file.getSizeBytes(), "/api/tasks/" + taskId + "/files/" + file.getId(),
                        fileAvailable(file)))
                .toList();
    }

    private boolean fileAvailable(TaskFileEntity file) {
        if (file.getContentData() != null && file.getContentData().length > 0) return true;
        Path localFile = uploadDirectory.resolve(file.getStoredName()).normalize();
        return localFile.startsWith(uploadDirectory) && Files.isRegularFile(localFile);
    }

    private String groupName(TaskEntity task) {
        return switch (task.getVisibility()) {
            case PERSONAL -> "Shaxsiy vazifalarim";
            case ONE_TO_ONE -> "1:1 topshiriq";
            case GROUP -> task.getGroupId() == null ? "Guruh" : groupRepository.findById(task.getGroupId())
                    .map(GroupEntity::getName).orElse("Guruh");
            case WORKSPACE -> "Workspace";
        };
    }

    private String topicName(TaskEntity task) {
        if (task.getGroupId() == null || task.getTopicId() == null) return null;
        return groupTopicRepository.findById(new GroupTopicId(task.getGroupId(), task.getTopicId()))
                .map(GroupTopicEntity::getName).orElse(null);
    }

    private long nextTaskSequence(Long workspaceId) {
        Long assigned = jdbcTemplate.queryForObject(
                "UPDATE workspaces SET next_task_sequence = next_task_sequence + 1 WHERE id = ? RETURNING next_task_sequence - 1",
                Long.class, workspaceId);
        return assigned;
    }

    private void persistChecklistItems(Long taskId, Long actorId, List<CreateChecklistItemRequest> checklistItems) {
        if (checklistItems == null || checklistItems.isEmpty()) {
            return;
        }
        IntStream.range(0, checklistItems.size()).forEach(index -> {
            CreateChecklistItemRequest item = checklistItems.get(index);
            jdbcTemplate.update(
                    "INSERT INTO task_checklists(task_id, text, completed, position) VALUES (?, ?, ?, ?)",
                    taskId, item.text().trim(), Boolean.TRUE.equals(item.done()), index);
        });
        insertHistory(taskId, actorId, "CHECKLIST_UPDATED", "{\"items\":" + checklistItems.size() + "}");
    }

    private void replaceChecklistItems(Long taskId, Long actorId,
                                       List<CreateChecklistItemRequest> checklistItems) {
        jdbcTemplate.update("DELETE FROM task_checklists WHERE task_id = ?", taskId);
        persistChecklistItems(taskId, actorId, checklistItems);
        if (checklistItems == null || checklistItems.isEmpty()) {
            insertHistory(taskId, actorId, "CHECKLIST_UPDATED", "{\"items\":0}");
        }
    }

    private void replaceReminder(TaskEntity task, Long actorId, Integer minutesBefore) {
        jdbcTemplate.update("DELETE FROM task_reminders WHERE task_id = ? AND sent_at IS NULL", task.getId());
        if (minutesBefore == null) return;
        if (!Set.of(0, 5, 15, 30, 60, 1440).contains(minutesBefore)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REMINDER_INVALID", "Eslatma vaqti noto'g'ri");
        }
        if (task.getDueAt() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REMINDER_DEADLINE_REQUIRED",
                    "Eslatma uchun avval muddat tanlang");
        }
        Instant remindAt = task.getDueAt().minusSeconds(minutesBefore * 60L);
        if (!remindAt.isAfter(clock.instant())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REMINDER_IN_PAST",
                    "Eslatma vaqti o'tib ketgan. Yaqinroq eslatma tanlang");
        }
        if (task.getVisibility() == TaskVisibility.GROUP) {
            jdbcTemplate.update("INSERT INTO task_reminders(task_id, user_id, remind_at, type) VALUES (?, NULL, ?, ?)",
                    task.getId(), Timestamp.from(remindAt), reminderType(minutesBefore));
        } else {
            List<Long> targets = assigneeRepository.findAllByIdTaskIdIn(List.of(task.getId())).stream()
                    .map(TaskAssigneeEntity::getUserId).distinct().toList();
            if (targets.isEmpty()) targets = List.of(actorId);
            for (Long targetUserId : targets) {
                jdbcTemplate.update("INSERT INTO task_reminders(task_id, user_id, remind_at, type) VALUES (?, ?, ?, ?)",
                        task.getId(), targetUserId, Timestamp.from(remindAt), reminderType(minutesBefore));
            }
        }
        insertHistory(task.getId(), actorId, "REMINDER_UPDATED",
                "{\"minutes_before\":" + minutesBefore + "}");
    }

    private void purgeReminderForRemovedAssignees(Long taskId, Set<Long> keepUserIds) {
        if (keepUserIds.isEmpty()) {
            jdbcTemplate.update(
                    "DELETE FROM task_reminders WHERE task_id = ? AND user_id IS NOT NULL AND sent_at IS NULL", taskId);
            return;
        }
        String placeholders = keepUserIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(taskId);
        args.addAll(keepUserIds);
        jdbcTemplate.update(
                "DELETE FROM task_reminders WHERE task_id = ? AND user_id IS NOT NULL AND sent_at IS NULL "
                        + "AND user_id NOT IN (" + placeholders + ")",
                args.toArray());
    }

    private Integer reminderMinutes(Long taskId) {
        List<Integer> values = jdbcTemplate.query(
                "SELECT type FROM task_reminders WHERE task_id = ? AND sent_at IS NULL ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> reminderMinutes(rs.getString("type")), taskId);
        return values.isEmpty() ? null : values.getFirst();
    }

    private String reminderType(int minutesBefore) {
        return minutesBefore == 0 ? "AT_DUE" : "BEFORE_" + minutesBefore + "_MIN";
    }

    private int reminderMinutes(String type) {
        if ("AT_DUE".equals(type)) return 0;
        try {
            return Integer.parseInt(type.replace("BEFORE_", "").replace("_MIN", ""));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private void persistFiles(Long taskId, Long actorId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return;
        }
        long totalBytes = files.stream().filter(java.util.Objects::nonNull).mapToLong(MultipartFile::getSize).sum();
        if (totalBytes > MAX_TASK_UPLOAD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_FILES_TOO_LARGE",
                    "Bir vazifadagi fayllar jami 20 MB dan oshmasligi kerak");
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            if (file.getSize() > MAX_FILE_SIZE_BYTES) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_FILE_TOO_LARGE",
                        "Har bir fayl 8 MB dan oshmasligi kerak");
            }
            String originalName = sanitizeFileName(file.getOriginalFilename());
            String storedName = taskId + "-" + UUID.randomUUID().toString().replace("-", "");
            String extension = fileExtension(originalName);
            if (!extension.isBlank()) {
                storedName += extension;
            }
            Path target = uploadDirectory.resolve(storedName).normalize();
            if (!target.startsWith(uploadDirectory)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_PATH_INVALID", "Fayl nomi noto'g'ri");
            }
            byte[] content;
            try {
                content = file.getBytes();
            } catch (IOException exception) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_UPLOAD_FAILED", "Fayl saqlanmadi");
            }
            fileRepository.save(new TaskFileEntity(taskId, actorId, originalName, storedName,
                    file.getContentType(), file.getSize(), content));
            try {
                Files.write(target, content);
            } catch (IOException exception) {
                // Asosiy nusxa PostgreSQL'da saqlanadi. Render lokal diski vaqtinchalik va majburiy emas.
                log.warn("Vazifa {} faylining lokal nusxasi yozilmadi: {}", taskId, exception.getMessage());
            }
        }
        insertHistory(taskId, actorId, "FILES_UPLOADED", "{\"count\":" + files.size() + "}");
    }

    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "file";
        }
        return Paths.get(originalName).getFileName().toString();
    }

    private String fileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex >= 0 ? fileName.substring(dotIndex) : "";
    }

    private String displayName(Long userId) {
        return userRepository.findById(userId)
                .map(this::displayName)
                .orElse("Siz");
    }

    private String displayName(UserEntity user) {
        return user.getLastName() == null || user.getLastName().isBlank()
                ? user.getFirstName()
                : user.getFirstName() + " " + user.getLastName();
    }

    private List<TelegramTaskNotificationService.Assignee> assigneeMentions(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) return List.of();
        Map<Long, UserEntity> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return userIds.stream().map(users::get).filter(java.util.Objects::nonNull)
                .map(user -> new TelegramTaskNotificationService.Assignee(displayName(user), user.getTelegramId()))
                .toList();
    }

    private void publishAfterCommit(TelegramTaskNotificationService.TaskTelegramMessage notification) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskNotificationService.publishAsync(notification);
                }
            });
        } else {
            taskNotificationService.publishAsync(notification);
        }
    }

    private void deleteDraftPreviewMessage(long draftId) {
        VoiceDraftService.PreviewMessageLocation location = voiceDraftService.discardAndGetPreviewMessage(draftId);
        if (location != null) {
            taskNotificationService.deleteMessageAsync(location.chatId(), location.messageId());
        }
    }

    private void syncTelegramAfterCommit(TaskEntity task, String activity, boolean replaceMedia) {
        if (task.getVisibility() != TaskVisibility.GROUP || task.getGroupId() == null) return;
        groupRepository.findById(task.getGroupId()).filter(GroupEntity::isActive).ifPresent(group -> {
            List<Long> assigneeIds = assigneeRepository.findAllByIdTaskIdIn(List.of(task.getId())).stream()
                    .map(TaskAssigneeEntity::getUserId).toList();
            TelegramTaskNotificationService.TaskTelegramMessage notification =
                    new TelegramTaskNotificationService.TaskTelegramMessage(
                            task.getId(), group.getTelegramChatId(), task.getTopicId(), task.getTelegramMessageId(),
                            task.getTitle(), task.getDescription(), displayName(task.getAuthorId()),
                            assigneeMentions(assigneeIds), task.getDueAt(), task.getStatus(), activity,
                            telegramAttachment(task.getId()), replaceMedia);
            runAfterCommit(() -> taskNotificationService.syncAsync(notification));
        });
    }

    private void validateTelegramAction(TaskStatus status, TelegramTaskAction action,
                                        boolean author, boolean assigned, boolean hasAnyAssignee) {
        boolean allowed = switch (action) {
            case START -> status == TaskStatus.NEW && (author || assigned || !hasAnyAssignee);
            case COMPLETE -> status == TaskStatus.NEW && (author || assigned);
            case PROBLEM, REVIEW -> status == TaskStatus.IN_PROGRESS && (author || assigned);
            case RESUME -> status == TaskStatus.BLOCKED && (author || assigned);
            case APPROVE, RETURN -> status == TaskStatus.REVIEW && author;
            case REOPEN -> status == TaskStatus.COMPLETED && author;
        };
        if (!allowed) {
            String message = switch (action) {
                case APPROVE, RETURN, REOPEN -> "Bu amalni faqat vazifa yaratuvchisi bajarishi mumkin";
                case START -> "Bu vazifa boshqa xodimga biriktirilgan";
                case PROBLEM, REVIEW -> "Avval \"Boshladim\" tugmasini bosing";
                default -> "Bu amal vazifaning hozirgi statusiga mos emas";
            };
            throw new ApiException(HttpStatus.FORBIDDEN, "TASK_ACTION_FORBIDDEN", message);
        }
    }

    private TelegramTaskNotificationService.TaskTelegramAttachment telegramAttachment(Long taskId) {
        List<TaskFileEntity> files = fileRepository.findAllByTaskIdOrderByCreatedAtAsc(taskId);
        for (boolean photosOnly : List.of(true, false)) {
            for (TaskFileEntity file : files) {
                boolean photo = isTelegramPhoto(file);
                if (photosOnly != photo) continue;
                byte[] bytes = file.getContentData();
                if (bytes == null || bytes.length == 0) {
                    Path localFile = uploadDirectory.resolve(file.getStoredName()).normalize();
                    if (!localFile.startsWith(uploadDirectory) || !Files.isRegularFile(localFile)) continue;
                    try {
                        bytes = Files.readAllBytes(localFile);
                    } catch (IOException ignored) {
                        continue;
                    }
                }
                String contentType = file.getContentType() == null || file.getContentType().isBlank()
                        ? "application/octet-stream" : file.getContentType();
                return new TelegramTaskNotificationService.TaskTelegramAttachment(
                        file.getOriginalName(), contentType, bytes, photo);
            }
        }
        return null;
    }

    private boolean isTelegramPhoto(TaskFileEntity file) {
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String name = file.getOriginalName().toLowerCase();
        return type.equals("image/jpeg") || type.equals("image/png")
                || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
    }

    private String statusActivity(String actor, TaskStatus previous, TaskStatus next) {
        if (previous == next) return null;
        return switch (next) {
            case IN_PROGRESS -> previous == TaskStatus.COMPLETED
                    ? "🔄 " + actor + " vazifani qayta ochdi."
                    : previous == TaskStatus.REVIEW
                    ? "↩️ " + actor + " vazifani qayta ishlashga qaytardi."
                    : "▶️ " + actor + " vazifani davom ettirdi.";
            case BLOCKED -> "⚠️ " + actor + " vazifada muammo borligini bildirdi.";
            case REVIEW -> "🔎 " + actor + " vazifani tekshiruvga yubordi.";
            case COMPLETED -> previous == TaskStatus.REVIEW
                    ? "✅ " + actor + " vazifani tasdiqladi."
                    : "✅ " + actor + " vazifani bajardi.";
            case NEW -> "🆕 " + actor + " vazifani yangi holatga qaytardi.";
            case CANCELLED -> "🚫 " + actor + " vazifani bekor qildi.";
        };
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void insertHistory(Long taskId, Long actorId, String eventType, String newValue) {
        jdbcTemplate.update("INSERT INTO task_history(task_id, actor_id, event_type, new_value) " +
                        "VALUES (?, ?, ?, CAST(? AS jsonb))", taskId, actorId, eventType, newValue);
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                case '\b' -> result.append("\\b");
                case '\f' -> result.append("\\f");
                default -> {
                    if (c < 0x20) {
                        result.append(String.format("\\u%04x", (int) c));
                    } else {
                        result.append(c);
                    }
                }
            }
        }
        return result.toString();
    }

    private ChecklistSummary checklistSummaryFromRequest(List<CreateChecklistItemRequest> checklistItems) {
        if (checklistItems == null || checklistItems.isEmpty()) {
            return ChecklistSummary.EMPTY;
        }
        long completed = checklistItems.stream().filter(item -> Boolean.TRUE.equals(item.done())).count();
        return new ChecklistSummary((int) completed, checklistItems.size());
    }

    public enum TaskScope { ALL, MINE, ACTIVE, COMPLETED, OVERDUE, TODAY, UPCOMING }

    public record TaskResponse(Long id, Long workspaceId, Long groupId, Long topicId, Long authorId, String title,
                               String description, TaskStatus status, TaskPriority priority,
                               TaskVisibility visibility, Instant dueAt, Instant completedAt,
                               Instant createdAt, Instant updatedAt, List<Long> assigneeIds,
                               String checklist, Integer files, Integer comments,
                               String author, String groupName, String topicName, List<TaskPersonResponse> assignees,
                               List<TaskChecklistItemResponse> checklistItems,
                               List<TaskAttachmentResponse> attachments, Integer reminderMinutes,
                               Instant archivedAt, Long sequenceNumber) {
        public String code() {
            return "TASK-" + String.format("%04d", sequenceNumber);
        }
    }

    public record TaskPersonResponse(Long id, String name, String username, String photoUrl) {}

    public record TaskChecklistItemResponse(Long id, String text, boolean done) {}

    public record TaskAttachmentResponse(Long id, String name, String contentType, long size, String url,
                                         boolean available) {}

    public record TaskFileContent(String name, String contentType, byte[] bytes) {}

    public enum TelegramTaskAction { START, COMPLETE, PROBLEM, REVIEW, RESUME, APPROVE, RETURN, REOPEN }

    public record TelegramActionResult(Long userId, String displayName, TelegramTaskAction action,
                                       TaskStatus status, boolean newlyAssigned, boolean statusChanged) {}

    private record ChecklistSummary(int completed, int total) {
        private static final ChecklistSummary EMPTY = new ChecklistSummary(0, 0);

        private String formatted() {
            return completed + "/" + total;
        }
    }
}

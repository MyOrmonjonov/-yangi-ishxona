package uz.taskapp.group;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.TelegramProperties;
import uz.taskapp.user.UserEntity;
import uz.taskapp.user.UserRepository;
import uz.taskapp.workspace.WorkspaceMemberRepository;
import uz.taskapp.workspace.WorkspaceMemberEntity;
import uz.taskapp.telegram.TelegramTaskNotificationService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class GroupService {
    private static final Logger log = LoggerFactory.getLogger(GroupService.class);
    private static final Duration REQUEST_TTL = Duration.ofMinutes(10);

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final TelegramGroupRequestRepository requestRepository;
    private final TelegramKnownChatRepository knownChatRepository;
    private final GroupTopicRepository groupTopicRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final UserRepository userRepository;
    private final TelegramProperties telegramProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TelegramTaskNotificationService taskNotificationService;
    private final SecureRandom random = new SecureRandom();

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        TelegramGroupRequestRepository requestRepository,
                        TelegramKnownChatRepository knownChatRepository,
                        GroupTopicRepository groupTopicRepository,
                        WorkspaceMemberRepository workspaceMemberRepository,
                        UserRepository userRepository,
                        TelegramProperties telegramProperties,
                        ObjectMapper objectMapper,
                        TelegramTaskNotificationService taskNotificationService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.requestRepository = requestRepository;
        this.knownChatRepository = knownChatRepository;
        this.groupTopicRepository = groupTopicRepository;
        this.workspaceMemberRepository = workspaceMemberRepository;
        this.userRepository = userRepository;
        this.telegramProperties = telegramProperties;
        this.objectMapper = objectMapper;
        this.taskNotificationService = taskNotificationService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list(Long userId, Long workspaceId) {
        requireWorkspaceAccess(userId, workspaceId);
        return groupRepository.findAllByWorkspaceIdAndActiveTrueOrderByNameAsc(workspaceId).stream()
                .map(group -> new GroupResponse(
                        group.getId(), group.getName(), groupMemberRepository.countByGroupId(group.getId()),
                        groupMembers(group.getId()), taskNotificationService.isBotMember(group.getTelegramChatId()),
                        taskNotificationService.botUsername(), group.getTaskCreationPolicy()))
                .toList();
    }

    private List<GroupMemberResponse> groupMembers(Long groupId) {
        List<GroupMemberEntity> memberships = groupMemberRepository.findAllByGroupIdOrderByJoinedAtAsc(groupId);
        Map<Long, UserEntity> users = userRepository.findAllById(
                        memberships.stream().map(GroupMemberEntity::getUserId).toList()).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return memberships.stream()
                .map(membership -> users.get(membership.getUserId()))
                .filter(java.util.Objects::nonNull)
                .map(user -> new GroupMemberResponse(user.getId(), displayName(user), user.getUsername(),
                        user.getPhotoUrl()))
                .toList();
    }

    private String displayName(UserEntity user) {
        String fullName = String.join(" ",
                user.getFirstName() == null ? "" : user.getFirstName(),
                user.getLastName() == null ? "" : user.getLastName()).trim();
        if (!fullName.isBlank()) return fullName;
        if (user.getUsername() != null && !user.getUsername().isBlank()) return "@" + user.getUsername();
        return "Foydalanuvchi";
    }

    @Transactional(readOnly = true)
    public List<AvailableGroupResponse> listAvailableTelegramGroups(Long userId, Long workspaceId) {
        requireWorkspaceAccess(userId, workspaceId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND",
                        "Foydalanuvchi topilmadi"));
        return knownChatRepository.findAllByActiveTrue().stream()
                .filter(chat -> groupRepository.findByTelegramChatId(chat.getChatId())
                        .filter(GroupEntity::isActive).isEmpty())
                .filter(chat -> isUserMemberOfChat(chat.getChatId(), user.getTelegramId()))
                .map(chat -> new AvailableGroupResponse(chat.getChatId(), chat.getTitle()))
                .toList();
    }

    @Transactional
    public GroupResponse linkKnownGroup(Long userId, Long workspaceId, Long telegramChatId) {
        requireWorkspaceAccess(userId, workspaceId);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND",
                        "Foydalanuvchi topilmadi"));
        TelegramKnownChatEntity known = knownChatRepository.findById(telegramChatId)
                .filter(TelegramKnownChatEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TELEGRAM_GROUP_NOT_FOUND",
                        "Guruh topilmadi"));
        if (groupRepository.findByTelegramChatId(telegramChatId).filter(GroupEntity::isActive).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "TELEGRAM_GROUP_ALREADY_LINKED",
                    "Guruh allaqachon ulangan");
        }
        if (!isUserMemberOfChat(telegramChatId, user.getTelegramId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "TELEGRAM_GROUP_ACCESS_DENIED",
                    "Siz bu guruh a'zosi emassiz");
        }
        GroupEntity group = groupRepository.save(new GroupEntity(workspaceId, known.getTitle(), telegramChatId));
        groupMemberRepository.save(new GroupMemberEntity(group.getId(), userId));
        return new GroupResponse(group.getId(), group.getName(), 1, groupMembers(group.getId()),
                taskNotificationService.isBotMember(group.getTelegramChatId()), taskNotificationService.botUsername(),
                group.getTaskCreationPolicy());
    }

    @Transactional
    public GroupResponse updateTaskRules(Long userId, Long groupId, String taskCreationPolicy) {
        if (!Set.of("EVERYONE", "OWNER_ONLY").contains(taskCreationPolicy)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TASK_CREATION_POLICY_INVALID",
                    "Vazifa yaratish qoidasi noto'g'ri");
        }
        GroupEntity group = groupRepository.findById(groupId)
                .filter(GroupEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Guruh topilmadi"));
        WorkspaceMemberEntity membership = workspaceMemberRepository
                .findByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(group.getWorkspaceId(), userId)
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "WORKSPACE_ACCESS_DENIED",
                        "Ish maydoniga kirishga ruxsat yo'q"));
        if (!"OWNER".equals(membership.getRoleCode())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GROUP_RULES_CHANGE_FORBIDDEN",
                    "Faqat ish maydoni egasi vazifa qoidalarini o'zgartira oladi");
        }
        group.updateTaskCreationPolicy(taskCreationPolicy);
        return new GroupResponse(group.getId(), group.getName(), groupMemberRepository.countByGroupId(group.getId()),
                groupMembers(group.getId()), taskNotificationService.isBotMember(group.getTelegramChatId()),
                taskNotificationService.botUsername(), group.getTaskCreationPolicy());
    }

    @Transactional(readOnly = true)
    public void inviteMembers(Long userId, Long groupId) {
        GroupEntity group = groupRepository.findById(groupId)
                .filter(GroupEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Guruh topilmadi"));
        requireWorkspaceAccess(userId, group.getWorkspaceId());
        if (!taskNotificationService.sendMembershipInvite(group.getId(), group.getTelegramChatId())) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TELEGRAM_GROUP_MESSAGE_FAILED",
                    "Bot guruhga xabar yubora olmadi. Bot guruhda ekanini tekshiring");
        }
    }

    @Transactional(readOnly = true)
    public List<GroupTopicResponse> listTopics(Long userId, Long groupId) {
        GroupEntity group = groupRepository.findById(groupId)
                .filter(GroupEntity::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Guruh topilmadi"));
        requireWorkspaceAccess(userId, group.getWorkspaceId());
        return groupTopicRepository.findAllByIdGroupIdAndClosedFalseOrderByNameAsc(groupId).stream()
                .map(topic -> new GroupTopicResponse(topic.getId().topicId(), topic.getName()))
                .toList();
    }

    @Transactional
    public void upsertTopic(Long chatId, Long topicId, String name) {
        if (topicId == null) return;
        GroupEntity group = groupRepository.findByTelegramChatId(chatId).filter(GroupEntity::isActive).orElse(null);
        if (group == null) return;
        GroupTopicId id = new GroupTopicId(group.getId(), topicId);
        String fallbackName = "Mavzu #" + topicId;
        GroupTopicEntity topic = groupTopicRepository.findById(id)
                .orElseGet(() -> new GroupTopicEntity(group.getId(), topicId,
                        name != null && !name.isBlank() ? name : fallbackName));
        topic.refresh(name, false);
        groupTopicRepository.save(topic);
    }

    @Transactional
    public void setTopicClosed(Long chatId, Long topicId, boolean closed) {
        if (topicId == null) return;
        GroupEntity group = groupRepository.findByTelegramChatId(chatId).filter(GroupEntity::isActive).orElse(null);
        if (group == null) return;
        GroupTopicId id = new GroupTopicId(group.getId(), topicId);
        groupTopicRepository.findById(id).ifPresent(topic -> {
            topic.refresh(null, closed);
            groupTopicRepository.save(topic);
        });
    }

    @Transactional
    public GroupJoinResult joinFromTelegram(Long groupId, Long telegramChatId, Long telegramUserId,
                                            String firstName, String lastName, String username,
                                            String languageCode) {
        GroupEntity group = groupRepository.findById(groupId)
                .filter(item -> item.isActive() && item.getTelegramChatId().equals(telegramChatId))
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "TELEGRAM_GROUP_MISMATCH",
                        "Guruh mos kelmadi"));
        return addMember(group, telegramUserId, firstName, lastName, username, languageCode);
    }

    @Transactional
    public void syncMemberFromMessage(Long telegramChatId, Long telegramUserId, String firstName, String lastName,
                                      String username, String languageCode) {
        if (telegramUserId == null) return;
        GroupEntity group = groupRepository.findByTelegramChatId(telegramChatId).filter(GroupEntity::isActive)
                .orElse(null);
        if (group == null) return;
        UserEntity existing = userRepository.findByTelegramId(telegramUserId).orElse(null);
        if (existing != null && groupMemberRepository.existsById(new GroupMemberId(group.getId(), existing.getId()))) {
            return;
        }
        addMember(group, telegramUserId, firstName, lastName, username, languageCode);
    }

    private GroupJoinResult addMember(GroupEntity group, Long telegramUserId, String firstName, String lastName,
                                      String username, String languageCode) {
        String safeFirstName = firstName == null || firstName.isBlank() ? "Foydalanuvchi" : firstName;
        UserEntity user = userRepository.findByTelegramId(telegramUserId)
                .orElseGet(() -> new UserEntity(telegramUserId, safeFirstName));
        user.updateTelegramProfile(safeFirstName, lastName, username, user.getPhotoUrl(), languageCode);
        user.markBotConnected();
        user = userRepository.save(user);
        Long taskAppUserId = user.getId();

        WorkspaceMemberEntity workspaceMember = workspaceMemberRepository
                .findByWorkspaceIdAndUserId(group.getWorkspaceId(), taskAppUserId)
                .orElseGet(() -> new WorkspaceMemberEntity(group.getWorkspaceId(), taskAppUserId, "MEMBER"));
        if (!workspaceMember.isActive()) workspaceMember.activate();
        workspaceMemberRepository.save(workspaceMember);

        GroupMemberId memberId = new GroupMemberId(group.getId(), taskAppUserId);
        boolean added = !groupMemberRepository.existsById(memberId);
        if (added) groupMemberRepository.save(new GroupMemberEntity(group.getId(), taskAppUserId));
        return new GroupJoinResult(taskAppUserId, displayName(user), added);
    }

    @Transactional
    public PreparedGroupButton prepare(Long userId, Long workspaceId) {
        requireWorkspaceAccess(userId, workspaceId);
        if (telegramProperties.botToken() == null || telegramProperties.botToken().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TELEGRAM_BOT_NOT_CONFIGURED",
                    "Telegram bot hali sozlanmagan");
        }
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND",
                        "Foydalanuvchi topilmadi"));
        int requestId = nextRequestId();
        requestRepository.save(new TelegramGroupRequestEntity(
                requestId, userId, workspaceId, Instant.now().plus(REQUEST_TTL)));

        Map<String, Object> button = new LinkedHashMap<>();
        button.put("text", "Guruhni tanlash");
        button.put("request_chat", requestChatPayload(requestId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("user_id", user.getTelegramId());
        payload.put("button", button);

        String preparedButtonId = savePreparedKeyboardButton(payload);
        return new PreparedGroupButton(preparedButtonId, requestId);
    }

    @Transactional
    public boolean acceptTelegramSelection(Long telegramUserId, Integer requestId, Long telegramChatId, String title) {
        TelegramGroupRequestEntity pending = requestRepository.findById(requestId).orElse(null);
        if (pending == null || pending.getExpiresAt().isBefore(Instant.now())) {
            if (pending != null) requestRepository.delete(pending);
            log.warn("Muddati o'tgan yoki noma'lum Telegram guruh so'rovi: {}", requestId);
            return false;
        }
        UserEntity user = userRepository.findByTelegramId(telegramUserId).orElse(null);
        if (user == null || !user.getId().equals(pending.getUserId())) {
            log.warn("Telegram guruh so'rovi foydalanuvchisi mos emas: {}", requestId);
            return false;
        }

        GroupEntity group = groupRepository.findByTelegramChatId(telegramChatId).orElse(null);
        if (group != null && !group.getWorkspaceId().equals(pending.getWorkspaceId())) {
            log.warn("Telegram guruhi boshqa ish maydoniga ulangan: {}", telegramChatId);
            requestRepository.delete(pending);
            return false;
        }
        String groupName = title == null || title.isBlank() ? "Telegram guruhi" : title.trim();
        if (group == null) {
            group = groupRepository.save(new GroupEntity(pending.getWorkspaceId(), groupName, telegramChatId));
        } else {
            group.refreshTelegramData(groupName);
            group = groupRepository.save(group);
        }
        GroupMemberId memberId = new GroupMemberId(group.getId(), user.getId());
        if (!groupMemberRepository.existsById(memberId)) {
            groupMemberRepository.save(new GroupMemberEntity(group.getId(), user.getId()));
        }
        requestRepository.delete(pending);
        return true;
    }

    @Transactional(readOnly = true)
    public void sendFallback(Long userId, Integer requestId) {
        TelegramGroupRequestEntity pending = requestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "GROUP_REQUEST_NOT_FOUND",
                        "Guruh tanlash so'rovi topilmadi"));
        if (!pending.getUserId().equals(userId) || pending.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "GROUP_REQUEST_EXPIRED",
                    "Guruh tanlash so'rovi eskirgan");
        }
        requireWorkspaceAccess(userId, pending.getWorkspaceId());
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND",
                        "Foydalanuvchi topilmadi"));

        Map<String, Object> button = new LinkedHashMap<>();
        button.put("text", "Guruhni tanlash");
        button.put("request_chat", requestChatPayload(requestId));

        Map<String, Object> replyMarkup = new LinkedHashMap<>();
        replyMarkup.put("keyboard", List.of(List.of(button)));
        replyMarkup.put("resize_keyboard", true);
        replyMarkup.put("one_time_keyboard", true);
        replyMarkup.put("input_field_placeholder", "Mavjud guruhingizni tanlang");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chat_id", user.getTelegramId());
        payload.put("text", "Mavjud Telegram guruhlaringiz ro'yxatini ochish uchun pastdagi tugmani bosing.");
        payload.put("reply_markup", replyMarkup);
        callTelegram("sendMessage", payload);
    }

    private void requireWorkspaceAccess(Long userId, Long workspaceId) {
        if (!workspaceMemberRepository.existsByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(workspaceId, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "WORKSPACE_ACCESS_DENIED",
                    "Ish maydoniga kirishga ruxsat yo'q");
        }
    }

    private int nextRequestId() {
        for (int attempt = 0; attempt < 10; attempt++) {
            int candidate = random.nextInt(1, Integer.MAX_VALUE);
            if (!requestRepository.existsById(candidate)) return candidate;
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GROUP_REQUEST_UNAVAILABLE",
                "Guruh tanlash so'rovini yaratib bo'lmadi");
    }

    private String savePreparedKeyboardButton(Map<String, Object> payload) {
        JsonNode body = callTelegram("savePreparedKeyboardButton", payload);
        JsonNode preparedId = body.path("result").path("id");
        if (!preparedId.isMissingNode() && !preparedId.asText().isBlank()) {
            return preparedId.asText();
        }
        log.warn("Telegram prepared button javobida id yo'q");
        throw telegramUnavailable();
    }

    private Map<String, Object> requestChatPayload(Integer requestId) {
        Map<String, Object> requestChat = new LinkedHashMap<>();
        requestChat.put("request_id", requestId);
        requestChat.put("chat_is_channel", false);
        requestChat.put("request_title", true);
        requestChat.put("request_username", true);
        requestChat.put("request_photo", true);
        return requestChat;
    }

    private boolean isUserMemberOfChat(Long chatId, Long telegramUserId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("chat_id", chatId);
            payload.put("user_id", telegramUserId);
            JsonNode body = callTelegram("getChatMember", payload);
            String status = body.path("result").path("status").asText("");
            return !status.isBlank() && !status.equals("left") && !status.equals("kicked");
        } catch (ApiException exception) {
            return false;
        }
    }

    private JsonNode callTelegram(String method, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.telegram.org/bot" + telegramProperties.botToken()
                            + "/" + method))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = objectMapper.readTree(response.body());
            if (response.statusCode() >= 200 && response.statusCode() < 300
                    && body.path("ok").asBoolean()) {
                return body;
            }
            log.warn("Telegram {} so'rovi bajarilmadi: {}", method,
                    body.path("description").asText("unknown"));
            throw telegramUnavailable();
        } catch (IOException exception) {
            log.warn("Telegram API javobini o'qib bo'lmadi", exception);
            throw telegramUnavailable();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw telegramUnavailable();
        }
    }

    private ApiException telegramUnavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "TELEGRAM_GROUP_PICKER_UNAVAILABLE",
                "Telegram guruhlar oynasini ochib bo'lmadi");
    }

    public record GroupResponse(Long id, String title, long members, List<GroupMemberResponse> memberList,
                                boolean botConnected, String botUsername, String taskCreationPolicy) {
    }

    public record AvailableGroupResponse(Long chatId, String title) {
    }

    public record GroupTopicResponse(Long id, String name) {
    }

    public record GroupMemberResponse(Long id, String name, String username, String photoUrl) {
    }

    public record PreparedGroupButton(String preparedButtonId, Integer requestId) {
    }

    public record GroupJoinResult(Long userId, String displayName, boolean added) {
    }
}

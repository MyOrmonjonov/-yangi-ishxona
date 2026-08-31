package uz.taskapp.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.taskapp.common.ApiException;
import uz.taskapp.config.AppProperties;
import uz.taskapp.user.UserEntity;
import uz.taskapp.user.UserRepository;
import uz.taskapp.workspace.WorkspaceEntity;
import uz.taskapp.workspace.WorkspaceMemberEntity;
import uz.taskapp.workspace.WorkspaceMemberRepository;
import uz.taskapp.workspace.WorkspaceRepository;

import java.util.List;
import java.util.Map;

@Service
public class AuthService {
    private final TelegramInitDataVerifier verifier;
    private final TelegramLoginVerifier loginVerifier;
    private final AccessTokenService tokenService;
    private final UserRepository userRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final AppProperties appProperties;

    public AuthService(TelegramInitDataVerifier verifier,
                       TelegramLoginVerifier loginVerifier,
                       AccessTokenService tokenService,
                       UserRepository userRepository,
                       WorkspaceRepository workspaceRepository,
                       WorkspaceMemberRepository memberRepository,
                       AppProperties appProperties) {
        this.verifier = verifier;
        this.loginVerifier = loginVerifier;
        this.tokenService = tokenService;
        this.userRepository = userRepository;
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.appProperties = appProperties;
    }

    @Transactional
    public AuthResponse authenticate(String initData) {
        VerifiedTelegramData verified = verifier.verify(initData);
        return authenticateVerified(verified.user());
    }

    /** Browser login via the Telegram Login Widget (outside the Telegram Mini App). */
    @Transactional
    public AuthResponse authenticateViaLoginWidget(Map<String, String> fields) {
        TelegramUserData telegram = loginVerifier.verify(fields);
        return authenticateVerified(telegram);
    }

    private AuthResponse authenticateVerified(TelegramUserData telegram) {
        UserEntity user = userRepository.findByTelegramId(telegram.id())
                .orElseGet(() -> new UserEntity(telegram.id(), telegram.firstName()));
        user.updateTelegramProfile(telegram.firstName(), telegram.lastName(), telegram.username(),
                telegram.photoUrl(), telegram.languageCode());
        user = userRepository.save(user);
        if (user.isBlocked()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_BLOCKED", "Foydalanuvchi bloklangan");
        }

        List<WorkspaceMemberEntity> memberships = memberRepository.findAllByUserIdAndActiveTrue(user.getId())
                .stream().filter(member -> !member.isTemporarilyBlocked()).toList();
        if (memberships.isEmpty() && telegram.id() == appProperties.bootstrapOwnerTelegramId()) {
            WorkspaceEntity workspace = workspaceRepository.save(
                    new WorkspaceEntity("Mening ish maydonim", preferredLanguage(telegram.languageCode()), user.getId()));
            memberships = List.of(memberRepository.save(
                    new WorkspaceMemberEntity(workspace.getId(), user.getId(), "OWNER")));
        }
        if (memberships.isEmpty()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "INVITATION_REQUIRED",
                    "Ilovadan foydalanish uchun ish maydoniga taklif kerak");
        }

        List<AuthWorkspace> workspaces = memberships.stream()
                .map(member -> workspaceRepository.findById(member.getWorkspaceId())
                        .map(workspace -> new AuthWorkspace(workspace.getId(), workspace.getName(), member.getRoleCode()))
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
        return new AuthResponse(tokenService.issue(user.getId()), new AuthUser(
                user.getId(), user.getTelegramId(), user.getFirstName(), user.getLastName(),
                user.getUsername(), user.getPhotoUrl(), user.getUiLanguage(), user.getTheme(),
                user.isRemindersEnabled()), workspaces);
    }

    private String preferredLanguage(String languageCode) {
        return switch (languageCode == null ? "uz" : languageCode.toLowerCase()) {
            case "ru" -> "ru";
            case "en" -> "en";
            default -> "uz";
        };
    }

    public record AuthResponse(String accessToken, AuthUser user, List<AuthWorkspace> workspaces) {}
    public record AuthUser(Long id, Long telegramId, String firstName, String lastName,
                           String username, String photoUrl, String uiLanguage, String theme,
                           boolean remindersEnabled) {}
    public record AuthWorkspace(Long id, String name, String role) {}
}

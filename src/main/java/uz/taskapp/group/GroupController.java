package uz.taskapp.group;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import uz.taskapp.auth.AuthInterceptor;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {
    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping
    List<GroupService.GroupResponse> list(HttpServletRequest request, @RequestParam Long workspaceId) {
        return groupService.list(userId(request), workspaceId);
    }

    @PostMapping("/telegram/prepare")
    GroupService.PreparedGroupButton prepare(HttpServletRequest request, @RequestParam Long workspaceId) {
        return groupService.prepare(userId(request), workspaceId);
    }

    @GetMapping("/telegram/available")
    List<GroupService.AvailableGroupResponse> available(HttpServletRequest request, @RequestParam Long workspaceId) {
        return groupService.listAvailableTelegramGroups(userId(request), workspaceId);
    }

    @PostMapping("/telegram/link")
    GroupService.GroupResponse link(HttpServletRequest request, @RequestParam Long workspaceId,
                                    @RequestParam Long chatId) {
        return groupService.linkKnownGroup(userId(request), workspaceId, chatId);
    }

    @PostMapping("/telegram/fallback")
    ResponseEntity<Void> fallback(HttpServletRequest request, @RequestParam Integer requestId) {
        groupService.sendFallback(userId(request), requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{groupId}/topics")
    List<GroupService.GroupTopicResponse> topics(HttpServletRequest request, @PathVariable Long groupId) {
        return groupService.listTopics(userId(request), groupId);
    }

    @PostMapping("/{groupId}/invite-members")
    ResponseEntity<Void> inviteMembers(HttpServletRequest request, @PathVariable Long groupId) {
        groupService.inviteMembers(userId(request), groupId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{groupId}/rules")
    GroupService.GroupResponse updateRules(HttpServletRequest request, @PathVariable Long groupId,
                                           @RequestBody UpdateGroupRulesRequest body) {
        return groupService.updateTaskRules(userId(request), groupId, body.taskCreationPolicy());
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }

    public record UpdateGroupRulesRequest(String taskCreationPolicy) {
    }
}

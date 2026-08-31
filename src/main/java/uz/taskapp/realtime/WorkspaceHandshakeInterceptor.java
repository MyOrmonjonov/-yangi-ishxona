package uz.taskapp.realtime;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import uz.taskapp.auth.AccessTokenService;
import uz.taskapp.workspace.WorkspaceMemberRepository;

import java.util.Map;

@Component
public class WorkspaceHandshakeInterceptor implements HandshakeInterceptor {
    static final String WORKSPACE_ID_ATTRIBUTE = "workspaceId";
    static final String USER_ID_ATTRIBUTE = "userId";

    private final AccessTokenService accessTokenService;
    private final WorkspaceMemberRepository memberRepository;

    public WorkspaceHandshakeInterceptor(AccessTokenService accessTokenService, WorkspaceMemberRepository memberRepository) {
        this.accessTokenService = accessTokenService;
        this.memberRepository = memberRepository;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) return false;
        HttpServletRequest httpRequest = servletRequest.getServletRequest();
        String token = httpRequest.getParameter("token");
        String workspaceParam = httpRequest.getParameter("workspaceId");
        if (token == null || token.isBlank() || workspaceParam == null) return false;

        Long userId;
        Long workspaceId;
        try {
            userId = accessTokenService.verify(token);
            workspaceId = Long.parseLong(workspaceParam);
        } catch (RuntimeException exception) {
            return false;
        }
        if (!memberRepository.existsByWorkspaceIdAndUserIdAndActiveTrueAndTemporarilyBlockedFalse(workspaceId, userId)) {
            return false;
        }
        attributes.put(WORKSPACE_ID_ATTRIBUTE, workspaceId);
        attributes.put(USER_ID_ATTRIBUTE, userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
    }
}

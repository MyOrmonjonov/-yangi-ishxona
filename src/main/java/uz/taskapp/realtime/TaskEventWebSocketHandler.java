package uz.taskapp.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class TaskEventWebSocketHandler extends TextWebSocketHandler {
    private final WorkspaceBroadcastService broadcastService;

    public TaskEventWebSocketHandler(WorkspaceBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long workspaceId = workspaceId(session);
        if (workspaceId == null) {
            closeQuietly(session);
            return;
        }
        broadcastService.register(workspaceId, session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long workspaceId = workspaceId(session);
        if (workspaceId != null) broadcastService.unregister(workspaceId, session);
    }

    private Long workspaceId(WebSocketSession session) {
        return (Long) session.getAttributes().get(WorkspaceHandshakeInterceptor.WORKSPACE_ID_ATTRIBUTE);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
        }
    }
}

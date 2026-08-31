package uz.taskapp.realtime;

import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
public class WorkspaceBroadcastService {
    private static final Logger log = LoggerFactory.getLogger(WorkspaceBroadcastService.class);

    private final Map<Long, Set<WebSocketSession>> sessionsByWorkspace = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public WorkspaceBroadcastService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void register(Long workspaceId, WebSocketSession session) {
        sessionsByWorkspace.computeIfAbsent(workspaceId, id -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(Long workspaceId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByWorkspace.get(workspaceId);
        if (sessions == null) return;
        sessions.remove(session);
        sessionsByWorkspace.computeIfPresent(workspaceId, (id, current) -> current.isEmpty() ? null : current);
    }

    public void notifyTaskChanged(Long workspaceId, Long taskId, Long actorUserId) {
        broadcast(workspaceId, Map.of("type", "TASK_CHANGED", "taskId", taskId, "actorUserId", actorUserId));
    }

    private void broadcast(Long workspaceId, Object payload) {
        Set<WebSocketSession> sessions = sessionsByWorkspace.get(workspaceId);
        if (sessions == null || sessions.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (RuntimeException exception) {
            log.warn("Broadcast payload serialize bo'lmadi", exception);
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) session.sendMessage(message);
            } catch (IOException exception) {
                log.debug("WebSocket xabar yuborilmadi", exception);
            }
        }
    }
}

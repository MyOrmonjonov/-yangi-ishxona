package uz.taskapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import uz.taskapp.realtime.TaskEventWebSocketHandler;
import uz.taskapp.realtime.WorkspaceHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final TaskEventWebSocketHandler taskEventWebSocketHandler;
    private final WorkspaceHandshakeInterceptor handshakeInterceptor;

    public WebSocketConfig(TaskEventWebSocketHandler taskEventWebSocketHandler,
                            WorkspaceHandshakeInterceptor handshakeInterceptor) {
        this.taskEventWebSocketHandler = taskEventWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(taskEventWebSocketHandler, "/ws/tasks")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("https://*.telegram.org", "https://*.t.me", "http://localhost:*");
    }
}

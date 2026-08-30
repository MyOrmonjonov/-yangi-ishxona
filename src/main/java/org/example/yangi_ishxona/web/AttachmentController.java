package org.example.yangi_ishxona.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.yangi_ishxona.domain.AppUser;
import org.example.yangi_ishxona.domain.TaskAttachment;
import org.example.yangi_ishxona.repository.TaskAttachmentRepository;
import org.example.yangi_ishxona.service.DomainException;
import org.example.yangi_ishxona.service.ProjectService;
import org.example.yangi_ishxona.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLConnection;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Streams task attachments through our own server (rather than redirecting the browser
 * straight to Telegram's file URL) so the bot token never reaches the client - per ТЗ 3.5
 * "вложение видно в карточке задачи ... и открывается по клику". Accepts either the dashboard's
 * session cookie OR the Mini App's {@code X-Telegram-Init-Data} header, since both surfaces
 * link to the same attachment URL.
 */
@Controller
public class AttachmentController {

    private static final String INIT_DATA_HEADER = "X-Telegram-Init-Data";

    private final TaskAttachmentRepository attachmentRepository;
    private final ProjectService projectService;
    private final UserService userService;
    private final TelegramWebAppAuthService webAppAuthService;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${telegram.bot.token}")
    private String botToken;

    public AttachmentController(TaskAttachmentRepository attachmentRepository, ProjectService projectService,
                                 UserService userService, TelegramWebAppAuthService webAppAuthService) {
        this.attachmentRepository = attachmentRepository;
        this.projectService = projectService;
        this.userService = userService;
        this.webAppAuthService = webAppAuthService;
    }

    @GetMapping("/attachments/{id}/open")
    public void open(@PathVariable Long id, HttpSession session,
                      @RequestHeader(value = INIT_DATA_HEADER, required = false) String initData,
                      @RequestParam(value = "initData", required = false) String initDataParam,
                      HttpServletResponse response) throws Exception {
        AppUser user = resolveUser(session, initData != null ? initData : initDataParam);
        TaskAttachment attachment = attachmentRepository.findById(id)
                .orElseThrow(() -> new DomainException("error.attachment.notFound"));
        if (!projectService.canView(user, attachment.getTask().getSprint().getProject())) {
            throw new DomainException("error.attachment.noAccess");
        }

        HttpRequest metaRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/bot" + botToken + "/getFile?file_id="
                        + attachment.getTelegramFileId()))
                .GET().build();
        HttpResponse<String> metaResponse = httpClient.send(metaRequest, HttpResponse.BodyHandlers.ofString());
        JsonNode root = objectMapper.readTree(metaResponse.body());
        if (!root.path("ok").asBoolean(false)) {
            throw new DomainException("error.attachment.fetchFailed");
        }
        String filePath = root.path("result").path("file_path").asText();

        HttpRequest fileRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.telegram.org/file/bot" + botToken + "/" + filePath))
                .GET().build();
        HttpResponse<InputStream> fileResponse = httpClient.send(fileRequest, HttpResponse.BodyHandlers.ofInputStream());

        String fileName = attachment.getOriginalFileName() != null ? attachment.getOriginalFileName() : "file";
        String contentType = URLConnection.guessContentTypeFromName(fileName);
        response.setContentType(contentType != null ? contentType : "application/octet-stream");
        response.setHeader("Content-Disposition", "inline; filename=\"" + fileName.replace("\"", "") + "\"");
        try (InputStream in = fileResponse.body(); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    private AppUser resolveUser(HttpSession session, String initData) {
        if (initData != null && !initData.isBlank()) {
            Long telegramUserId = webAppAuthService.verifyAndExtractUserId(initData)
                    .orElseThrow(() -> new DomainException("error.session.invalid"));
            return userService.findByTelegramId(telegramUserId)
                    .orElseThrow(() -> new DomainException("error.session.invalid"));
        }
        Object userId = session.getAttribute("userId");
        if (userId instanceof Long id) {
            return userService.findById(id).orElseThrow(() -> new DomainException("error.session.invalid"));
        }
        throw new DomainException("error.session.invalid");
    }
}

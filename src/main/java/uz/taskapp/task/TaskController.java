package uz.taskapp.task;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.taskapp.auth.AuthInterceptor;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    List<TaskService.TaskResponse> list(HttpServletRequest request,
                                        @RequestParam Long workspaceId,
                                        @RequestParam(required = false) TaskService.TaskScope scope) {
        return taskService.list(userId(request), workspaceId, scope);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    TaskService.TaskResponse create(HttpServletRequest request,
                                    @Valid @RequestBody CreateTaskRequest body) {
        return taskService.create(userId(request), body);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    TaskService.TaskResponse createMultipart(HttpServletRequest request,
                                             @Valid @RequestPart("task") CreateTaskRequest body,
                                             @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return taskService.create(userId(request), body, files);
    }

    @PatchMapping("/{taskId}/status")
    TaskService.TaskResponse changeStatus(HttpServletRequest request,
                                          @PathVariable Long taskId,
                                          @Valid @RequestBody ChangeStatusRequest body) {
        return taskService.changeStatus(userId(request), taskId, body.status());
    }

    @PutMapping(value = "/{taskId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    TaskService.TaskResponse update(HttpServletRequest request,
                                    @PathVariable Long taskId,
                                    @Valid @RequestBody UpdateTaskRequest body) {
        return taskService.update(userId(request), taskId, body);
    }

    @PutMapping(value = "/{taskId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    TaskService.TaskResponse updateMultipart(HttpServletRequest request,
                                             @PathVariable Long taskId,
                                             @Valid @RequestPart("task") UpdateTaskRequest body,
                                             @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        return taskService.update(userId(request), taskId, body, files);
    }

    @GetMapping("/{taskId}")
    TaskService.TaskResponse detail(HttpServletRequest request, @PathVariable Long taskId) {
        return taskService.detail(userId(request), taskId);
    }

    @GetMapping("/{taskId}/files/{fileId}")
    ResponseEntity<byte[]> file(HttpServletRequest request, @PathVariable Long taskId, @PathVariable Long fileId) {
        TaskService.TaskFileContent file = taskService.fileContent(userId(request), taskId, fileId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (file.contentType() != null && !file.contentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(file.contentType());
            } catch (IllegalArgumentException ignored) {
                // Noma'lum MIME turi xavfsiz binary sifatida qaytariladi.
            }
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    @DeleteMapping("/{taskId}/files/{fileId}")
    TaskService.TaskResponse deleteFile(HttpServletRequest request, @PathVariable Long taskId,
                                        @PathVariable Long fileId) {
        return taskService.deleteFile(userId(request), taskId, fileId);
    }

    @GetMapping("/archived")
    List<TaskService.TaskResponse> archived(HttpServletRequest request, @RequestParam Long workspaceId) {
        return taskService.listArchived(userId(request), workspaceId);
    }

    @PostMapping("/{taskId}/archive")
    ResponseEntity<Void> archive(HttpServletRequest request, @PathVariable Long taskId) {
        taskService.archive(userId(request), taskId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{taskId}/restore")
    TaskService.TaskResponse restore(HttpServletRequest request, @PathVariable Long taskId) {
        return taskService.restore(userId(request), taskId);
    }

    @DeleteMapping("/{taskId}")
    ResponseEntity<Void> hardDelete(HttpServletRequest request, @PathVariable Long taskId) {
        taskService.hardDelete(userId(request), taskId);
        return ResponseEntity.noContent().build();
    }

    private Long userId(HttpServletRequest request) {
        return (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
    }

    public record ChangeStatusRequest(@jakarta.validation.constraints.NotNull TaskStatus status) {}
}

package uz.taskapp.voice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uz.taskapp.auth.AuthInterceptor;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/voice")
public class VoiceDraftController {
    private final VoiceDraftService voiceDraftService;

    public VoiceDraftController(VoiceDraftService voiceDraftService) {
        this.voiceDraftService = voiceDraftService;
    }

    @GetMapping("/availability")
    AvailabilityResponse availability() {
        return new AvailabilityResponse(voiceDraftService.available());
    }

    @PostMapping(value = "/drafts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    VoiceDraftResponse createDraft(HttpServletRequest request,
                                   @RequestPart("audio") MultipartFile audio,
                                   @RequestParam Long workspaceId) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
        return voiceDraftService.createDraft(userId, workspaceId, audio);
    }

    @GetMapping("/drafts/pending/{id}")
    PendingVoiceDraftResponse getPendingDraft(HttpServletRequest request, @PathVariable long id) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
        return voiceDraftService.getPendingDraft(id, userId);
    }

    @GetMapping("/drafts/pending/{id}/audio")
    ResponseEntity<byte[]> getPendingAudio(HttpServletRequest request, @PathVariable long id) {
        Long userId = (Long) request.getAttribute(AuthInterceptor.USER_ID_ATTRIBUTE);
        VoiceDraftService.PendingVoiceAudio audio = voiceDraftService.getPendingAudio(id, userId);
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (audio.mimeType() != null && !audio.mimeType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(audio.mimeType());
            } catch (IllegalArgumentException ignored) {
                // Noma'lum MIME turi xavfsiz binary sifatida qaytariladi.
            }
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("voice-message", StandardCharsets.UTF_8).build().toString())
                .body(audio.bytes());
    }

    record AvailabilityResponse(boolean available) {
    }
}

package uz.taskapp.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "task_files")
public class TaskFileEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "uploaded_by", nullable = false)
    private Long uploadedBy;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, unique = true, length = 255)
    private String storedName;

    @Column(name = "content_type", length = 150)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "content_data", columnDefinition = "bytea")
    private byte[] contentData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskFileEntity() {
    }

    public TaskFileEntity(Long taskId, Long uploadedBy, String originalName, String storedName,
                          String contentType, long sizeBytes, byte[] contentData) {
        this.taskId = taskId;
        this.uploadedBy = uploadedBy;
        this.originalName = originalName;
        this.storedName = storedName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.contentData = contentData;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getCommentId() { return commentId; }
    public Long getUploadedBy() { return uploadedBy; }
    public String getOriginalName() { return originalName; }
    public String getStoredName() { return storedName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public byte[] getContentData() { return contentData; }
    public Instant getCreatedAt() { return createdAt; }
}

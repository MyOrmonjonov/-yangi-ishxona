package org.example.yangi_ishxona.domain;

/**
 * Computed status for Project/Sprint, derived from child tasks by
 * {@link org.example.yangi_ishxona.service.StatusRollupService}. Never set directly.
 */
public enum RollupStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE,
    CANCELLED
}

package org.example.yangi_ishxona.bot;

import lombok.Data;
import org.example.yangi_ishxona.domain.Language;

import java.time.LocalDate;

/** Per-chat conversation state for the step-by-step bot wizards. Not persisted - in-memory only. */
@Data
public class ChatSession {
    private ChatState state = ChatState.NONE;

    private Long selectedProjectId;
    private Long selectedSprintId;
    private Long selectedTaskId;
    private Long selectedUserId;

    private String draftName;
    private String draftDescription;
    private LocalDate draftDeadline;

    private String regFullName;
    private String regPosition;
    private Language regLanguage;

    public void reset() {
        state = ChatState.NONE;
        selectedProjectId = null;
        selectedSprintId = null;
        selectedTaskId = null;
        selectedUserId = null;
        draftName = null;
        draftDescription = null;
        draftDeadline = null;
        regFullName = null;
        regPosition = null;
        regLanguage = null;
    }
}

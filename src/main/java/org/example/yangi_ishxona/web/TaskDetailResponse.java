package org.example.yangi_ishxona.web;

import lombok.Value;

import java.util.List;

@Value
public class TaskDetailResponse {
    Long id;
    String name;
    String description;
    String executorName;
    String statusCode;
    String statusLabel;
    String deadlineLabel;
    String colorClass;
    List<CommentRow> comments;
    List<AttachmentDto> attachments;
    List<HistoryRow> history;
    TaskActionsDto actions;
}

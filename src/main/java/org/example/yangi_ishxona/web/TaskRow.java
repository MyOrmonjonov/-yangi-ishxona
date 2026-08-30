package org.example.yangi_ishxona.web;

import lombok.Value;

@Value
public class TaskRow {
    Long id;
    String name;
    String executorName;
    String deadlineLabel;
    String colorClass;
    String statusLabel;
    String statusCode;
    boolean overdue;
}

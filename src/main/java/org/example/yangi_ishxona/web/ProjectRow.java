package org.example.yangi_ishxona.web;

import lombok.Value;

@Value
public class ProjectRow {
    Long id;
    String name;
    String responsibleName;
    String deadlineLabel;
    String colorClass;
    int percentDone;
    long overdueCount;
    String statusLabel;
}

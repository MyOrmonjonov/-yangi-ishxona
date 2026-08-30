package org.example.yangi_ishxona.web;

import lombok.Value;

@Value
public class SprintRow {
    Long id;
    Long projectId;
    String name;
    String responsibleName;
    String deadlineLabel;
    String colorClass;
    int percentDone;
    String statusLabel;
}

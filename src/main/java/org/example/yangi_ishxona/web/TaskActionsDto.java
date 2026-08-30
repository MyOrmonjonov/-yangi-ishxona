package org.example.yangi_ishxona.web;

import lombok.Value;

@Value
public class TaskActionsDto {
    boolean canStart;
    boolean canComplete;
    boolean canCancel;
    boolean canComment;
    boolean canRequestPostpone;
}

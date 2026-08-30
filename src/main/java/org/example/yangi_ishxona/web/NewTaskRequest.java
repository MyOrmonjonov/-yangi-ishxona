package org.example.yangi_ishxona.web;

public record NewTaskRequest(String name, String description, Long executorId, String deadline) {
}

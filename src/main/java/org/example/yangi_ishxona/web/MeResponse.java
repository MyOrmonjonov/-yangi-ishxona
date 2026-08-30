package org.example.yangi_ishxona.web;

import lombok.Value;

@Value
public class MeResponse {
    Long id;
    String fullName;
    String position;
    String role;
    String roleLabel;
    String language;
}

package org.example.yangi_ishxona.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MiniAppPageController {

    @GetMapping("/miniapp")
    public String page() {
        return "miniapp";
    }
}

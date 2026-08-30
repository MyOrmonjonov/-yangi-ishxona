package org.example.yangi_ishxona.web;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.example.yangi_ishxona.domain.Language;
import org.example.yangi_ishxona.service.DomainException;
import org.example.yangi_ishxona.service.Messages;
import org.example.yangi_ishxona.service.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(assignableTypes = {DashboardController.class, AttachmentController.class})
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final Messages messages;
    private final UserService userService;

    @ExceptionHandler(DomainException.class)
    public ModelAndView handle(DomainException e, HttpSession session) {
        Language lang = currentLanguage(session);
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("message", messages.t(lang, e.getMessageKey(), (Object[]) e.getArgs()));
        return mv;
    }

    private Language currentLanguage(HttpSession session) {
        Object userId = session.getAttribute("userId");
        if (userId instanceof Long id) {
            return userService.findById(id).map(u -> u.getLanguage()).orElse(Language.RU);
        }
        return Language.RU;
    }
}

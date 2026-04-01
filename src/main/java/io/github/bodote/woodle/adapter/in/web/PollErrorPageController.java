package io.github.bodote.woodle.adapter.in.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

@Controller
public class PollErrorPageController {

    @GetMapping("/poll/error")
    public String renderTestErrorPage(@RequestParam("test-http-status") int testHttpStatus,
                                      Model model,
                                      HttpServletRequest request,
                                      HttpServletResponse response) {
        HttpStatus status = HttpStatus.resolve(testHttpStatus);
        if (status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungültiger HTTP-Statuscode für Testzwecke");
        }

        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, status.value());
        response.setStatus(status.value());

        model.addAttribute("status", status.value());
        model.addAttribute("timestamp", OffsetDateTime.now());
        model.addAttribute("path", request.getRequestURI());
        return "error";
    }
}

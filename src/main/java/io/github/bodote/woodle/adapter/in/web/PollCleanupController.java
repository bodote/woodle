package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.CleanupExpiredPollsUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Internal endpoint triggered by the weekly EventBridge schedule. The AWS Lambda Web
 * Adapter forwards the (non-HTTP) schedule event as a POST to the pass-through path
 * ({@code /events}). The endpoint is also reachable through the public HTTP API, so it
 * is guarded by a shared token carried in the request body; if no token is configured
 * the endpoint is disabled.
 */
@RestController
public class PollCleanupController {

    static final String CLEANUP_TASK = "cleanup-expired-polls";

    private final CleanupExpiredPollsUseCase cleanupExpiredPollsUseCase;
    private final String configuredToken;

    public PollCleanupController(CleanupExpiredPollsUseCase cleanupExpiredPollsUseCase,
                                 @Value("${woodle.cleanup.token:}") String configuredToken) {
        this.cleanupExpiredPollsUseCase = cleanupExpiredPollsUseCase;
        this.configuredToken = configuredToken;
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Integer>> handleEvent(@RequestBody(required = false) CleanupEventDTO event) {
        if (configuredToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cleanup is not enabled");
        }
        if (event == null || !configuredToken.equals(event.token()) || !CLEANUP_TASK.equals(event.task())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid cleanup request");
        }
        int deleted = cleanupExpiredPollsUseCase.cleanupExpiredPolls();
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}

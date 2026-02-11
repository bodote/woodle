package io.github.bodote.woodle.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ApiExceptionTestController {

    static final String PRECONDITION_FAILED_PATH = "/test/precondition-failed";
    static final String PRECONDITION_REQUIRED_PATH = "/test/precondition-required";
    static final String CONFLICT_PATH = "/test/conflict";
    static final String BAD_REQUEST_PATH = "/test/bad-request";
    static final String INTERNAL_ERROR_PATH = "/test/internal-error";
    static final String ILLEGAL_ARGUMENT_PATH = "/test/illegal-argument";
    static final String UNREADABLE_PAYLOAD_PATH = "/test/unreadable";
    static final String ETAG_MISMATCH_MESSAGE = "etag mismatch";
    static final String PRECONDITION_REQUIRED_MESSAGE = "missing if-match";
    static final String CONFLICT_MESSAGE = "duplicate vote";
    static final String BAD_REQUEST_MESSAGE = "invalid input";
    static final String INTERNAL_ERROR_MESSAGE = "unsupported status";
    static final String ILLEGAL_ARGUMENT_MESSAGE = "invalid participant";

    @GetMapping(PRECONDITION_FAILED_PATH)
    void preconditionFailed() {
        throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, ETAG_MISMATCH_MESSAGE);
    }

    @GetMapping(PRECONDITION_REQUIRED_PATH)
    void preconditionRequired() {
        throw new ResponseStatusException(HttpStatus.PRECONDITION_REQUIRED, PRECONDITION_REQUIRED_MESSAGE);
    }

    @GetMapping(CONFLICT_PATH)
    void conflict() {
        throw new ResponseStatusException(HttpStatus.CONFLICT, CONFLICT_MESSAGE);
    }

    @GetMapping(BAD_REQUEST_PATH)
    void badRequest() {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, BAD_REQUEST_MESSAGE);
    }

    @GetMapping(INTERNAL_ERROR_PATH)
    void internalError() {
        throw new ResponseStatusException(HttpStatus.I_AM_A_TEAPOT, INTERNAL_ERROR_MESSAGE);
    }

    @GetMapping(ILLEGAL_ARGUMENT_PATH)
    void illegalArgument() {
        throw new IllegalArgumentException(ILLEGAL_ARGUMENT_MESSAGE);
    }

    @PostMapping(UNREADABLE_PAYLOAD_PATH)
    void unreadablePayload(@RequestBody CreatePollRequestDTO requestDTO) {
        // Body intentionally empty; malformed JSON should fail before method invocation.
    }
}

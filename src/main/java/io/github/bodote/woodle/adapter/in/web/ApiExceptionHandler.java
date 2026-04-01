package io.github.bodote.woodle.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        ApiErrorResponseDTO response = new ApiErrorResponseDTO(
                new ApiErrorDTO(codeFor(status), exception.getReason())
        );
        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleUnreadablePayload() {
        ApiErrorResponseDTO response = new ApiErrorResponseDTO(
                new ApiErrorDTO("VALIDATION_ERROR", "Invalid request payload")
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleIllegalArgument(IllegalArgumentException exception) {
        ApiErrorResponseDTO response = new ApiErrorResponseDTO(
                new ApiErrorDTO("VALIDATION_ERROR", exception.getMessage())
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    private String codeFor(HttpStatus status) {
        if (status == HttpStatus.NOT_FOUND) {
            return "POLL_NOT_FOUND";
        }
        if (status == HttpStatus.PRECONDITION_FAILED) {
            return "PRECONDITION_FAILED";
        }
        if (status == HttpStatus.PRECONDITION_REQUIRED) {
            return "PRECONDITION_REQUIRED";
        }
        if (status == HttpStatus.CONFLICT) {
            return "CONFLICT";
        }
        if (status == HttpStatus.BAD_REQUEST) {
            return "VALIDATION_ERROR";
        }
        return "INTERNAL_ERROR";
    }
}

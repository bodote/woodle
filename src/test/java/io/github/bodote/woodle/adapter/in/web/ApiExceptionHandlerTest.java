package io.github.bodote.woodle.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApiExceptionTestController.class)
@Import(ApiExceptionHandler.class)
@DisplayName("API exception handler")
class ApiExceptionHandlerTest {

    private static final String VALIDATION_ERROR_CODE = "VALIDATION_ERROR";
    private static final String INVALID_PAYLOAD_MESSAGE = "Invalid request payload";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("maps PRECONDITION_FAILED to an API error code")
    void mapsPreconditionFailedToApiErrorCode() throws Exception {
        mockMvc.perform(get(ApiExceptionTestController.PRECONDITION_FAILED_PATH))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.error.code").value("PRECONDITION_FAILED"))
                .andExpect(jsonPath("$.error.message").value(ApiExceptionTestController.ETAG_MISMATCH_MESSAGE));
    }

    @Test
    @DisplayName("maps PRECONDITION_REQUIRED to an API error code")
    void mapsPreconditionRequiredToApiErrorCode() throws Exception {
        mockMvc.perform(get(ApiExceptionTestController.PRECONDITION_REQUIRED_PATH))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.error.code").value("PRECONDITION_REQUIRED"))
                .andExpect(jsonPath("$.error.message").value(ApiExceptionTestController.PRECONDITION_REQUIRED_MESSAGE));
    }

    @Test
    @DisplayName("maps CONFLICT to an API error code")
    void mapsConflictToApiErrorCode() throws Exception {
        mockMvc.perform(get(ApiExceptionTestController.CONFLICT_PATH))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value(ApiExceptionTestController.CONFLICT_MESSAGE));
    }

    @Test
    @DisplayName("maps BAD_REQUEST to validation error code")
    void mapsBadRequestToValidationErrorCode() throws Exception {
        mockMvc.perform(get(ApiExceptionTestController.BAD_REQUEST_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(VALIDATION_ERROR_CODE))
                .andExpect(jsonPath("$.error.message").value(ApiExceptionTestController.BAD_REQUEST_MESSAGE));
    }

    @Test
    @DisplayName("maps unknown response status to internal error code")
    void mapsUnknownResponseStatusToInternalErrorCode() throws Exception {
        mockMvc.perform(get(ApiExceptionTestController.INTERNAL_ERROR_PATH))
                .andExpect(status().isIAmATeapot())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(ApiExceptionTestController.INTERNAL_ERROR_MESSAGE));
    }

    @Test
    @DisplayName("maps IllegalArgumentException to validation error")
    void mapsIllegalArgumentExceptionToValidationError() throws Exception {
        mockMvc.perform(get(ApiExceptionTestController.ILLEGAL_ARGUMENT_PATH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(VALIDATION_ERROR_CODE))
                .andExpect(jsonPath("$.error.message").value(ApiExceptionTestController.ILLEGAL_ARGUMENT_MESSAGE));
    }

    @Test
    @DisplayName("maps malformed JSON payload to validation error")
    void mapsMalformedJsonPayloadToValidationError() throws Exception {
        String malformedJson = "{\"authorName\":";

        mockMvc.perform(post(ApiExceptionTestController.UNREADABLE_PAYLOAD_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value(VALIDATION_ERROR_CODE))
                .andExpect(jsonPath("$.error.message").value(INVALID_PAYLOAD_MESSAGE));
    }
}

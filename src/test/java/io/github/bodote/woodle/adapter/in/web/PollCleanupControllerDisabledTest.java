package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.CleanupExpiredPollsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PollCleanupController.class, properties = "woodle.cleanup.token=")
@DisplayName("POST /events (cleanup) when no token is configured")
class PollCleanupControllerDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CleanupExpiredPollsUseCase cleanupExpiredPollsUseCase;

    @Test
    @DisplayName("is disabled and rejects every request")
    void isDisabledWhenTokenBlank() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"cleanup-expired-polls\",\"token\":\"\"}"))
                .andExpect(status().isForbidden());

        verify(cleanupExpiredPollsUseCase, never()).cleanupExpiredPolls();
    }
}

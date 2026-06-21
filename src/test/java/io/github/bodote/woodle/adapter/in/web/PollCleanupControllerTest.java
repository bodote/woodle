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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PollCleanupController.class, properties = "woodle.cleanup.token=secret-token")
@DisplayName("POST /events (cleanup)")
class PollCleanupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CleanupExpiredPollsUseCase cleanupExpiredPollsUseCase;

    @Test
    @DisplayName("runs cleanup and returns deleted count for a valid request")
    void runsCleanupForValidRequest() throws Exception {
        when(cleanupExpiredPollsUseCase.cleanupExpiredPolls()).thenReturn(3);

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"cleanup-expired-polls\",\"token\":\"secret-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(3));

        verify(cleanupExpiredPollsUseCase).cleanupExpiredPolls();
    }

    @Test
    @DisplayName("rejects a request with the wrong token")
    void rejectsWrongToken() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"cleanup-expired-polls\",\"token\":\"wrong\"}"))
                .andExpect(status().isForbidden());

        verify(cleanupExpiredPollsUseCase, never()).cleanupExpiredPolls();
    }

    @Test
    @DisplayName("rejects a request with no body")
    void rejectsRequestWithNoBody() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isForbidden());

        verify(cleanupExpiredPollsUseCase, never()).cleanupExpiredPolls();
    }

    @Test
    @DisplayName("rejects a request for an unknown task")
    void rejectsUnknownTask() throws Exception {
        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"task\":\"something-else\",\"token\":\"secret-token\"}"))
                .andExpect(status().isForbidden());

        verify(cleanupExpiredPollsUseCase, never()).cleanupExpiredPolls();
    }
}

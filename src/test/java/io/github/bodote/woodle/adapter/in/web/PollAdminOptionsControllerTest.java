package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.adapter.in.web.PollAdminOptionsController;
import io.github.bodote.woodle.application.port.in.AdminPollOptionsUseCase;
import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(PollAdminOptionsController.class)
@DisplayName("admin options")
class PollAdminOptionsControllerTest {

    private static final String POLL_ID = "00000000-0000-0000-0000-000000000120";
    private static final String ADMIN_SECRET = "AdminSecret12";

    @MockitoBean
    private AdminPollOptionsUseCase adminPollOptionsUseCase;

    @MockitoBean
    private ReadPollUseCase readPollUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("adds option and returns fragment")
    void addsOptionAndReturnsFragment() throws Exception {
        Poll poll = TestFixtures.poll(
                UUID.fromString(POLL_ID),
                ADMIN_SECRET,
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );
        when(readPollUseCase.getAdmin(UUID.fromString(POLL_ID), ADMIN_SECRET)).thenReturn(poll);

        mockMvc.perform(post("/poll/" + POLL_ID + "-" + ADMIN_SECRET + "/options/add")
                        .param("date", "2026-02-12"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026-02-10")))
                .andExpect(content().string(containsString("Termin löschen")));

        verify(adminPollOptionsUseCase).addDate(any(UUID.class), any(String.class), any(LocalDate.class), any());
    }

    @Test
    @DisplayName("adds intraday option with start time and returns fragment")
    void addsIntradayOptionWithStartTimeAndReturnsFragment() throws Exception {
        Poll poll = TestFixtures.poll(
                UUID.fromString(POLL_ID),
                ADMIN_SECRET,
                EventType.INTRADAY,
                90,
                List.of(TestFixtures.option(
                        UUID.randomUUID(),
                        LocalDate.of(2026, 2, 12),
                        LocalTime.of(14, 30),
                        LocalTime.of(16, 0))),
                List.of()
        );
        when(readPollUseCase.getAdmin(UUID.fromString(POLL_ID), ADMIN_SECRET)).thenReturn(poll);

        mockMvc.perform(post("/poll/" + POLL_ID + "-" + ADMIN_SECRET + "/options/add")
                        .param("date", "2026-02-12")
                        .param("startTime", "14:30"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026-02-12")))
                .andExpect(content().string(containsString("14:30")))
                .andExpect(content().string(containsString("Termin löschen")));

        verify(adminPollOptionsUseCase).addDate(
                UUID.fromString(POLL_ID),
                ADMIN_SECRET,
                LocalDate.of(2026, 2, 12),
                LocalTime.of(14, 30)
        );
    }

    @Test
    @DisplayName("removes option and returns fragment")
    void removesOptionAndReturnsFragment() throws Exception {
        Poll poll = TestFixtures.poll(
                UUID.fromString(POLL_ID),
                ADMIN_SECRET,
                EventType.INTRADAY,
                60,
                List.of(TestFixtures.option(
                        UUID.randomUUID(),
                        LocalDate.of(2026, 2, 10),
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0))),
                List.of()
        );
        when(readPollUseCase.getAdmin(UUID.fromString(POLL_ID), ADMIN_SECRET)).thenReturn(poll);

        mockMvc.perform(post("/poll/" + POLL_ID + "-" + ADMIN_SECRET + "/options/remove")
                        .param("date", "2026-02-10")
                        .param("startTime", "09:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026-02-10")))
                .andExpect(content().string(containsString("Termin löschen")));

        verify(adminPollOptionsUseCase).removeOption(
                UUID.fromString(POLL_ID),
                ADMIN_SECRET,
                LocalDate.of(2026, 2, 10),
                LocalTime.of(9, 0)
        );
    }
}

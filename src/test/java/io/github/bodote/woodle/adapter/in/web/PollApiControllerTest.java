package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.application.port.in.CreatePollResult;
import io.github.bodote.woodle.application.port.in.CreatePollUseCase;
import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.application.port.in.command.CreatePollCommand;
import io.github.bodote.woodle.application.port.out.PollRepository;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.testfixtures.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PollApiController.class)
@DisplayName("Poll API")
class PollApiControllerTest {

    private static final UUID POLL_ID = UUID.fromString("00000000-0000-0000-0000-000000000111");
    private static final String ADMIN_SECRET = "Abc123XyZ789";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePollUseCase createPollUseCase;

    @MockitoBean
    private ReadPollUseCase readPollUseCase;

    @MockitoBean
    private PollRepository pollRepository;

    @Test
    @DisplayName("creates poll via POST /v1/polls")
    void createsPollViaPost() throws Exception {
        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(POLL_ID, ADMIN_SECRET, true));

        String request = """
                {
                  "authorName": "Alice",
                  "authorEmail": "alice@example.com",
                  "title": "Team lunch",
                  "description": "Pick a date",
                  "eventType": "ALL_DAY",
                  "durationMinutes": null,
                  "dates": ["2026-02-10", "2026-02-11"],
                  "startTimes": [],
                  "expiresAtOverride": null
                }
                """;

        mockMvc.perform(post("/v1/polls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/v1/polls/" + POLL_ID))
                .andExpect(jsonPath("$.id").value(POLL_ID.toString()))
                .andExpect(jsonPath("$.adminUrl").value("/poll/" + POLL_ID + "-" + ADMIN_SECRET))
                .andExpect(jsonPath("$.voteUrl").value("/poll/" + POLL_ID))
                .andExpect(jsonPath("$.notificationQueued").value(true));
    }

    @Test
    @DisplayName("gets poll via GET /v1/polls/{pollId}")
    void getsPollViaGet() throws Exception {
        Poll poll = TestFixtures.poll(
                POLL_ID,
                List.of(TestFixtures.option(UUID.fromString("00000000-0000-0000-0000-000000000201"), LocalDate.of(2026, 2, 10))),
                List.of()
        );
        when(readPollUseCase.getPublic(POLL_ID)).thenReturn(poll);

        mockMvc.perform(get("/v1/polls/" + POLL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(POLL_ID.toString()))
                .andExpect(jsonPath("$.title").value("Team Meeting"))
                .andExpect(jsonPath("$.options[0].date").value("2026-02-10"));
    }

    @Test
    @DisplayName("returns 404 when poll is missing")
    void returns404WhenPollMissing() throws Exception {
        doThrow(new IllegalArgumentException("Poll not found"))
                .when(readPollUseCase).getPublic(POLL_ID);

        mockMvc.perform(get("/v1/polls/" + POLL_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("returns validation error body for invalid create payload")
    void returnsValidationErrorBodyForInvalidCreatePayload() throws Exception {
        String request = """
                {
                  "authorName": "Alice",
                  "authorEmail": "alice@example.com",
                  "description": "Pick a date",
                  "eventType": "ALL_DAY",
                  "durationMinutes": null,
                  "dates": ["2026-02-10"],
                  "startTimes": [],
                  "expiresAtOverride": null
                }
                """;

        mockMvc.perform(post("/v1/polls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("gets active poll count via GET /v1/polls/active-count")
    void getsActivePollCountViaGet() throws Exception {
        when(pollRepository.countActivePolls()).thenReturn(7L);

        mockMvc.perform(get("/v1/polls/active-count"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(content().string("7"));
    }

    @Test
    @DisplayName("gets active poll count via GET /poll/active-count")
    void getsActivePollCountViaPollPath() throws Exception {
        when(pollRepository.countActivePolls()).thenReturn(9L);

        mockMvc.perform(get("/poll/active-count"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(content().string("9"));
    }

    @Test
    @DisplayName("defaults missing startTimes to empty list in create command")
    void defaultsMissingStartTimesToEmptyListInCreateCommand() throws Exception {
        when(createPollUseCase.create(any(CreatePollCommand.class)))
                .thenReturn(new CreatePollResult(POLL_ID, ADMIN_SECRET, false));

        String request = """
                {
                  "authorName": "Alice",
                  "authorEmail": "alice@example.com",
                  "title": "Team lunch",
                  "description": "Pick a date",
                  "eventType": "ALL_DAY",
                  "durationMinutes": null,
                  "dates": ["2026-02-10"],
                  "expiresAtOverride": null
                }
                """;

        mockMvc.perform(post("/v1/polls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        ArgumentCaptor<CreatePollCommand> captor = ArgumentCaptor.forClass(CreatePollCommand.class);
        org.mockito.Mockito.verify(createPollUseCase).create(captor.capture());
        assertEquals(List.of(), captor.getValue().startTimes());
    }

    @Test
    @DisplayName("maps option start and end time strings when poll has timed options")
    void mapsOptionStartAndEndTimeStringsWhenPollHasTimedOptions() throws Exception {
        Poll poll = TestFixtures.poll(
                POLL_ID,
                "secret",
                io.github.bodote.woodle.domain.model.EventType.INTRADAY,
                30,
                List.of(TestFixtures.option(
                        UUID.fromString("00000000-0000-0000-0000-000000000209"),
                        LocalDate.of(2026, 2, 12),
                        LocalTime.of(10, 15),
                        LocalTime.of(10, 45))),
                List.of()
        );
        when(readPollUseCase.getPublic(POLL_ID)).thenReturn(poll);

        mockMvc.perform(get("/v1/polls/" + POLL_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0].startTime").value("10:15"))
                .andExpect(jsonPath("$.options[0].endTime").value("10:45"));
    }
}

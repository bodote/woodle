package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.adapter.in.web.PollViewController;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WebMvcTest(PollViewController.class)
@DisplayName("/poll/{id}")
class PollViewControllerTest {

    @MockitoBean
    private ReadPollUseCase readPollUseCase;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("renders participant view")
    void rendersParticipantView() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000010");
        Poll poll = TestFixtures.poll(
                pollId,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );

        when(readPollUseCase.getPublic(pollId)).thenReturn(poll);

        mockMvc.perform(get("/poll/" + pollId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Team Meeting")));
    }

    @Test
    @DisplayName("renders admin view")
    void rendersAdminView() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        String adminSecret = TestFixtures.ADMIN_SECRET;
        Poll poll = TestFixtures.poll(
                pollId,
                adminSecret,
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );

        when(readPollUseCase.getAdmin(pollId, adminSecret)).thenReturn(poll);

        mockMvc.perform(get("/poll/" + pollId + "-" + adminSecret))
                .andExpect(status().isOk())
                .andExpect(model().attribute("adminView", true))
                .andExpect(content().string(containsString("Link für Teilnehmende")))
                .andExpect(content().string(containsString("Admin-Link")))
                .andExpect(content().string(containsString("Termin löschen")))
                .andExpect(content().string(containsString("2026-02-10")))
                .andExpect(content().string(not(containsString("option.date"))))
                .andExpect(content().string(containsString("Kopiere Link")))
                .andExpect(content().string(not(containsString("id=\"participant-link\" type=\"text\""))))
                .andExpect(content().string(not(containsString("id=\"admin-link\" type=\"text\""))));
    }

    @Test
    @DisplayName("renders absolute share links for local host and port")
    void rendersAbsoluteShareLinksForLocalHostAndPort() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000014");
        String adminSecret = "AdminSecret78";
        Poll poll = TestFixtures.poll(
                pollId,
                adminSecret,
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );

        when(readPollUseCase.getAdmin(pollId, adminSecret)).thenReturn(poll);

        mockMvc.perform(get("/poll/" + pollId + "-" + adminSecret)
                        .with(request -> {
                            request.setScheme("http");
                            request.setServerName("localhost");
                            request.setServerPort(8088);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http://localhost:8088/poll/" + pollId)))
                .andExpect(content().string(containsString("http://localhost:8088/poll/" + pollId + "-" + adminSecret)));
    }

    @Test
    @DisplayName("renders absolute share links for forwarded https host")
    void rendersAbsoluteShareLinksForForwardedHttpsHost() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000015");
        String adminSecret = "AdminSecret90";
        Poll poll = TestFixtures.poll(
                pollId,
                adminSecret,
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );

        when(readPollUseCase.getAdmin(pollId, adminSecret)).thenReturn(poll);

        mockMvc.perform(get("/poll/" + pollId + "-" + adminSecret)
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "woodle.click")
                        .header("X-Forwarded-Port", "443")
                        .with(request -> {
                            request.setScheme("http");
                            request.setServerName("internal-host");
                            request.setServerPort(8080);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://woodle.click/poll/" + pollId)))
                .andExpect(content().string(containsString("https://woodle.click/poll/" + pollId + "-" + adminSecret)));
    }

    @Test
    @DisplayName("renders admin sections with options editor before share links")
    void rendersAdminSectionsOrder() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000013");
        String adminSecret = "AdminSecret56";
        Poll poll = TestFixtures.poll(
                pollId,
                adminSecret,
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );

        when(readPollUseCase.getAdmin(pollId, adminSecret)).thenReturn(poll);

        String content = mockMvc.perform(get("/poll/" + pollId + "-" + adminSecret))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        int optionsEditorIndex = content.indexOf("Optionen bearbeiten");
        int shareLinksIndex = content.indexOf("Links zum Teilen");
        assertTrue(optionsEditorIndex >= 0 && shareLinksIndex >= 0);
        assertTrue(optionsEditorIndex < shareLinksIndex);
    }

    @Test
    @DisplayName("renders admin delete with start time for intraday options")
    void rendersAdminDeleteWithStartTimeForIntradayOptions() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000012");
        String adminSecret = "AdminSecret34";
        Poll poll = TestFixtures.poll(
                pollId,
                adminSecret,
                EventType.INTRADAY,
                90,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10), LocalTime.of(9, 0), LocalTime.of(10, 30))),
                List.of()
        );

        when(readPollUseCase.getAdmin(pollId, adminSecret)).thenReturn(poll);

        mockMvc.perform(get("/poll/" + pollId + "-" + adminSecret))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"startTime\"")))
                .andExpect(content().string(containsString("value=\"09:00\"")))
                .andExpect(content().string(not(containsString("Datum entfernen"))));
    }
}

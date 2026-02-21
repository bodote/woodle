package io.github.bodote.woodle.adapter.in.web;

import io.github.bodote.woodle.testfixtures.TestFixtures;

import io.github.bodote.woodle.adapter.in.web.PollViewController;
import io.github.bodote.woodle.application.port.in.ReadPollUseCase;
import io.github.bodote.woodle.domain.model.EventType;
import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import io.github.bodote.woodle.domain.model.PollResponse;
import io.github.bodote.woodle.domain.model.PollVote;
import io.github.bodote.woodle.domain.model.PollVoteValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.ExtendedModelMap;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
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
    @DisplayName("renders admin email warning when email delivery failed")
    void rendersAdminEmailWarningWhenEmailDeliveryFailed() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000031");
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

        mockMvc.perform(get("/poll/" + pollId + "-" + adminSecret + "?emailFailed=true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("E-Mail konnte nicht gesendet werden")));
    }

    @Test
    @DisplayName("renders admin email disabled info when email delivery is disabled")
    void rendersAdminEmailDisabledInfoWhenEmailDeliveryIsDisabled() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000032");
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

        mockMvc.perform(get("/poll/" + pollId + "-" + adminSecret + "?emailDisabled=true"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Emailversand ist deaktiviert")));
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
    @DisplayName("renders absolute share links for RFC forwarded header host")
    void rendersAbsoluteShareLinksForRfcForwardedHeaderHost() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000016");
        String adminSecret = "AdminSecret99";
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
                        .header("Forwarded", "for=203.0.113.10;proto=https;host=woodle.click")
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
    @DisplayName("renders share links for quoted Forwarded host and proto values")
    void rendersShareLinksForQuotedForwardedHostAndProtoValues() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000018");
        String adminSecret = "AdminSecretQuoted";
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
                        .header("Forwarded", "for=203.0.113.10;proto=\"https\";host=\"quoted.woodle.click\""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://quoted.woodle.click/poll/" + pollId)))
                .andExpect(content().string(containsString("https://quoted.woodle.click/poll/" + pollId + "-" + adminSecret)));
    }

    @Test
    @DisplayName("renders share links for forwarded host with explicit port in host value")
    void rendersShareLinksForForwardedHostWithExplicitPort() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000019");
        String adminSecret = "AdminSecretPort";
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
                        .header("Forwarded", "for=203.0.113.10;proto=https;host=woodle.click:8443"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://woodle.click:8443/poll/" + pollId)))
                .andExpect(content().string(containsString("https://woodle.click:8443/poll/" + pollId + "-" + adminSecret)));
    }

    @Test
    @DisplayName("renders share links for proxy http host without forwarded port")
    void rendersShareLinksForProxyHttpHostWithoutForwardedPort() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        String adminSecret = "AdminSecretHttp";
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
                        .header("X-Forwarded-Proto", "http")
                        .header("X-Forwarded-Host", "proxy.woodle.click")
                        .with(request -> {
                            request.setScheme("https");
                            request.setServerName("internal-host");
                            request.setServerPort(8443);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http://proxy.woodle.click/poll/" + pollId)))
                .andExpect(content().string(containsString("http://proxy.woodle.click/poll/" + pollId + "-" + adminSecret)));
    }

    @Test
    @DisplayName("renders participant grid for intraday option labels and missing votes")
    void rendersParticipantGridForIntradayOptionLabelsAndMissingVotes() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID optionOne = UUID.fromString("00000000-0000-0000-0000-000000000301");
        UUID optionTwo = UUID.fromString("00000000-0000-0000-0000-000000000302");
        PollResponse response = TestFixtures.response(
                UUID.fromString("00000000-0000-0000-0000-000000000401"),
                "Max",
                List.of(new PollVote(optionOne, PollVoteValue.YES))
        );
        Poll poll = TestFixtures.poll(
                pollId,
                List.of(
                        TestFixtures.option(optionOne, LocalDate.of(2026, 2, 10), LocalTime.of(9, 0), LocalTime.of(10, 30)),
                        TestFixtures.option(optionTwo, LocalDate.of(2026, 2, 11))
                ),
                List.of(response)
        );

        when(readPollUseCase.getPublic(pollId)).thenReturn(poll);

        mockMvc.perform(get("/poll/" + pollId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("09:00")))
                .andExpect(content().string(containsString("Di., 10.02.")))
                .andExpect(content().string(containsString("✓")));
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

    @Test
    @DisplayName("renders time input for adding options in intraday admin view")
    void rendersTimeInputForAddingOptionsInIntradayAdminView() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000027");
        String adminSecret = "AdminSecretIntradayInput";
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
                .andExpect(content().string(containsString("id=\"new-start-time\"")))
                .andExpect(content().string(containsString("type=\"time\"")));
    }

    @Test
    @DisplayName("resets admin add-option form after successful htmx request")
    void resetsAdminAddOptionFormAfterSuccessfulHtmxRequest() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000028");
        String adminSecret = "AdminSecretResetForm";
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
                .andExpect(content().string(containsString("hx-on::after-request=\"if(event.detail.successful) this.reset()\"")));
    }

    @Test
    @DisplayName("falls back to https when request scheme is blank")
    void fallsBackToHttpsWhenRequestSchemeIsBlank() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000022");
        String adminSecret = "AdminSecretBlankScheme";
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
                            request.setScheme("");
                            request.setServerName("fallback.woodle.click");
                            request.setServerPort(443);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://fallback.woodle.click/poll/" + pollId)))
                .andExpect(content().string(containsString("https://fallback.woodle.click/poll/" + pollId + "-" + adminSecret)));
    }

    @Test
    @DisplayName("renders share links with empty host when no host information is available")
    void rendersShareLinksWithEmptyHostWhenNoHostInformationIsAvailable() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000023");
        String adminSecret = "AdminSecretEmptyHost";
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
                            request.setScheme("https");
                            request.setServerName("");
                            request.setServerPort(443);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https:///poll/" + pollId)))
                .andExpect(content().string(containsString("https:///poll/" + pollId + "-" + adminSecret)));
    }

    @Test
    @DisplayName("ignores malformed forwarded tokens without equals sign")
    void ignoresMalformedForwardedTokensWithoutEqualsSign() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000024");
        String adminSecret = "AdminSecretMalformedForwarded";
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
                        .header("Forwarded", "for;proto=https;host=forwarded.woodle.click")
                        .with(request -> {
                            request.setScheme("http");
                            request.setServerName("internal-host");
                            request.setServerPort(8080);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("https://forwarded.woodle.click/poll/" + pollId)))
                .andExpect(content().string(containsString("https://forwarded.woodle.click/poll/" + pollId + "-" + adminSecret)));
    }

    @Test
    @DisplayName("treats null configured public base URL as empty")
    void treatsNullConfiguredPublicBaseUrlAsEmpty() {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000025");
        String adminSecret = "AdminSecretNullBase";
        Poll poll = TestFixtures.poll(
                pollId,
                adminSecret,
                EventType.ALL_DAY,
                null,
                List.of(TestFixtures.option(UUID.randomUUID(), LocalDate.of(2026, 2, 10))),
                List.of()
        );
        when(readPollUseCase.getAdmin(pollId, adminSecret)).thenReturn(poll);

        PollViewController controller = new PollViewController(readPollUseCase, null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("null-base.woodle.click");
        request.setServerPort(443);
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.viewPollAdmin(pollId, adminSecret, false, false, model, request);

        assertEquals("poll/view", view);
        assertEquals("https://null-base.woodle.click/poll/" + pollId, model.getAttribute("participantShareUrl"));
        assertEquals("https://null-base.woodle.click/poll/" + pollId + "-" + adminSecret, model.getAttribute("adminShareUrl"));
    }

    @Test
    @DisplayName("falls back to request origin when Forwarded header has no host or proto")
    void fallsBackToRequestOriginWhenForwardedHeaderHasNoHostOrProto() throws Exception {
        UUID pollId = UUID.fromString("00000000-0000-0000-0000-000000000026");
        String adminSecret = "AdminSecretForwardedFallback";
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
                        .header("Forwarded", "for=203.0.113.10")
                        .with(request -> {
                            request.setScheme("http");
                            request.setServerName("fallback-forwarded.woodle.click");
                            request.setServerPort(8080);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http://fallback-forwarded.woodle.click:8080/poll/" + pollId)))
                .andExpect(content().string(containsString("http://fallback-forwarded.woodle.click:8080/poll/" + pollId + "-" + adminSecret)));
    }
}

package io.github.bodote.woodle.adapter.out.email;

import io.github.bodote.woodle.application.port.out.PollCreatedEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("SesPollEmailSender")
class SesPollEmailSenderTest {

    @Test
    @DisplayName("sends poll created email via SES and returns true")
    void sendsPollCreatedEmailViaSesAndReturnsTrue() {
        SesV2Client sesV2Client = mock(SesV2Client.class);
        when(sesV2Client.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder().build());
        SesPollEmailSender sender = new SesPollEmailSender(
                sesV2Client,
                "noreply@woodle.click",
                "[prod]",
                "https://woodle.click"
        );

        boolean queued = sender.sendPollCreated(new PollCreatedEmail(
                UUID.fromString("00000000-0000-0000-0000-000000000111"),
                "Abc123XyZ789",
                "Alice",
                "alice@example.com",
                "Team lunch"
        ));

        assertTrue(queued);
        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesV2Client).sendEmail(requestCaptor.capture());
        SendEmailRequest request = requestCaptor.getValue();
        assertEquals("noreply@woodle.click", request.fromEmailAddress());
        assertEquals("alice@example.com", request.destination().toAddresses().getFirst());
        String subject = request.content().simple().subject().data();
        String body = request.content().simple().body().text().data();
        assertTrue(subject.contains("Umfrage erstellt"));
        assertTrue(subject.contains("Team lunch"));
        assertTrue(body.contains("https://woodle.click/poll/static/00000000-0000-0000-0000-000000000111"));
        assertTrue(body.contains("https://woodle.click/poll/static/00000000-0000-0000-0000-000000000111-Abc123XyZ789"));
    }

    @Test
    @DisplayName("returns false when SES throws runtime exception")
    void returnsFalseWhenSesThrowsRuntimeException() {
        SesV2Client sesV2Client = mock(SesV2Client.class);
        when(sesV2Client.sendEmail(any(SendEmailRequest.class))).thenThrow(new RuntimeException("boom"));
        SesPollEmailSender sender = new SesPollEmailSender(
                sesV2Client,
                "noreply@woodle.click",
                "",
                "https://woodle.click"
        );

        boolean queued = sender.sendPollCreated(new PollCreatedEmail(
                UUID.fromString("00000000-0000-0000-0000-000000000112"),
                "Abc123XyZ780",
                "Bob",
                "bob@example.com",
                "Team sync"
        ));

        assertFalse(queued);
    }

    @Test
    @DisplayName("uses relative links and no prefix when base URL is blank and subject prefix is null")
    void usesRelativeLinksAndNoPrefixWhenBaseUrlBlankAndSubjectPrefixNull() {
        SesV2Client sesV2Client = mock(SesV2Client.class);
        when(sesV2Client.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder().build());
        SesPollEmailSender sender = new SesPollEmailSender(
                sesV2Client,
                "noreply@woodle.click",
                null,
                ""
        );

        boolean queued = sender.sendPollCreated(new PollCreatedEmail(
                UUID.fromString("00000000-0000-0000-0000-000000000113"),
                "Secret113ABC",
                "Carla",
                "carla@example.com",
                "Board meeting"
        ));

        assertTrue(queued);
        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesV2Client).sendEmail(requestCaptor.capture());
        String subject = requestCaptor.getValue().content().simple().subject().data();
        String body = requestCaptor.getValue().content().simple().body().text().data();
        assertEquals("Umfrage erstellt: Board meeting", subject);
        assertTrue(body.contains("/poll/static/00000000-0000-0000-0000-000000000113"));
        assertTrue(body.contains("/poll/static/00000000-0000-0000-0000-000000000113-Secret113ABC"));
    }

    @Test
    @DisplayName("normalizes trailing slash in configured base URL")
    void normalizesTrailingSlashInConfiguredBaseUrl() {
        SesV2Client sesV2Client = mock(SesV2Client.class);
        when(sesV2Client.sendEmail(any(SendEmailRequest.class))).thenReturn(SendEmailResponse.builder().build());
        SesPollEmailSender sender = new SesPollEmailSender(
                sesV2Client,
                "noreply@woodle.click",
                "[Woodle]",
                "https://woodle.click/"
        );

        boolean queued = sender.sendPollCreated(new PollCreatedEmail(
                UUID.fromString("00000000-0000-0000-0000-000000000114"),
                "Secret114ABC",
                "Diana",
                "diana@example.com",
                "Lunch vote"
        ));

        assertTrue(queued);
        ArgumentCaptor<SendEmailRequest> requestCaptor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(sesV2Client).sendEmail(requestCaptor.capture());
        String body = requestCaptor.getValue().content().simple().body().text().data();
        assertTrue(body.contains("https://woodle.click/poll/static/00000000-0000-0000-0000-000000000114"));
        assertFalse(body.contains("https://woodle.click//poll/static/00000000-0000-0000-0000-000000000114"));
    }
}

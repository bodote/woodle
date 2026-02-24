package io.github.bodote.woodle.adapter.out.email;

import io.github.bodote.woodle.application.port.out.PollCreatedEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("SmtpPollEmailSender")
class SmtpPollEmailSenderTest {

    @Test
    @DisplayName("sends poll created email via SMTP and returns true")
    void sendsPollCreatedEmailViaSmtpAndReturnsTrue() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        SmtpPollEmailSender sender = new SmtpPollEmailSender(
                javaMailSender,
                "woodle@funknstein.de",
                "[Woodle]",
                "https://woodle.click"
        );

        boolean queued = sender.sendPollCreated(new PollCreatedEmail(
                UUID.fromString("00000000-0000-0000-0000-000000000211"),
                "Abc123XyZ789",
                "Alice",
                "alice@example.com",
                "Team lunch"
        ));

        assertTrue(queued);
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("woodle@funknstein.de", message.getFrom());
        assertEquals("alice@example.com", message.getTo()[0]);
        assertTrue(message.getSubject().contains("Umfrage erstellt"));
        assertTrue(message.getText().contains("https://woodle.click/poll/static/00000000-0000-0000-0000-000000000211"));
        assertTrue(message.getText().contains("https://woodle.click/poll/static/00000000-0000-0000-0000-000000000211-Abc123XyZ789"));
    }

    @Test
    @DisplayName("returns false when SMTP sender throws runtime exception")
    void returnsFalseWhenSmtpSenderThrowsRuntimeException() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        doThrow(new RuntimeException("smtp boom")).when(javaMailSender).send(org.mockito.ArgumentMatchers.any(SimpleMailMessage.class));
        SmtpPollEmailSender sender = new SmtpPollEmailSender(
                javaMailSender,
                "woodle@funknstein.de",
                "",
                "https://woodle.click"
        );

        boolean queued = sender.sendPollCreated(new PollCreatedEmail(
                UUID.fromString("00000000-0000-0000-0000-000000000212"),
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
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        SmtpPollEmailSender sender = new SmtpPollEmailSender(
                javaMailSender,
                "woodle@funknstein.de",
                null,
                ""
        );

        boolean queued = sender.sendPollCreated(new PollCreatedEmail(
                UUID.fromString("00000000-0000-0000-0000-000000000213"),
                "Secret113ABC",
                "Carla",
                "carla@example.com",
                "Board meeting"
        ));

        assertTrue(queued);
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("Umfrage erstellt: Board meeting", message.getSubject());
        assertTrue(message.getText().contains("/poll/static/00000000-0000-0000-0000-000000000213"));
        assertTrue(message.getText().contains("/poll/static/00000000-0000-0000-0000-000000000213-Secret113ABC"));
    }

    @Test
    @DisplayName("normalizes trailing slash in configured base URL")
    void normalizesTrailingSlashInConfiguredBaseUrl() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        SmtpPollEmailSender sender = new SmtpPollEmailSender(
                javaMailSender,
                "woodle@funknstein.de",
                "[Woodle]",
                "https://woodle.click/"
        );

        boolean queued = sender.sendPollCreated(new PollCreatedEmail(
                UUID.fromString("00000000-0000-0000-0000-000000000214"),
                "Secret114ABC",
                "Diana",
                "diana@example.com",
                "Lunch vote"
        ));

        assertTrue(queued);
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        String body = messageCaptor.getValue().getText();
        assertTrue(body.contains("https://woodle.click/poll/static/00000000-0000-0000-0000-000000000214"));
        assertFalse(body.contains("https://woodle.click//poll/static/00000000-0000-0000-0000-000000000214"));
    }

    @Test
    @DisplayName("uses relative links when base URL is null")
    void usesRelativeLinksWhenBaseUrlIsNull() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        SmtpPollEmailSender sender = new SmtpPollEmailSender(
                javaMailSender,
                "woodle@funknstein.de",
                "[Woodle]",
                null
        );

        boolean queued = sender.sendPollCreated(new PollCreatedEmail(
                UUID.fromString("00000000-0000-0000-0000-000000000215"),
                "Secret115ABC",
                "Eve",
                "eve@example.com",
                "Sprint planning"
        ));

        assertTrue(queued);
        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender).send(messageCaptor.capture());
        String body = messageCaptor.getValue().getText();
        assertTrue(body.contains("/poll/static/00000000-0000-0000-0000-000000000215"));
        assertFalse(body.contains("https://"));
    }
}

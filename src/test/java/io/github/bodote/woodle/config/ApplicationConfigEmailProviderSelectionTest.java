package io.github.bodote.woodle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mail.javamail.JavaMailSender;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ApplicationConfig email provider selection")
class ApplicationConfigEmailProviderSelectionTest {

    private final ApplicationConfig applicationConfig = new ApplicationConfig();

    @Test
    @DisplayName("creates SMTP sender when provider is smtp")
    void createsSmtpSenderWhenProviderIsSmtp() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of(
                        "woodle.email.enabled", "true",
                        "woodle.email.provider", "smtp",
                        "woodle.email.from", "woodle@funknstein.de",
                        "woodle.email.smtp.host", "smtp.ionos.de",
                        "woodle.email.smtp.port", "587",
                        "woodle.email.smtp.username", "woodle@funknstein.de",
                        "woodle.email.smtp.password", "dummy"
                ))
        );
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(SesV2Client.class, () -> mock(SesV2Client.class));
        context.register(ApplicationConfig.class);

        context.refresh();

        PollEmailSender pollEmailSender = context.getBean(PollEmailSender.class);
        assertTrue(
                pollEmailSender.getClass().getName().contains("SmtpPollEmailSender"),
                "Expected SMTP sender when woodle.email.provider=smtp"
        );
        context.close();
    }

    @Test
    @DisplayName("uses SES sender when provider is null")
    void usesSesSenderWhenProviderIsNull() {
        ObjectProvider<SesV2Client> sesProvider = mock(ObjectProvider.class);
        ObjectProvider<JavaMailSender> smtpProvider = mock(ObjectProvider.class);
        when(sesProvider.getIfAvailable()).thenReturn(mock(SesV2Client.class));

        PollEmailSender sender = applicationConfig.pollEmailSender(
                true,
                null,
                "no-reply@woodle.click",
                "[Woodle]",
                "https://woodle.click",
                sesProvider,
                smtpProvider
        );

        assertTrue(sender.getClass().getName().contains("SesPollEmailSender"));
    }

    @Test
    @DisplayName("fails fast when SMTP provider has no JavaMailSender")
    void failsFastWhenSmtpProviderHasNoJavaMailSender() {
        ObjectProvider<SesV2Client> sesProvider = mock(ObjectProvider.class);
        ObjectProvider<JavaMailSender> smtpProvider = mock(ObjectProvider.class);
        when(smtpProvider.getIfAvailable()).thenReturn(null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> applicationConfig.pollEmailSender(
                        true,
                        "smtp",
                        "woodle@funknstein.de",
                        "[Woodle]",
                        "https://woodle.click",
                        sesProvider,
                        smtpProvider
                )
        );

        assertEquals("Email provider smtp is enabled but no JavaMailSender bean is available", exception.getMessage());
    }

    @Test
    @DisplayName("fails fast when SES provider has no SesV2Client")
    void failsFastWhenSesProviderHasNoSesClient() {
        ObjectProvider<SesV2Client> sesProvider = mock(ObjectProvider.class);
        ObjectProvider<JavaMailSender> smtpProvider = mock(ObjectProvider.class);
        when(sesProvider.getIfAvailable()).thenReturn(null);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> applicationConfig.pollEmailSender(
                        true,
                        "ses",
                        "no-reply@woodle.click",
                        "[Woodle]",
                        "https://woodle.click",
                        sesProvider,
                        smtpProvider
                )
        );

        assertEquals("Email provider ses is enabled but no SesV2Client bean is available", exception.getMessage());
    }

    @Test
    @DisplayName("fails fast for unsupported provider")
    void failsFastForUnsupportedProvider() {
        ObjectProvider<SesV2Client> sesProvider = mock(ObjectProvider.class);
        ObjectProvider<JavaMailSender> smtpProvider = mock(ObjectProvider.class);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> applicationConfig.pollEmailSender(
                        true,
                        "invalid-provider",
                        "no-reply@woodle.click",
                        "[Woodle]",
                        "https://woodle.click",
                        sesProvider,
                        smtpProvider
                )
        );

        assertEquals("Unsupported email provider: invalid-provider", exception.getMessage());
    }

    @Test
    @DisplayName("fails fast when smtp host is blank")
    void failsFastWhenSmtpHostIsBlank() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> applicationConfig.javaMailSender("", 587, "woodle@funknstein.de", "geheim")
        );

        assertEquals("Email provider smtp requires property woodle.email.smtp.host", exception.getMessage());
    }

    @Test
    @DisplayName("fails fast when smtp username is blank")
    void failsFastWhenSmtpUsernameIsBlank() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> applicationConfig.javaMailSender("smtp.ionos.de", 587, "", "geheim")
        );

        assertEquals("Email provider smtp requires property woodle.email.smtp.username", exception.getMessage());
    }

    @Test
    @DisplayName("fails fast when smtp password is blank")
    void failsFastWhenSmtpPasswordIsBlank() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> applicationConfig.javaMailSender("smtp.ionos.de", 587, "woodle@funknstein.de", "")
        );

        assertEquals("Email provider smtp requires property woodle.email.smtp.password", exception.getMessage());
    }
}

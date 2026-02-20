package io.github.bodote.woodle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("ApplicationConfig email provider selection")
class ApplicationConfigEmailProviderSelectionTest {

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
}

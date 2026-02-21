package io.github.bodote.woodle.adapter.out.email;

import io.github.bodote.woodle.application.port.out.PollCreatedEmail;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpPollEmailSender implements PollEmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpPollEmailSender.class);

    private final JavaMailSender javaMailSender;
    private final String fromAddress;
    private final String subjectPrefix;
    private final String publicBaseUrl;

    public SmtpPollEmailSender(JavaMailSender javaMailSender,
                               String fromAddress,
                               String subjectPrefix,
                               String publicBaseUrl) {
        this.javaMailSender = javaMailSender;
        this.fromAddress = fromAddress;
        this.subjectPrefix = subjectPrefix == null ? "" : subjectPrefix.trim();
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    @Override
    public boolean sendPollCreated(PollCreatedEmail pollCreatedEmail) {
        String pollId = pollCreatedEmail.pollId().toString();
        String adminUrl = absoluteUrl("/poll/" + pollId + "-" + pollCreatedEmail.adminSecret());
        String voteUrl = absoluteUrl("/poll/" + pollId);

        String subject = subjectPrefix.isBlank()
                ? "Umfrage erstellt: " + pollCreatedEmail.pollTitle()
                : subjectPrefix + " Umfrage erstellt: " + pollCreatedEmail.pollTitle();
        String body = "Hello " + pollCreatedEmail.authorName() + ",\n\n"
                + "your poll \"" + pollCreatedEmail.pollTitle() + "\" has been created successfully.\n\n"
                + "Admin URL:\n"
                + adminUrl + "\n\n"
                + "Vote URL:\n"
                + voteUrl + "\n";

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(pollCreatedEmail.authorEmail());
        message.setSubject(subject);
        message.setText(body);

        try {
            javaMailSender.send(message);
            return true;
        } catch (RuntimeException ex) {
            LOGGER.warn("Failed to send poll created email for poll {}", pollId, ex);
            return false;
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        String trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private String absoluteUrl(String path) {
        if (publicBaseUrl.isBlank()) {
            return path;
        }
        return publicBaseUrl + path;
    }
}

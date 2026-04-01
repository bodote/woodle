package io.github.bodote.woodle.adapter.out.email;

import io.github.bodote.woodle.application.port.out.PollCreatedEmail;
import io.github.bodote.woodle.application.port.out.PollEmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

public class SesPollEmailSender implements PollEmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(SesPollEmailSender.class);

    private final SesV2Client sesV2Client;
    private final String fromAddress;
    private final String subjectPrefix;
    private final String publicBaseUrl;

    public SesPollEmailSender(SesV2Client sesV2Client,
                              String fromAddress,
                              String subjectPrefix,
                              String publicBaseUrl) {
        this.sesV2Client = sesV2Client;
        this.fromAddress = fromAddress;
        this.subjectPrefix = subjectPrefix == null ? "" : subjectPrefix.trim();
        this.publicBaseUrl = normalizeBaseUrl(publicBaseUrl);
    }

    @Override
    public boolean sendPollCreated(PollCreatedEmail pollCreatedEmail) {
        String pollId = pollCreatedEmail.pollId().toString();
        String adminUrl = absoluteUrl("/poll/static/" + pollId + "-" + pollCreatedEmail.adminSecret());
        String voteUrl = absoluteUrl("/poll/static/" + pollId);

        String subject = subjectPrefix.isBlank()
                ? "Umfrage erstellt: " + pollCreatedEmail.pollTitle()
                : subjectPrefix + " Umfrage erstellt: " + pollCreatedEmail.pollTitle();
        String body = "Hello " + pollCreatedEmail.authorName() + ",\n\n"
                + "your poll \"" + pollCreatedEmail.pollTitle() + "\" has been created successfully.\n\n"
                + "Admin URL:\n"
                + adminUrl + "\n\n"
                + "Vote URL:\n"
                + voteUrl + "\n";

        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(fromAddress)
                .destination(Destination.builder().toAddresses(pollCreatedEmail.authorEmail()).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().data(subject).build())
                                .body(Body.builder()
                                        .text(Content.builder().data(body).build())
                                        .build())
                                .build())
                        .build())
                .build();

        try {
            sesV2Client.sendEmail(request);
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

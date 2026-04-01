package io.github.bodote.woodle.application.port.out;

public interface PollEmailSender {
    boolean sendPollCreated(PollCreatedEmail pollCreatedEmail);
}

package io.github.bodote.woodle.adapter.out.email;

import io.github.bodote.woodle.application.port.out.PollCreatedEmail;
import io.github.bodote.woodle.application.port.out.PollEmailSender;

public class NoopPollEmailSender implements PollEmailSender {

    @Override
    public boolean sendPollCreated(PollCreatedEmail pollCreatedEmail) {
        return false;
    }
}

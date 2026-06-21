package io.github.bodote.woodle.application.port.in;

public interface CleanupExpiredPollsUseCase {

    /**
     * Deletes all polls whose {@code expiresAt} date has passed.
     *
     * @return the number of polls deleted
     */
    int cleanupExpiredPolls();
}

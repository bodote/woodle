package io.github.bodote.woodle.application.port.out;

import io.github.bodote.woodle.adapter.in.web.WizardState;

import java.util.Optional;
import java.util.UUID;

public interface WizardStateRepository {

    UUID create(WizardState state);

    void save(UUID draftId, WizardState state);

    Optional<WizardState> findById(UUID draftId);

    void delete(UUID draftId);
}

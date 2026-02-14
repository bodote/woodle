package io.github.bodote.woodle.adapter.out.persistence;

import io.github.bodote.woodle.adapter.in.web.WizardState;
import io.github.bodote.woodle.application.port.out.WizardStateRepository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryWizardStateRepository implements WizardStateRepository {

    private final ConcurrentMap<UUID, WizardState> storage = new ConcurrentHashMap<>();

    @Override
    public UUID create(WizardState state) {
        UUID draftId = UUID.randomUUID();
        storage.put(draftId, WizardState.copyOf(state));
        return draftId;
    }

    @Override
    public void save(UUID draftId, WizardState state) {
        storage.put(draftId, WizardState.copyOf(state));
    }

    @Override
    public Optional<WizardState> findById(UUID draftId) {
        WizardState state = storage.get(draftId);
        return state == null ? Optional.empty() : Optional.of(WizardState.copyOf(state));
    }

    @Override
    public void delete(UUID draftId) {
        storage.remove(draftId);
    }
}

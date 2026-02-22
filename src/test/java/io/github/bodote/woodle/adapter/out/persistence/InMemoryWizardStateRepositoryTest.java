package io.github.bodote.woodle.adapter.out.persistence;

import io.github.bodote.woodle.application.model.WizardState;
import io.github.bodote.woodle.domain.model.EventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("InMemoryWizardStateRepository")
class InMemoryWizardStateRepositoryTest {

    @Test
    @DisplayName("creates, loads, saves and deletes draft state")
    void createsLoadsSavesAndDeletesDraftState() {
        InMemoryWizardStateRepository repository = new InMemoryWizardStateRepository();
        WizardState initial = new WizardState();
        initial.setAuthorName("Alice");
        initial.setAuthorEmail("alice@example.com");
        initial.setTitle("Kickoff");
        initial.setEventType(EventType.ALL_DAY);
        initial.setDates(List.of(LocalDate.of(2026, 2, 20)));

        UUID draftId = repository.create(initial);
        Optional<WizardState> loaded = repository.findById(draftId);
        assertTrue(loaded.isPresent());
        assertEquals("Alice", loaded.orElseThrow().authorName());

        WizardState updated = loaded.orElseThrow();
        updated.setTitle("Kickoff Updated");
        repository.save(draftId, updated);

        Optional<WizardState> reloaded = repository.findById(draftId);
        assertTrue(reloaded.isPresent());
        assertEquals("Kickoff Updated", reloaded.orElseThrow().title());

        repository.delete(draftId);
        assertTrue(repository.findById(draftId).isEmpty());
    }
}

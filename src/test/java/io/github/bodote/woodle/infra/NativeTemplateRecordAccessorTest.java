package io.github.bodote.woodle.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.bodote.woodle.domain.model.Poll;
import io.github.bodote.woodle.domain.model.PollOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Native template record accessor compatibility")
class NativeTemplateRecordAccessorTest {

    @Test
    @DisplayName("uses bean-style property expressions in poll templates")
    void usesBeanStylePropertyExpressionsInPollTemplates() throws IOException {
        String pollViewTemplate = Files.readString(Path.of("src/main/resources/templates/poll/view.html"));
        String optionsListTemplate = Files.readString(Path.of("src/main/resources/templates/poll/options-list.html"));
        String participantRowTemplate = Files.readString(Path.of("src/main/resources/templates/poll/participant-row.html"));
        String participantRowEditTemplate = Files.readString(Path.of("src/main/resources/templates/poll/participant-row-edit.html"));

        assertTrue(pollViewTemplate.contains("${poll.title}"), "Expected bean-style property poll.title in poll view");
        assertTrue(
                pollViewTemplate.contains("${poll.description}"),
                "Expected bean-style property poll.description in poll view");
        assertTrue(
                optionsListTemplate.contains("${poll.options}"),
                "Expected bean-style property poll.options in options list");
        assertTrue(
                participantRowTemplate.contains("${row.responseId}"),
                "Expected bean-style property row.responseId in participant row");
        assertTrue(
                participantRowEditTemplate.contains("${editRow.responseId}"),
                "Expected bean-style property editRow.responseId in editable participant row");
    }

    @Test
    @DisplayName("provides bean-style getters on record-backed template models")
    void providesBeanStyleGettersOnTemplateModels() throws NoSuchMethodException {
        assertNotNull(Poll.class.getMethod("getTitle"));
        assertNotNull(Poll.class.getMethod("getDescription"));
        assertNotNull(Poll.class.getMethod("getOptions"));
        assertNotNull(PollOption.class.getMethod("getOptionId"));
        assertNotNull(PollOption.class.getMethod("getDate"));
        assertNotNull(PollOption.class.getMethod("getStartTime"));
    }

    @Test
    @DisplayName("registers runtime hints for thymeleaf models in native mode")
    void registersRuntimeHintsForNativeThymeleafEvaluation() throws IOException {
        String appClass = Files.readString(Path.of("src/main/java/io/github/bodote/woodle/WoodleApplication.java"));
        String hintsClass = Files.readString(Path.of("src/main/java/io/github/bodote/woodle/config/ThymeleafRuntimeHints.java"));

        assertTrue(
                appClass.contains("@ImportRuntimeHints(ThymeleafRuntimeHints.class)"),
                "Expected WoodleApplication to import ThymeleafRuntimeHints");
        assertTrue(
                hintsClass.contains("io.github.bodote.woodle.domain.model.Poll"),
                "Expected Poll type to be registered for reflection hints");
        assertTrue(
                hintsClass.contains("PollViewController$ParticipantRow"),
                "Expected participant row model to be registered for reflection hints");
    }

    @Test
    @DisplayName("avoids enum static field references in editable participant row template")
    void avoidsEnumStaticFieldReferencesInEditableParticipantRowTemplate() throws IOException {
        String participantRowEditTemplate =
                Files.readString(Path.of("src/main/resources/templates/poll/participant-row-edit.html"));

        assertTrue(
                !participantRowEditTemplate.contains("T(io.github.bodote.woodle.domain.model.PollVoteValue).YES"),
                "Expected template to avoid enum static field reference for YES");
        assertTrue(
                !participantRowEditTemplate.contains("T(io.github.bodote.woodle.domain.model.PollVoteValue).IF_NEEDED"),
                "Expected template to avoid enum static field reference for IF_NEEDED");
        assertTrue(
                !participantRowEditTemplate.contains("T(io.github.bodote.woodle.domain.model.PollVoteValue).NO"),
                "Expected template to avoid enum static field reference for NO");
    }
}

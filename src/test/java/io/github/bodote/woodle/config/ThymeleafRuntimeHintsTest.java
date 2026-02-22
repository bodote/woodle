package io.github.bodote.woodle.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ThymeleafRuntimeHints")
class ThymeleafRuntimeHintsTest {

    @Test
    @DisplayName("registers reflection hints for thymeleaf model types")
    void registersReflectionHintsForThymeleafModelTypes() {
        RuntimeHints hints = new RuntimeHints();
        ThymeleafRuntimeHints registrar = new ThymeleafRuntimeHints();

        registrar.registerHints(hints, getClass().getClassLoader());

        assertTrue(hints.reflection().typeHints().count() >= 20);
        var pollHint = hints.reflection().getTypeHint(TypeReference.of("io.github.bodote.woodle.domain.model.Poll"));
        assertNotNull(pollHint);
        assertTrue(pollHint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertTrue(pollHint.getMemberCategories().contains(MemberCategory.DECLARED_FIELDS));

        var wizardDraftHint = hints.reflection().getTypeHint(
                TypeReference.of("io.github.bodote.woodle.adapter.out.persistence.S3WizardStateRepository$WizardStateDocument"));
        assertNotNull(wizardDraftHint);
        assertTrue(wizardDraftHint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS));
        assertTrue(wizardDraftHint.getMemberCategories().contains(MemberCategory.DECLARED_FIELDS));

        var dateGroupHint = hints.reflection().getTypeHint(
                TypeReference.of("io.github.bodote.woodle.adapter.in.web.PollViewController$DateGroup"));
        assertNotNull(dateGroupHint);
        assertTrue(dateGroupHint.getMemberCategories().contains(MemberCategory.INVOKE_DECLARED_METHODS));
    }
}

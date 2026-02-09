package io.github.bodote.woodle.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class ThymeleafRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        registerType(hints, "io.github.bodote.woodle.domain.model.Poll");
        registerType(hints, "io.github.bodote.woodle.domain.model.PollOption");
        registerType(hints, "io.github.bodote.woodle.adapter.in.web.PollViewController$MonthGroup");
        registerType(hints, "io.github.bodote.woodle.adapter.in.web.PollViewController$OptionHeader");
        registerType(hints, "io.github.bodote.woodle.adapter.in.web.PollViewController$ParticipantRow");
        registerType(hints, "io.github.bodote.woodle.adapter.in.web.PollViewController$EditableRow");
        registerType(hints, "io.github.bodote.woodle.adapter.in.web.PollViewController$VoteCell");
        registerType(hints, "io.github.bodote.woodle.adapter.in.web.PollViewController$SummaryCell");
        registerType(hints, "io.github.bodote.woodle.adapter.in.web.PollVoteController$ParticipantRow");
        registerType(hints, "io.github.bodote.woodle.adapter.in.web.PollVoteController$VoteCell");
    }

    private void registerType(RuntimeHints hints, String typeName) {
        hints.reflection().registerType(
                TypeReference.of(typeName),
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS,
                MemberCategory.PUBLIC_FIELDS);
    }
}

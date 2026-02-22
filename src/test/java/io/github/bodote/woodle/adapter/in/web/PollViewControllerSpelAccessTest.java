package io.github.bodote.woodle.adapter.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PollViewController SpEL access")
class PollViewControllerSpelAccessTest {

    @Test
    @DisplayName("exposes DateGroup as public type for template property introspection")
    void exposesDateGroupAsPublicTypeForTemplatePropertyIntrospection() throws Exception {
        Class<?> dateGroupClass = Class.forName(
                "io.github.bodote.woodle.adapter.in.web.PollViewController$DateGroup"
        );
        assertTrue(
                Modifier.isPublic(dateGroupClass.getModifiers()),
                "DateGroup must be public to avoid template property lookup failures in native runtime"
        );
    }

    @Test
    @DisplayName("resolves DateGroup span via SpEL property access")
    void resolvesDateGroupSpanViaSpelPropertyAccess() throws Exception {
        Class<?> dateGroupClass = Class.forName(
                "io.github.bodote.woodle.adapter.in.web.PollViewController$DateGroup"
        );
        Constructor<?> constructor = dateGroupClass.getDeclaredConstructor(String.class, int.class, int.class);
        constructor.setAccessible(true);
        Object dateGroup = constructor.newInstance("So., 22.02.", 0, 2);

        ExpressionParser parser = new SpelExpressionParser();
        StandardEvaluationContext context = new StandardEvaluationContext(dateGroup);

        Integer span = parser.parseExpression("span").getValue(context, Integer.class);

        assertEquals(2, span);
    }
}

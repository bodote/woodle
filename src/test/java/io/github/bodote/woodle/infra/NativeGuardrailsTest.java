package io.github.bodote.woodle.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Native guardrails")
class NativeGuardrailsTest {

    private static final Path TEMPLATES_ROOT = Path.of("src/main/resources/templates");
    private static final Path CONFIG_ROOT = Path.of("src/main/java/io/github/bodote/woodle/config");
    private static final Pattern TEMPLATE_LISTS_SIZE_PATTERN = Pattern.compile("#lists\\.size\\(");
    private static final Pattern TEMPLATE_DOT_SIZE_PATTERN = Pattern.compile("\\.size\\(\\)");
    private static final Pattern BEAN_METHOD_PATTERN = Pattern.compile(
            "@Bean(?:\\R\\s*@[^\n\r]+)*\\R\\s*(?:public|protected|private)?\\s+[\\w$<>,\\[\\]\\s?]+\\s+(\\w+)\\s*\\(");

    @Test
    @DisplayName("forbids native-fragile size calls in thymeleaf templates")
    void forbidsNativeFragileSizeCallsInThymeleafTemplates() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(TEMPLATES_ROOT)) {
            files.filter(path -> path.toString().endsWith(".html"))
                    .forEach(path -> scanTemplateForForbiddenSizeCalls(path, violations));
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Found native-fragile Thymeleaf expressions:\n" + String.join("\n", violations));
    }

    @Test
    @DisplayName("forbids bean-to-bean self invocation in config classes")
    void forbidsBeanToBeanSelfInvocationInConfigClasses() throws IOException {
        List<String> violations = new ArrayList<>();
        try (var files = Files.walk(CONFIG_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> scanConfigForBeanSelfInvocation(path, violations));
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Found @Bean self-invocation patterns that are brittle in native mode:\n"
                        + String.join("\n", violations));
    }

    private static void scanTemplateForForbiddenSizeCalls(Path path, List<String> violations) {
        String content = readFile(path);
        addPatternViolations(path, content, TEMPLATE_LISTS_SIZE_PATTERN, "Forbidden #lists.size(...) usage", violations);
        addPatternViolations(path, content, TEMPLATE_DOT_SIZE_PATTERN, "Forbidden .size() usage in template expression", violations);
    }

    private static void scanConfigForBeanSelfInvocation(Path path, List<String> violations) {
        String content = readFile(path);
        List<BeanMethod> beanMethods = findBeanMethods(content);
        for (BeanMethod current : beanMethods) {
            String body = methodBody(content, current);
            for (BeanMethod candidate : beanMethods) {
                if (current.name().equals(candidate.name())) {
                    continue;
                }
                Pattern invocationPattern = Pattern.compile("\\b" + Pattern.quote(candidate.name()) + "\\s*\\(");
                Matcher invocationMatcher = invocationPattern.matcher(body);
                if (invocationMatcher.find()) {
                    int line = lineNumber(content, current.signatureStartIndex() + invocationMatcher.start());
                    violations.add(path + ":" + line + " calls @Bean method '" + candidate.name()
                            + "()' from @Bean method '" + current.name() + "()'");
                }
            }
        }
    }

    private static List<BeanMethod> findBeanMethods(String content) {
        List<BeanMethod> beanMethods = new ArrayList<>();
        Matcher matcher = BEAN_METHOD_PATTERN.matcher(content);
        while (matcher.find()) {
            beanMethods.add(new BeanMethod(matcher.group(1), matcher.start(), matcher.end()));
        }
        return beanMethods;
    }

    private static String methodBody(String content, BeanMethod method) {
        int openBraceIndex = content.indexOf('{', method.signatureEndIndex());
        if (openBraceIndex < 0) {
            return "";
        }

        int depth = 0;
        for (int i = openBraceIndex; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return content.substring(openBraceIndex, i + 1);
                }
            }
        }
        return content.substring(openBraceIndex);
    }

    private static void addPatternViolations(
            Path path,
            String content,
            Pattern pattern,
            String message,
            List<String> violations) {
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            int line = lineNumber(content, matcher.start());
            violations.add(path + ":" + line + " " + message);
        }
    }

    private static int lineNumber(String content, int index) {
        int line = 1;
        for (int i = 0; i < index && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String readFile(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file: " + path, e);
        }
    }

    private record BeanMethod(String name, int signatureStartIndex, int signatureEndIndex) {}
}

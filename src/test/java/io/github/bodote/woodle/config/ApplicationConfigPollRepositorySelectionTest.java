package io.github.bodote.woodle.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@DisplayName("ApplicationConfig poll repository selection")
class ApplicationConfigPollRepositorySelectionTest {

    @Test
    @DisplayName("fails fast when S3 is enabled but no S3 client is available")
    void failsFastWhenS3IsEnabledButNoS3ClientIsAvailable() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of(
                        "woodle.s3.enabled", "true",
                        "woodle.s3.bucket", "woodle-test"
                ))
        );
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.register(ApplicationConfig.class);

        Exception exception = assertThrows(Exception.class, context::refresh);

        assertTrue(
                containsMessage(exception, "S3 is enabled but no S3 client bean is available"),
                "Expected explicit startup failure instead of silent in-memory fallback"
        );
    }

    @Test
    @DisplayName("creates S3 repository when S3 is enabled and S3 client is present")
    void createsS3RepositoryWhenS3IsEnabledAndS3ClientIsPresent() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.of(
                        "woodle.s3.enabled", "true",
                        "woodle.s3.bucket", "woodle-test"
                ))
        );
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(S3Client.class, () -> mock(S3Client.class));
        context.register(ApplicationConfig.class);

        context.refresh();

        Object pollRepository = context.getBean(io.github.bodote.woodle.application.port.out.PollRepository.class);
        assertTrue(
                pollRepository.getClass().getName().contains("S3PollRepository"),
                "Expected S3-backed repository when S3 is enabled"
        );
        context.close();
    }

    private boolean containsMessage(Throwable throwable, String expected) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(expected)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

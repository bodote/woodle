package io.github.bodote.woodle.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

@DisplayName("S3Config credentials provider selection")
class S3ConfigCredentialsTest {

    @Test
    @DisplayName("uses default credentials provider when configured keys are dummy")
    void usesDefaultProviderForDummyCredentials() throws Exception {
        S3Config config = new S3Config();

        Method method = S3Config.class.getDeclaredMethod(
                "resolveCredentialsProvider", String.class, String.class);
        method.setAccessible(true);

        AwsCredentialsProvider provider =
                (AwsCredentialsProvider) method.invoke(config, "dummy", "dummy");

        assertEquals(DefaultCredentialsProvider.class, provider.getClass());
    }

    @Test
    @DisplayName("uses static credentials provider when explicit keys are configured")
    void usesStaticProviderForExplicitCredentials() throws Exception {
        S3Config config = new S3Config();

        Method method = S3Config.class.getDeclaredMethod(
                "resolveCredentialsProvider", String.class, String.class);
        method.setAccessible(true);

        AwsCredentialsProvider provider =
                (AwsCredentialsProvider) method.invoke(config, "access", "secret");

        assertEquals(StaticCredentialsProvider.class, provider.getClass());
    }

    @Test
    @DisplayName("uses default credentials provider for blank keys")
    void usesDefaultProviderForBlankCredentials() throws Exception {
        S3Config config = new S3Config();

        Method method = S3Config.class.getDeclaredMethod(
                "resolveCredentialsProvider", String.class, String.class);
        method.setAccessible(true);

        AwsCredentialsProvider provider =
                (AwsCredentialsProvider) method.invoke(config, " ", "");

        assertEquals(DefaultCredentialsProvider.class, provider.getClass());
    }

    @Test
    @DisplayName("uses default credentials provider for null keys")
    void usesDefaultProviderForNullCredentials() throws Exception {
        S3Config config = new S3Config();

        Method method = S3Config.class.getDeclaredMethod(
                "resolveCredentialsProvider", String.class, String.class);
        method.setAccessible(true);

        AwsCredentialsProvider provider =
                (AwsCredentialsProvider) method.invoke(config, new Object[]{null, null});

        assertEquals(DefaultCredentialsProvider.class, provider.getClass());
    }
}

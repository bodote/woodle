package io.github.bodote.woodle.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Poll bucket IAM permissions")
class PollBucketListPermissionTest {

    @Test
    @DisplayName("grants list-bucket permission for active poll counting")
    void grantsListBucketPermissionForActivePollCounting() throws IOException {
        String template = Files.readString(Path.of("infra/template.yaml"));

        assertTrue(
                template.contains("- s3:ListBucket"),
                "Expected Lambda policy to grant s3:ListBucket for /poll/active-count");
        assertTrue(
                template.contains("Resource: !GetAtt PollsBucket.Arn"),
                "Expected bucket-level ARN resource for s3:ListBucket permission");
        assertTrue(
                template.contains("${PollsBucket.Arn}/drafts/*"),
                "Expected object-level access for persisted wizard drafts");
    }
}

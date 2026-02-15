package io.github.bodote.woodle.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SES email infrastructure")
class SesEmailInfrastructureTest {

    @Test
    @DisplayName("configures Lambda email environment and SES send permission")
    void configuresLambdaEmailEnvironmentAndSesSendPermission() throws IOException {
        String template = Files.readString(Path.of("infra/template.yaml"));

        assertTrue(template.contains("EmailEnabled:"), "Expected EmailEnabled stack parameter");
        assertTrue(template.contains("EmailFromAddress:"), "Expected EmailFromAddress stack parameter");
        assertTrue(template.contains("WOODLE_EMAIL_ENABLED"), "Expected Lambda WOODLE_EMAIL_ENABLED env var");
        assertTrue(template.contains("WOODLE_EMAIL_FROM"), "Expected Lambda WOODLE_EMAIL_FROM env var");
        assertTrue(template.contains("ses:SendEmail"), "Expected SES send permission in Lambda IAM policy");
    }

    @Test
    @DisplayName("restricts SES send permission to configured from address")
    void restrictsSesSendPermissionToConfiguredFromAddress() throws IOException {
        String template = Files.readString(Path.of("infra/template.yaml"));

        assertTrue(
                template.contains("Resource: \"*\""),
                "Expected SES policy resource to allow SES identities required by sandbox");
        assertTrue(
                template.contains("ses:FromAddress: !Ref EmailFromAddress"),
                "Expected SES policy to restrict allowed sender via ses:FromAddress condition");
    }

    @Test
    @DisplayName("enables email by default for deployed stages")
    void enablesEmailByDefaultForDeployedStages() throws IOException {
        String template = Files.readString(Path.of("infra/template.yaml"));
        String deployScript = Files.readString(Path.of("aws-deploy.sh"));

        assertTrue(
                template.contains("Default: \"true\""),
                "Expected EmailEnabled CloudFormation default to be true");
        assertTrue(
                deployScript.contains("WOODLE_EMAIL_ENABLED=\"${WOODLE_EMAIL_ENABLED:-true}\""),
                "Expected deploy script email default to be enabled");
    }
}

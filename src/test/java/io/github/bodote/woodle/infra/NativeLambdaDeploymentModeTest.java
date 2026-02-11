package io.github.bodote.woodle.infra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AWS deployment runtime modes")
class NativeLambdaDeploymentModeTest {

    @Test
    @DisplayName("supports choosing jvm or native runtime in deployment script")
    void supportsJvmAndNativeDeploymentModes() throws IOException {
        String deployScript = Files.readString(Path.of("aws-deploy.sh"));

        assertTrue(
                deployScript.contains("DEPLOY_RUNTIME=\"${DEPLOY_RUNTIME:-jvm}\""),
                "Expected deployment script to default to DEPLOY_RUNTIME=jvm");
        assertTrue(
                deployScript.contains("if [[ \"${DEPLOY_RUNTIME}\" == \"native\" ]]; then"),
                "Expected deployment script branch for DEPLOY_RUNTIME=native");
        assertTrue(
                deployScript.contains("LAMBDA_DOCKERFILE=\"${LAMBDA_DOCKERFILE_NATIVE}\""),
                "Expected deployment script to switch to a native Dockerfile for DEPLOY_RUNTIME=native");
    }

    @Test
    @DisplayName("runs native deployment preflight checks for dockerfile and buildx")
    void runsNativeDeploymentPreflightChecks() throws IOException {
        String deployScript = Files.readString(Path.of("aws-deploy.sh"));

        assertTrue(
                deployScript.contains("Running native deployment preflight checks..."),
                "Expected native preflight log section in deployment script");
        assertTrue(
                deployScript.contains("Native Dockerfile not found"),
                "Expected native preflight to validate Dockerfile presence");
        assertTrue(
                deployScript.contains("Docker buildx is required for native lambda image publishing."),
                "Expected native preflight to validate docker buildx support");
    }

    @Test
    @DisplayName("supports dry-run mode for deployment validation without AWS changes")
    void supportsDryRunMode() throws IOException {
        String deployScript = Files.readString(Path.of("aws-deploy.sh"));

        assertTrue(
                deployScript.contains("DRY_RUN=\"${DRY_RUN:-false}\""),
                "Expected deployment script to expose DRY_RUN toggle");
        assertTrue(
                deployScript.contains("Dry-run enabled. Stopping before AWS and Docker operations."),
                "Expected deployment script to stop early in dry-run mode");
    }

    @Test
    @DisplayName("defaults to qs stage domain and supports -prod switch for production domain")
    void defaultsToQsDomainAndSupportsProdSwitch() throws IOException {
        String deployScript = Files.readString(Path.of("aws-deploy.sh"));

        assertTrue(
                deployScript.contains("DEPLOY_STAGE=\"qs\""),
                "Expected deployment script to default to qs stage");
        assertTrue(
                deployScript.contains("if [[ \"${1:-}\" == \"-prod\" ]]; then"),
                "Expected deployment script to support -prod argument");
        assertTrue(
                deployScript.contains("APP_DOMAIN_NAME=\"${APP_DOMAIN_NAME:-${DEFAULT_FRONTEND_DOMAIN}}\""),
                "Expected deployment script to default APP_DOMAIN_NAME based on selected stage");
    }

    @Test
    @DisplayName("fails fast when custom CloudFront alias is already bound to another distribution")
    void failsFastWhenCustomAliasIsAlreadyBoundElsewhere() throws IOException {
        String deployScript = Files.readString(Path.of("aws-deploy.sh"));

        assertTrue(
                deployScript.contains("Validating CloudFront custom domain alias ownership..."),
                "Expected deployment script to validate alias ownership before stack deploy");
        assertTrue(
                deployScript.contains("ALIAS_OWNER_DISTRIBUTION_ID"),
                "Expected deployment script to resolve the owning CloudFront distribution id for alias");
        assertTrue(
                deployScript.contains("is already associated with distribution"),
                "Expected deployment script to fail with a clear alias-collision message");
    }

    @Test
    @DisplayName("waits for ACM DNS validation record availability in domain wiring script")
    void waitsForAcmDnsValidationRecordAvailability() throws IOException {
        String wiringScript = Files.readString(Path.of("wire-domains.sh"));

        assertTrue(
                wiringScript.contains("Waiting for ACM DNS validation record to become available"),
                "Expected domain wiring script to wait for ACM validation record propagation");
        assertTrue(
                wiringScript.contains("for attempt in {1..20}; do"),
                "Expected domain wiring script to retry ACM validation record lookup");
    }

    @Test
    @DisplayName("defaults domain wiring to qs stage and supports -prod switch")
    void defaultsDomainWiringToQsStageAndSupportsProdSwitch() throws IOException {
        String wiringScript = Files.readString(Path.of("wire-domains.sh"));

        assertTrue(
                wiringScript.contains("DEPLOY_STAGE=\"qs\""),
                "Expected domain wiring script to default to qs stage");
        assertTrue(
                wiringScript.contains("if [[ \"${1:-}\" == \"-prod\" ]]; then"),
                "Expected domain wiring script to support -prod argument");
        assertTrue(
                wiringScript.contains("ROOT_DOMAIN=\"${ROOT_DOMAIN:-${DEFAULT_FRONTEND_DOMAIN}}\""),
                "Expected domain wiring script to default ROOT_DOMAIN based on selected stage");
        assertTrue(
                wiringScript.contains("./aws-deploy.sh -prod"),
                "Expected production wiring flow to call aws-deploy.sh with -prod");
    }

    @Test
    @DisplayName("provides dedicated dockerfile for native lambda image")
    void providesNativeLambdaDockerfile() {
        assertTrue(
                Files.exists(Path.of("Dockerfile.lambda.native")),
                "Expected Dockerfile.lambda.native for GraalVM/native Lambda deployment");
    }

    @Test
    @DisplayName("installs xargs dependency in native build container")
    void installsXargsDependencyInNativeBuildContainer() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile.lambda.native"));

        assertTrue(
                dockerfile.contains("microdnf install -y findutils"),
                "Expected native Dockerfile to install findutils/xargs before running Gradle");
    }

    @Test
    @DisplayName("uses glibc-based runtime image for native lambda binary")
    void usesGlibcBasedRuntimeImageForNativeBinary() throws IOException {
        String dockerfile = Files.readString(Path.of("Dockerfile.lambda.native"));

        assertTrue(
                dockerfile.contains("FROM public.ecr.aws/amazonlinux/amazonlinux:2023"),
                "Expected native runtime stage to use a glibc-based amazonlinux image");
    }
}

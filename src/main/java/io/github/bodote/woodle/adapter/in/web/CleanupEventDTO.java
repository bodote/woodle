package io.github.bodote.woodle.adapter.in.web;

/**
 * Payload delivered by the EventBridge schedule and forwarded by the AWS Lambda Web
 * Adapter as the body of a POST to the pass-through path.
 */
public record CleanupEventDTO(String task, String token) {
}

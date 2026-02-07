#!/usr/bin/env bash
set -euo pipefail

LOCALSTACK_CONTAINER="woodle-localstack"
LOCALSTACK_IMAGE="localstack/localstack:3.1.0"
LOCALSTACK_PORT="4566"
BUCKET_NAME="woodle"

APP_PORT="8088"

if ! docker ps --format '{{.Names}}' | grep -q "^${LOCALSTACK_CONTAINER}$"; then
  docker run -d --name "${LOCALSTACK_CONTAINER}" -p ${LOCALSTACK_PORT}:4566 "${LOCALSTACK_IMAGE}"
fi

docker exec "${LOCALSTACK_CONTAINER}" awslocal s3 mb "s3://${BUCKET_NAME}" >/dev/null 2>&1 || true

./gradlew bootRun --args="--server.port=${APP_PORT} --woodle.s3.enabled=true --woodle.s3.endpoint=http://localhost:${LOCALSTACK_PORT} --woodle.s3.region=eu-central-1 --woodle.s3.pathStyle=true --woodle.s3.bucket=${BUCKET_NAME}"

#!/usr/bin/env bash
set -euo pipefail

# Reusable local deployment script for Woodle (SAM + Lambda container image).
# Defaults can be overridden via environment variables.
#
# Example:
#   ./aws-deploy.sh              # qs stage -> qs.woodle.click
#   ./aws-deploy.sh -prod        # prod stage -> woodle.click

DEPLOY_STAGE="qs"
if [[ "${1:-}" == "-prod" ]]; then
  DEPLOY_STAGE="prod"
  shift
fi

if [[ $# -gt 0 ]]; then
  echo "Usage: ./aws-deploy.sh [-prod]" >&2
  exit 1
fi

DEFAULT_ENV_NAME="${DEPLOY_STAGE}"
DEFAULT_FRONTEND_DOMAIN="qs.woodle.click"
DEFAULT_EMAIL_FROM="no-reply@qs.woodle.click"
DEFAULT_EMAIL_SUBJECT_PREFIX="[Woodle-QS]"
if [[ "${DEPLOY_STAGE}" == "prod" ]]; then
  DEFAULT_FRONTEND_DOMAIN="woodle.click"
  DEFAULT_EMAIL_FROM="no-reply@woodle.click"
  DEFAULT_EMAIL_SUBJECT_PREFIX="[Woodle]"
fi

AWS_REGION="${AWS_REGION:-eu-central-1}"
ENV_NAME="${ENV_NAME:-${DEFAULT_ENV_NAME}}"
STACK_NAME="${STACK_NAME:-woodle-${ENV_NAME}}"
ECR_REPO_NAME="${ECR_REPO_NAME:-woodle-lambda}"
if [[ -z "${IMAGE_TAG:-}" ]]; then
  IMAGE_TAG="$(date +%Y%m%d%H%M%S)"
fi
TEMPLATE_FILE="${TEMPLATE_FILE:-infra/template.yaml}"
DEPLOY_RUNTIME="${DEPLOY_RUNTIME:-jvm}"
LAMBDA_DOCKERFILE_JVM="${LAMBDA_DOCKERFILE_JVM:-Dockerfile.lambda}"
LAMBDA_DOCKERFILE_NATIVE="${LAMBDA_DOCKERFILE_NATIVE:-Dockerfile.lambda.native}"
LAMBDA_DOCKERFILE="${LAMBDA_DOCKERFILE:-}"
APP_DOMAIN_NAME="${APP_DOMAIN_NAME:-${DEFAULT_FRONTEND_DOMAIN}}"
ACM_CERTIFICATE_ARN="${ACM_CERTIFICATE_ARN:-}"
DISABLE_CLOUDFRONT_INVALIDATION="${DISABLE_CLOUDFRONT_INVALIDATION:-false}"
CLOUDFRONT_INVALIDATION_PATHS="${CLOUDFRONT_INVALIDATION_PATHS:-/*}"
WOODLE_BACKEND_BASE_URL="${WOODLE_BACKEND_BASE_URL:-}"
WOODLE_EMAIL_ENABLED="${WOODLE_EMAIL_ENABLED:-true}"
WOODLE_EMAIL_PROVIDER="${WOODLE_EMAIL_PROVIDER:-smtp}"
WOODLE_EMAIL_FROM="${WOODLE_EMAIL_FROM:-${DEFAULT_EMAIL_FROM}}"
WOODLE_EMAIL_SUBJECT_PREFIX="${WOODLE_EMAIL_SUBJECT_PREFIX:-${DEFAULT_EMAIL_SUBJECT_PREFIX}}"
WOODLE_EMAIL_SMTP_HOST="${WOODLE_EMAIL_SMTP_HOST:-smtp.ionos.de}"
WOODLE_EMAIL_SMTP_PORT="${WOODLE_EMAIL_SMTP_PORT:-587}"
WOODLE_EMAIL_SMTP_USERNAME="${WOODLE_EMAIL_SMTP_USERNAME:-woodle@funknstein.de}"
WOODLE_EMAIL_SMTP_PASSWORD_SECRET_ID="${WOODLE_EMAIL_SMTP_PASSWORD_SECRET_ID:-}"
DRY_RUN="${DRY_RUN:-false}"

if [[ -z "${WOODLE_EMAIL_FROM}" ]]; then
  echo "WOODLE_EMAIL_FROM must be set (for example no-reply@${DEFAULT_FRONTEND_DOMAIN})." >&2
  exit 1
fi

if [[ -z "${LAMBDA_DOCKERFILE}" ]]; then
  if [[ "${DEPLOY_RUNTIME}" == "native" ]]; then
    LAMBDA_DOCKERFILE="${LAMBDA_DOCKERFILE_NATIVE}"
  elif [[ "${DEPLOY_RUNTIME}" == "jvm" ]]; then
    LAMBDA_DOCKERFILE="${LAMBDA_DOCKERFILE_JVM}"
  else
    echo "Unsupported DEPLOY_RUNTIME: ${DEPLOY_RUNTIME}. Use 'jvm' or 'native'." >&2
    exit 1
  fi
fi

if [[ "${DEPLOY_RUNTIME}" == "native" ]]; then
  echo "Running native deployment preflight checks..."

  if [[ ! -f "${LAMBDA_DOCKERFILE}" ]]; then
    echo "Native Dockerfile not found: ${LAMBDA_DOCKERFILE}" >&2
    exit 1
  fi

  if ! docker buildx version >/dev/null 2>&1; then
    echo "Docker buildx is required for native lambda image publishing." >&2
    exit 1
  fi

  BUILDX_PLATFORMS="$(docker buildx inspect 2>/dev/null | grep -E '^Platforms:' || true)"
  if [[ -z "${BUILDX_PLATFORMS}" || "${BUILDX_PLATFORMS}" != *"linux/arm64"* ]]; then
    echo "Docker buildx builder does not report linux/arm64 support." >&2
    echo "Create/select a builder with arm64 support, then re-run deployment." >&2
    exit 1
  fi
fi

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

require_cmd aws
require_cmd sam
require_cmd docker
require_cmd jq
require_cmd rsync

echo "Resolving AWS account ID..."
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
if [[ -z "${ACCOUNT_ID}" || "${ACCOUNT_ID}" == "None" ]]; then
  echo "Unable to resolve AWS account ID from current credentials." >&2
  exit 1
fi

ECR_REGISTRY="${ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE_URI="${ECR_REGISTRY}/${ECR_REPO_NAME}:${IMAGE_TAG}"

echo "Deploy configuration:"
echo "  Account:       ${ACCOUNT_ID}"
echo "  Region:        ${AWS_REGION}"
echo "  Stage:         ${DEPLOY_STAGE}"
echo "  Stack:         ${STACK_NAME}"
echo "  Environment:   ${ENV_NAME}"
echo "  Runtime mode:  ${DEPLOY_RUNTIME}"
echo "  Dry run:       ${DRY_RUN}"
echo "  ECR repo:      ${ECR_REPO_NAME}"
echo "  Image URI:     ${IMAGE_URI}"
echo "  Template file: ${TEMPLATE_FILE}"
echo "  Dockerfile:    ${LAMBDA_DOCKERFILE}"
echo "  Domain:        ${APP_DOMAIN_NAME}"

if [[ -n "${APP_DOMAIN_NAME}" && -z "${ACM_CERTIFICATE_ARN}" ]]; then
  echo "Resolving ACM certificate for ${APP_DOMAIN_NAME} in us-east-1..."
  ACM_CERTIFICATE_ARN="$(aws acm list-certificates \
    --region us-east-1 \
    --certificate-statuses ISSUED \
    --query "CertificateSummaryList[?DomainName=='${APP_DOMAIN_NAME}'] | [0].CertificateArn" \
    --output text)"

  if [[ -z "${ACM_CERTIFICATE_ARN}" || "${ACM_CERTIFICATE_ARN}" == "None" ]]; then
    echo "No ISSUED ACM certificate found for ${APP_DOMAIN_NAME} in us-east-1." >&2
    echo "Set ACM_CERTIFICATE_ARN explicitly or provision certificate first." >&2
    exit 1
  fi
fi

if [[ "${DEPLOY_RUNTIME}" == "jvm" ]]; then
  echo "Building application jar for JVM deployment..."
  ./gradlew bootJar
else
  echo "Skipping local jar build: native image is built inside Docker."
fi

if [[ "${DRY_RUN}" == "true" ]]; then
  echo "Dry-run enabled. Stopping before AWS and Docker operations."
  exit 0
fi

if [[ -n "${APP_DOMAIN_NAME}" ]]; then
  echo "Validating CloudFront custom domain alias ownership..."
  ALIAS_OWNER_DISTRIBUTION_ID="$(aws cloudfront list-distributions \
    --query "DistributionList.Items[?Aliases.Quantity > \`0\` && contains(Aliases.Items, '${APP_DOMAIN_NAME}')].Id | [0]" \
    --output text)"

  STACK_DISTRIBUTION_ID="$(aws cloudformation describe-stack-resources \
    --stack-name "${STACK_NAME}" \
    --region "${AWS_REGION}" \
    --logical-resource-id "WebDistribution" \
    --query "StackResources[0].PhysicalResourceId" \
    --output text 2>/dev/null || true)"

  if [[ "${ALIAS_OWNER_DISTRIBUTION_ID}" != "None" && -n "${ALIAS_OWNER_DISTRIBUTION_ID}" \
    && "${ALIAS_OWNER_DISTRIBUTION_ID}" != "${STACK_DISTRIBUTION_ID}" ]]; then
    echo "CloudFront alias '${APP_DOMAIN_NAME}' is already associated with distribution ${ALIAS_OWNER_DISTRIBUTION_ID}." >&2
    echo "Remove the alias from the old distribution or deploy with a different APP_DOMAIN_NAME." >&2
    exit 1
  fi
fi

echo "Ensuring ECR repository exists: ${ECR_REPO_NAME}"
if ! aws ecr describe-repositories --repository-names "${ECR_REPO_NAME}" --region "${AWS_REGION}" >/dev/null 2>&1; then
  aws ecr create-repository --repository-name "${ECR_REPO_NAME}" --region "${AWS_REGION}" >/dev/null
fi

echo "Logging in to ECR..."
aws ecr get-login-password --region "${AWS_REGION}" \
  | docker login --username AWS --password-stdin "${ECR_REGISTRY}"

echo "Checking Docker buildx support..."
if ! docker buildx version >/dev/null 2>&1; then
  echo "Docker buildx is required for Lambda-compatible image publishing." >&2
  exit 1
fi

echo "Building and pushing Lambda image (linux/arm64, lambda-compatible manifest)..."
docker buildx build \
  --platform linux/arm64 \
  --provenance=false \
  --sbom=false \
  -f "${LAMBDA_DOCKERFILE}" \
  -t "${IMAGE_URI}" \
  --push \
  .

PARAMETER_OVERRIDES=(
  "EnvironmentName=${ENV_NAME}"
  "LambdaImageUri=${IMAGE_URI}"
  "EmailEnabled=${WOODLE_EMAIL_ENABLED}"
  "EmailProvider=${WOODLE_EMAIL_PROVIDER}"
  "EmailFromAddress=${WOODLE_EMAIL_FROM}"
  "EmailSubjectPrefix=${WOODLE_EMAIL_SUBJECT_PREFIX}"
  "EmailSmtpHost=${WOODLE_EMAIL_SMTP_HOST}"
  "EmailSmtpPort=${WOODLE_EMAIL_SMTP_PORT}"
  "EmailSmtpUsername=${WOODLE_EMAIL_SMTP_USERNAME}"
)

if [[ -n "${WOODLE_EMAIL_SMTP_PASSWORD_SECRET_ID}" ]]; then
  PARAMETER_OVERRIDES+=("EmailSmtpPasswordSecretId=${WOODLE_EMAIL_SMTP_PASSWORD_SECRET_ID}")
fi

if [[ -n "${APP_DOMAIN_NAME}" ]]; then
  PARAMETER_OVERRIDES+=("AppDomainName=${APP_DOMAIN_NAME}")
fi

if [[ -n "${ACM_CERTIFICATE_ARN}" ]]; then
  PARAMETER_OVERRIDES+=("AcmCertificateArn=${ACM_CERTIFICATE_ARN}")
fi

echo "Checking CloudFormation stack state..."
STACK_STATUS="$(aws cloudformation describe-stacks \
  --stack-name "${STACK_NAME}" \
  --region "${AWS_REGION}" \
  --query "Stacks[0].StackStatus" \
  --output text 2>/dev/null || true)"

if [[ "${STACK_STATUS}" == "ROLLBACK_COMPLETE" ]]; then
  echo "Stack ${STACK_NAME} is in ROLLBACK_COMPLETE. Deleting before redeploy..."
  aws cloudformation delete-stack \
    --stack-name "${STACK_NAME}" \
    --region "${AWS_REGION}"
  aws cloudformation wait stack-delete-complete \
    --stack-name "${STACK_NAME}" \
    --region "${AWS_REGION}"
  echo "Stack ${STACK_NAME} deleted."
fi

echo "Deploying SAM stack..."
sam deploy \
  --template-file "${TEMPLATE_FILE}" \
  --stack-name "${STACK_NAME}" \
  --capabilities CAPABILITY_IAM \
  --region "${AWS_REGION}" \
  --resolve-s3 \
  --resolve-image-repos \
  --no-confirm-changeset \
  --no-fail-on-empty-changeset \
  --parameter-overrides "${PARAMETER_OVERRIDES[@]}"

echo "Deployment completed."

STATIC_DIR="src/main/resources/static/"
if [[ -d "${STATIC_DIR}" ]]; then
  echo "Resolving stack outputs for static asset deployment..."
  WEB_BUCKET_NAME="$(aws cloudformation describe-stacks \
    --stack-name "${STACK_NAME}" \
    --region "${AWS_REGION}" \
    --query "Stacks[0].Outputs[?OutputKey=='WebBucketName'].OutputValue | [0]" \
    --output text)"
  FRONTEND_CLOUDFRONT_URL="$(aws cloudformation describe-stacks \
    --stack-name "${STACK_NAME}" \
    --region "${AWS_REGION}" \
    --query "Stacks[0].Outputs[?OutputKey=='FrontendCloudFrontUrl'].OutputValue | [0]" \
    --output text)"

  if [[ -z "${WEB_BUCKET_NAME}" || "${WEB_BUCKET_NAME}" == "None" ]]; then
    echo "Could not resolve WebBucketName from stack outputs." >&2
    exit 1
  fi

  TMP_STATIC_DIR="$(mktemp -d)"
  trap 'rm -rf "${TMP_STATIC_DIR}"' EXIT

  rsync -a "${STATIC_DIR}/" "${TMP_STATIC_DIR}/"

  RUNTIME_BACKEND_BASE_URL=""
  if [[ -n "${WOODLE_BACKEND_BASE_URL}" ]]; then
    RUNTIME_BACKEND_BASE_URL="${WOODLE_BACKEND_BASE_URL%/}"
  fi

  if [[ -n "${RUNTIME_BACKEND_BASE_URL}" ]]; then
    if [[ "${RUNTIME_BACKEND_BASE_URL}" =~ ^http:// ]]; then
      RUNTIME_BACKEND_BASE_URL="https://${RUNTIME_BACKEND_BASE_URL#http://}"
      echo "Normalized backend base URL to HTTPS: ${RUNTIME_BACKEND_BASE_URL}"
    elif [[ ! "${RUNTIME_BACKEND_BASE_URL}" =~ ^https:// ]]; then
      RUNTIME_BACKEND_BASE_URL="https://${RUNTIME_BACKEND_BASE_URL}"
      echo "Prefixed backend base URL with HTTPS: ${RUNTIME_BACKEND_BASE_URL}"
    fi
  else
    echo "Using same-origin backend base URL for runtime config."
  fi

  printf 'window.WOODLE_BACKEND_BASE_URL = "%s";\n' "${RUNTIME_BACKEND_BASE_URL}" > "${TMP_STATIC_DIR}/runtime-config.js"

  echo "Syncing static assets from ${STATIC_DIR} to s3://${WEB_BUCKET_NAME}/ ..."
  aws s3 sync "${TMP_STATIC_DIR}/" "s3://${WEB_BUCKET_NAME}/" --delete --region "${AWS_REGION}"

  if [[ "${DISABLE_CLOUDFRONT_INVALIDATION}" != "true" ]]; then
    if [[ -z "${FRONTEND_CLOUDFRONT_URL}" || "${FRONTEND_CLOUDFRONT_URL}" == "None" ]]; then
      echo "Skipping CloudFront invalidation: FrontendCloudFrontUrl output missing."
    else
      FRONTEND_CLOUDFRONT_DOMAIN="${FRONTEND_CLOUDFRONT_URL#https://}"
      FRONTEND_CLOUDFRONT_DOMAIN="${FRONTEND_CLOUDFRONT_DOMAIN%/}"

      DISTRIBUTION_ID="$(aws cloudfront list-distributions \
        --query "DistributionList.Items[?DomainName=='${FRONTEND_CLOUDFRONT_DOMAIN}'].Id | [0]" \
        --output text)"

      if [[ -z "${DISTRIBUTION_ID}" || "${DISTRIBUTION_ID}" == "None" ]]; then
        echo "Skipping CloudFront invalidation: distribution id not found for ${FRONTEND_CLOUDFRONT_DOMAIN}."
      else
        read -r -a INVALIDATION_PATH_ARRAY <<< "${CLOUDFRONT_INVALIDATION_PATHS}"
        echo "Creating CloudFront invalidation for distribution ${DISTRIBUTION_ID}: ${INVALIDATION_PATH_ARRAY[*]}"
        aws cloudfront create-invalidation \
          --distribution-id "${DISTRIBUTION_ID}" \
          --paths "${INVALIDATION_PATH_ARRAY[@]}" >/dev/null
      fi
    fi
  fi
else
  echo "Skipping static asset sync: directory not found: ${STATIC_DIR}"
fi

echo "Fetching stack outputs..."
aws cloudformation describe-stacks \
  --stack-name "${STACK_NAME}" \
  --region "${AWS_REGION}" \
  --query "Stacks[0].Outputs" \
  --output table

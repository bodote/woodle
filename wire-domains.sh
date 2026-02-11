#!/usr/bin/env bash
set -euo pipefail

DEPLOY_STAGE="qs"
if [[ "${1:-}" == "-prod" ]]; then
  DEPLOY_STAGE="prod"
  shift
fi

if [[ $# -gt 0 ]]; then
  echo "Usage: ./wire-domains.sh [-prod]" >&2
  exit 1
fi

DEFAULT_STACK_NAME="woodle-qs"
DEFAULT_FRONTEND_DOMAIN="qs.woodle.click"
DEFAULT_API_DOMAIN="api.qs.woodle.click"
DEFAULT_HOSTED_ZONE_DOMAIN="woodle.click"
if [[ "${DEPLOY_STAGE}" == "prod" ]]; then
  DEFAULT_STACK_NAME="woodle-prod"
  DEFAULT_FRONTEND_DOMAIN="woodle.click"
  DEFAULT_API_DOMAIN="api.woodle.click"
fi

# Wires:
# - stage frontend domain -> CloudFront distribution (from stack output)
# - stage API domain      -> API Gateway HTTP API custom domain
#
# Defaults can be overridden:
#   ./wire-domains.sh        # qs stage
#   ./wire-domains.sh -prod  # production stage
# Optional:
#   HOSTED_ZONE_ID=<route53-zone-id> ./wire-domains.sh

AWS_REGION="${AWS_REGION:-eu-central-1}"
STACK_NAME="${STACK_NAME:-${DEFAULT_STACK_NAME}}"
ROOT_DOMAIN="${ROOT_DOMAIN:-${DEFAULT_FRONTEND_DOMAIN}}"
API_DOMAIN="${API_DOMAIN:-${DEFAULT_API_DOMAIN}}"
HOSTED_ZONE_DOMAIN="${HOSTED_ZONE_DOMAIN:-${DEFAULT_HOSTED_ZONE_DOMAIN}}"
HOSTED_ZONE_ID="${HOSTED_ZONE_ID:-}"
CF_HOSTED_ZONE_ID="Z2FDTNDATAQYW2" # CloudFront alias hosted zone id (global constant)

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

cleanup() {
  if [[ -n "${TMP_DIR:-}" && -d "${TMP_DIR}" ]]; then
    rm -rf "${TMP_DIR}"
  fi
}

get_stack_output() {
  local key="$1"
  aws cloudformation describe-stacks \
    --stack-name "${STACK_NAME}" \
    --region "${AWS_REGION}" \
    --query "Stacks[0].Outputs[?OutputKey=='${key}'].OutputValue | [0]" \
    --output text
}

strip_scheme() {
  local url="$1"
  echo "${url#https://}" | sed 's#/$##'
}

resolve_hosted_zone_id() {
  if [[ -n "${HOSTED_ZONE_ID}" ]]; then
    echo "${HOSTED_ZONE_ID}"
    return 0
  fi

  local zone_id
  zone_id="$(aws route53 list-hosted-zones-by-name \
    --dns-name "${HOSTED_ZONE_DOMAIN}" \
    --query "HostedZones[?Name=='${HOSTED_ZONE_DOMAIN}.'] | [0].Id" \
    --output text)"

  if [[ -z "${zone_id}" || "${zone_id}" == "None" ]]; then
    echo "Could not resolve hosted zone for ${HOSTED_ZONE_DOMAIN}. Set HOSTED_ZONE_ID explicitly." >&2
    exit 1
  fi

  echo "${zone_id#/hostedzone/}"
}

upsert_record() {
  local change_batch_file="$1"
  aws route53 change-resource-record-sets \
    --hosted-zone-id "${ZONE_ID}" \
    --change-batch "file://${change_batch_file}" >/dev/null
}

upsert_cname() {
  local name="$1"
  local value="$2"
  local file="${TMP_DIR}/cname-${name//./-}.json"
  cat > "${file}" <<EOF
{
  "Comment": "UPSERT validation CNAME for ${name}",
  "Changes": [{
    "Action": "UPSERT",
    "ResourceRecordSet": {
      "Name": "${name}",
      "Type": "CNAME",
      "TTL": 300,
      "ResourceRecords": [{"Value": "${value}"}]
    }
  }]
}
EOF
  upsert_record "${file}"
}

upsert_alias_a() {
  local name="$1"
  local dns_name="$2"
  local hosted_zone_id="$3"
  local file="${TMP_DIR}/alias-${name//./-}.json"
  cat > "${file}" <<EOF
{
  "Comment": "UPSERT alias A for ${name}",
  "Changes": [{
    "Action": "UPSERT",
    "ResourceRecordSet": {
      "Name": "${name}",
      "Type": "A",
      "AliasTarget": {
        "HostedZoneId": "${hosted_zone_id}",
        "DNSName": "${dns_name}",
        "EvaluateTargetHealth": false
      }
    }
  }]
}
EOF
  upsert_record "${file}"
}

find_or_request_cert() {
  local region="$1"
  local domain="$2"

  local existing
  existing="$(aws acm list-certificates \
    --region "${region}" \
    --certificate-statuses ISSUED \
    --query "CertificateSummaryList[?DomainName=='${domain}'] | [0].CertificateArn" \
    --output text)"

  if [[ -n "${existing}" && "${existing}" != "None" ]]; then
    echo "${existing}"
    return 0
  fi

  existing="$(aws acm list-certificates \
    --region "${region}" \
    --certificate-statuses PENDING_VALIDATION \
    --query "CertificateSummaryList[?DomainName=='${domain}'] | [0].CertificateArn" \
    --output text)"

  if [[ -n "${existing}" && "${existing}" != "None" ]]; then
    echo "${existing}"
    return 0
  fi

  aws acm request-certificate \
    --region "${region}" \
    --domain-name "${domain}" \
    --validation-method DNS \
    --query CertificateArn \
    --output text
}

upsert_validation_record_for_cert() {
  local region="$1"
  local cert_arn="$2"
  local domain="$3"

  local rr_json=""
  local rr_name=""
  local rr_value=""
  local rr_type=""
  for attempt in {1..20}; do
    rr_json="$(aws acm describe-certificate \
      --region "${region}" \
      --certificate-arn "${cert_arn}" \
      --query "Certificate.DomainValidationOptions[?DomainName=='${domain}'] | [0].ResourceRecord" \
      --output json)"

    rr_name="$(echo "${rr_json}" | jq -r '.Name // empty')"
    rr_value="$(echo "${rr_json}" | jq -r '.Value // empty')"
    rr_type="$(echo "${rr_json}" | jq -r '.Type // empty')"

    if [[ -n "${rr_name}" && -n "${rr_value}" && "${rr_type}" == "CNAME" ]]; then
      break
    fi

    echo "Waiting for ACM DNS validation record to become available (${attempt}/20) for ${domain}..."
    sleep 3
  done

  if [[ -z "${rr_name}" || -z "${rr_value}" || "${rr_type}" != "CNAME" ]]; then
    echo "Could not resolve DNS validation CNAME for ${domain} (${cert_arn})." >&2
    exit 1
  fi

  echo "Upserting ACM validation CNAME for ${domain}: ${rr_name}"
  upsert_cname "${rr_name}" "${rr_value}"
}

wait_for_certificate() {
  local region="$1"
  local cert_arn="$2"
  echo "Waiting for certificate validation: ${cert_arn} (${region})"
  aws acm wait certificate-validated \
    --region "${region}" \
    --certificate-arn "${cert_arn}"
}

get_certificate_status() {
  local region="$1"
  local cert_arn="$2"
  aws acm describe-certificate \
    --region "${region}" \
    --certificate-arn "${cert_arn}" \
    --query "Certificate.Status" \
    --output text
}

ensure_api_custom_domain() {
  local api_cert_arn="$1"
  if aws apigatewayv2 get-domain-name --region "${AWS_REGION}" --domain-name "${API_DOMAIN}" >/dev/null 2>&1; then
    echo "API custom domain already exists: ${API_DOMAIN}"
    return 0
  fi

  aws apigatewayv2 create-domain-name \
    --region "${AWS_REGION}" \
    --domain-name "${API_DOMAIN}" \
    --domain-name-configurations "CertificateArn=${api_cert_arn},EndpointType=REGIONAL,SecurityPolicy=TLS_1_2" >/dev/null
}

ensure_api_mapping() {
  local api_id="$1"
  local mapping_count
  mapping_count="$(aws apigatewayv2 get-api-mappings \
    --region "${AWS_REGION}" \
    --domain-name "${API_DOMAIN}" \
    --query "length(Items[?ApiId=='${api_id}' && Stage=='\$default'])" \
    --output text)"

  if [[ "${mapping_count}" != "0" ]]; then
    echo "API mapping for ${API_DOMAIN} -> ${api_id} (\$default) already exists"
    return 0
  fi

  aws apigatewayv2 create-api-mapping \
    --region "${AWS_REGION}" \
    --domain-name "${API_DOMAIN}" \
    --api-id "${api_id}" \
    --stage "\$default" >/dev/null
}

require_cmd aws
require_cmd jq
require_cmd sed

TMP_DIR="$(mktemp -d)"
trap cleanup EXIT

echo "Resolving hosted zone..."
ZONE_ID="$(resolve_hosted_zone_id)"
echo "Using hosted zone: ${ZONE_ID}"

echo "Reading CloudFormation outputs..."
FRONTEND_URL="$(get_stack_output FrontendCloudFrontUrl)"
API_BASE_URL="$(get_stack_output ApiBaseUrl)"

if [[ -z "${FRONTEND_URL}" || "${FRONTEND_URL}" == "None" ]]; then
  echo "Missing FrontendCloudFrontUrl output in stack ${STACK_NAME}" >&2
  exit 1
fi
if [[ -z "${API_BASE_URL}" || "${API_BASE_URL}" == "None" ]]; then
  echo "Missing ApiBaseUrl output in stack ${STACK_NAME}" >&2
  exit 1
fi

CF_DOMAIN="$(strip_scheme "${FRONTEND_URL}")"
API_ID="$(echo "${API_BASE_URL}" | sed -E 's#https://([^.]+)\..*#\1#')"

if [[ -z "${API_ID}" || "${API_ID}" == "${API_BASE_URL}" ]]; then
  echo "Could not parse API ID from ApiBaseUrl=${API_BASE_URL}" >&2
  exit 1
fi

echo "Step 1/6: Request or reuse CloudFront cert (us-east-1) for ${ROOT_DOMAIN}"
CF_CERT_ARN="$(find_or_request_cert us-east-1 "${ROOT_DOMAIN}")"
CF_CERT_STATUS="$(get_certificate_status us-east-1 "${CF_CERT_ARN}")"
if [[ "${CF_CERT_STATUS}" != "ISSUED" ]]; then
  upsert_validation_record_for_cert us-east-1 "${CF_CERT_ARN}" "${ROOT_DOMAIN}"
  wait_for_certificate us-east-1 "${CF_CERT_ARN}"
fi

if [[ ! -x "./aws-deploy.sh" ]]; then
  echo "Missing executable ./aws-deploy.sh required to update CloudFront alias cert/domain in stack." >&2
  exit 1
fi

echo "Step 2/6: Redeploy stack with CloudFront custom domain ${ROOT_DOMAIN}"
if [[ "${DEPLOY_STAGE}" == "prod" ]]; then
  APP_DOMAIN_NAME="${ROOT_DOMAIN}" ACM_CERTIFICATE_ARN="${CF_CERT_ARN}" ./aws-deploy.sh -prod
else
  APP_DOMAIN_NAME="${ROOT_DOMAIN}" ACM_CERTIFICATE_ARN="${CF_CERT_ARN}" ./aws-deploy.sh
fi

echo "Step 3/6: Alias ${ROOT_DOMAIN} -> ${CF_DOMAIN}"
upsert_alias_a "${ROOT_DOMAIN}" "${CF_DOMAIN}" "${CF_HOSTED_ZONE_ID}"

echo "Step 4/6: Request or reuse regional cert (${AWS_REGION}) for ${API_DOMAIN}"
API_CERT_ARN="$(find_or_request_cert "${AWS_REGION}" "${API_DOMAIN}")"
API_CERT_STATUS="$(get_certificate_status "${AWS_REGION}" "${API_CERT_ARN}")"
if [[ "${API_CERT_STATUS}" != "ISSUED" ]]; then
  upsert_validation_record_for_cert "${AWS_REGION}" "${API_CERT_ARN}" "${API_DOMAIN}"
  wait_for_certificate "${AWS_REGION}" "${API_CERT_ARN}"
fi

echo "Step 5/6: Ensure API Gateway custom domain and mapping"
ensure_api_custom_domain "${API_CERT_ARN}"
ensure_api_mapping "${API_ID}"

echo "Step 6/6: Alias ${API_DOMAIN} -> API Gateway domain"
read -r API_GATEWAY_TARGET_DOMAIN API_GATEWAY_TARGET_HZ < <(aws apigatewayv2 get-domain-name \
  --region "${AWS_REGION}" \
  --domain-name "${API_DOMAIN}" \
  --query "DomainNameConfigurations[0].[ApiGatewayDomainName,HostedZoneId]" \
  --output text)

if [[ -z "${API_GATEWAY_TARGET_DOMAIN}" || -z "${API_GATEWAY_TARGET_HZ}" ]]; then
  echo "Could not resolve API Gateway alias target for ${API_DOMAIN}" >&2
  exit 1
fi

upsert_alias_a "${API_DOMAIN}" "${API_GATEWAY_TARGET_DOMAIN}" "${API_GATEWAY_TARGET_HZ}"

echo "Domain wiring completed."
echo "Verify:"
echo "  https://${ROOT_DOMAIN}/poll/new-step1.html"
echo "  https://${API_DOMAIN}/v1/polls/non-existent-id"

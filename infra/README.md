# AWS Deploy (SAM)

This folder contains a baseline serverless deployment for:
- static step-1 frontend in S3 + CloudFront
- backend app in Lambda container image
- HTTP API Gateway in front of Lambda
- poll persistence in S3

## Prerequisites

1. AWS CLI configured for target account/region.
2. SAM CLI installed.
3. ECR repository for the Lambda image.
4. Docker available for image build/push.

## Build and push Lambda image

```bash
aws ecr create-repository --repository-name woodle-lambda --region eu-central-1
aws ecr get-login-password --region eu-central-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.eu-central-1.amazonaws.com
docker build -f Dockerfile.lambda -t woodle-lambda:latest .
docker tag woodle-lambda:latest <account-id>.dkr.ecr.eu-central-1.amazonaws.com/woodle-lambda:latest
docker push <account-id>.dkr.ecr.eu-central-1.amazonaws.com/woodle-lambda:latest
```

## Deploy stack

```bash
sam deploy \
  --template-file infra/template.yaml \
  --stack-name woodle-dev \
  --capabilities CAPABILITY_IAM \
  --region eu-central-1 \
  --parameter-overrides \
    EnvironmentName=dev \
    LambdaImageUri=<account-id>.dkr.ecr.eu-central-1.amazonaws.com/woodle-lambda:latest
```

## Post-deploy smoke checks

1. Open CloudFront URL from stack outputs and confirm `/poll/new-step1.html` loads.
2. Create a poll from step 1 and reach `/poll/step-2`.
3. Call API endpoint:
   - `POST /v1/polls`
   - `GET /v1/polls/{pollId}`
4. Verify object exists in poll bucket under `polls/{pollId}.json`.

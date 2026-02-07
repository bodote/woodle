# Woodle

## Local E2E (Playwright + LocalStack)

1. Start LocalStack (S3):
```bash
docker run -d --name woodle-localstack -p 4566:4566 localstack/localstack:3.1.0
docker exec woodle-localstack awslocal s3 mb s3://woodle
```

2. Start the app (S3 enabled):
```bash
./gradlew bootRun --args='--woodle.s3.enabled=true --woodle.s3.endpoint=http://localhost:4566 --woodle.s3.region=eu-central-1 --woodle.s3.pathStyle=true --woodle.s3.bucket=woodle'
```

3. Run E2E:
```bash
./gradlew test --tests '*E2E*'
```

4. Cleanup:
```bash
docker stop woodle-localstack && docker rm woodle-localstack
```

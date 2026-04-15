#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

if [[ ! -f "$STATE_FILE" ]]; then
  echo "ERROR: State file not found. Run setup scripts first."
  exit 1
fi

API_ENDPOINT=$(jq -r '.api_endpoint' "$STATE_FILE")

echo "=== Building and Deploying Backend ==="

# Build standard Spring Boot JAR
BACKEND_DIR="$(dirname "$(dirname "$SCRIPT_DIR")")"
cd "$BACKEND_DIR"
mvn clean package -DskipTests

# Build Lambda-compatible JAR using the flatten script
bash "$BACKEND_DIR/scripts/build-lambda-jar.sh"

LAMBDA_JAR="$BACKEND_DIR/target/flowboard-backend-1.0.0-lambda.jar"
JAR_SIZE=$(stat -f%z "$LAMBDA_JAR" 2>/dev/null || stat -c%s "$LAMBDA_JAR" 2>/dev/null || echo "0")
UPLOAD_LIMIT=50000000  # ~50 MB direct upload safe limit

if [[ "$JAR_SIZE" -gt "$UPLOAD_LIMIT" ]]; then
  echo "JAR size ($JAR_SIZE bytes) exceeds direct upload limit. Using S3 deployment."
  DEPLOY_BUCKET="${PROJECT_NAME}-lambda-deploy-$(aws sts get-caller-identity --query Account --output text)"
  if ! aws s3api head-bucket --bucket "$DEPLOY_BUCKET" >/dev/null 2>&1; then
    aws s3 mb "s3://$DEPLOY_BUCKET" --region "$AWS_REGION" >/dev/null
    echo "S3 deployment bucket created: $DEPLOY_BUCKET"
  fi
  S3_KEY="flowboard-backend-1.0.0-lambda.jar"
  aws s3 cp "$LAMBDA_JAR" "s3://$DEPLOY_BUCKET/$S3_KEY" >/dev/null
  echo "Lambda JAR uploaded to S3"
  aws lambda update-function-code \
    --function-name "$LAMBDA_FUNCTION_NAME" \
    --s3-bucket "$DEPLOY_BUCKET" \
    --s3-key "$S3_KEY" \
    --publish >/dev/null
else
  echo "Uploading $LAMBDA_JAR to Lambda via direct upload..."
  aws lambda update-function-code \
    --function-name "$LAMBDA_FUNCTION_NAME" \
    --zip-file "fileb://$LAMBDA_JAR" \
    --publish >/dev/null
fi

echo "Waiting for Lambda update to complete..."
aws lambda wait function-updated --function-name "$LAMBDA_FUNCTION_NAME"

echo "=== Deployment Complete ==="
echo "API Base URL: $API_ENDPOINT"
echo "Health Check: curl $API_ENDPOINT/api/v1/auth/register"
echo ""
echo "To run database migrations:"
echo "  source backend/scripts/aws-deploy/env.sh"
echo "  export DB_URL=jdbc:postgresql://$(jq -r '.proxy_endpoint' "$STATE_FILE"):5432/$DB_NAME"
echo "  cd backend && mvn flyway:migrate -Dflyway.url=\$DB_URL -Dflyway.user=$DB_USERNAME -Dflyway.password=\$DB_PASSWORD"

#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

if [[ ! -f "$STATE_FILE" ]]; then
  echo "ERROR: State file not found. Run previous setup scripts first."
  exit 1
fi

PRIVATE_SUBNETS=$(jq -r '.private_subnets | join(",")' "$STATE_FILE")
DB_SG=$(jq -r '.db_sg' "$STATE_FILE")
LAMBDA_SG=$(jq -r '.lambda_sg' "$STATE_FILE")
DB_ENDPOINT=$(jq -r '.db_endpoint' "$STATE_FILE")

echo "=== Setting up Lambda and API Gateway ==="

# Lambda execution role
TRUST_POLICY='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"lambda.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
if ! aws iam get-role --role-name "$LAMBDA_ROLE_NAME" >/dev/null 2>&1; then
  LAMBDA_ROLE_ARN=$(aws iam create-role \
    --role-name "$LAMBDA_ROLE_NAME" \
    --assume-role-policy-document "$TRUST_POLICY" \
    --query 'Role.Arn' --output text)
  aws iam attach-role-policy \
    --role-name "$LAMBDA_ROLE_NAME" \
    --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole >/dev/null
  aws iam attach-role-policy \
    --role-name "$LAMBDA_ROLE_NAME" \
    --policy-arn arn:aws:iam::aws:policy/CloudWatchLogsFullAccess >/dev/null
  sleep 10
  echo "Lambda IAM role created: $LAMBDA_ROLE_ARN"
else
  LAMBDA_ROLE_ARN=$(aws iam get-role --role-name "$LAMBDA_ROLE_NAME" --query 'Role.Arn' --output text)
  echo "Lambda IAM role already exists: $LAMBDA_ROLE_ARN"
fi

# Lambda function
ENV_JSON="{\"Variables\":{\"DB_URL\":\"jdbc:postgresql://$DB_ENDPOINT:5432/$DB_NAME?sslmode=disable\",\"DB_USERNAME\":\"$DB_USERNAME\",\"DB_PASSWORD\":\"$DB_PASSWORD\",\"JWT_SECRET\":\"$JWT_SECRET\",\"CORS_ALLOWED_ORIGINS\":\"$CORS_ALLOWED_ORIGINS\",\"AI_MOCK_ENABLED\":\"$AI_MOCK_ENABLED\",\"SPRING_PROFILES_ACTIVE\":\"lambda\",\"JAVA_TOOL_OPTIONS\":\"-Djava.security.egd=file:/dev/urandom\"}}"

if ! aws lambda get-function --function-name "$LAMBDA_FUNCTION_NAME" >/dev/null 2>&1; then
  DUMMY_JAR=$(mktemp).jar
  (cd "$(dirname "$DUMMY_JAR")" && echo '{}' | jar -cf "$DUMMY_JAR" -)
  aws lambda create-function \
    --function-name "$LAMBDA_FUNCTION_NAME" \
    --runtime java21 \
    --role "$LAMBDA_ROLE_ARN" \
    --handler com.flowboard.LambdaHandler \
    --memory-size 3008 \
    --timeout 120 \
    --vpc-config "SubnetIds=$PRIVATE_SUBNETS,SecurityGroupIds=$LAMBDA_SG" \
    --environment "$ENV_JSON" \
    --zip-file "fileb://$DUMMY_JAR" >/dev/null
  rm -f "$DUMMY_JAR"
  echo "Lambda function created"
else
  aws lambda update-function-configuration \
    --function-name "$LAMBDA_FUNCTION_NAME" \
    --vpc-config "SubnetIds=$PRIVATE_SUBNETS,SecurityGroupIds=$LAMBDA_SG" \
    --environment "$ENV_JSON" \
    --timeout 120 >/dev/null
  echo "Lambda function updated"
fi

LAMBDA_ARN=$(aws lambda get-function \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --query 'Configuration.FunctionArn' --output text)
echo "Lambda function ARN: $LAMBDA_ARN"

# API Gateway HTTP API
API_ID=$(aws apigatewayv2 get-apis \
  --query "Items[?Name=='$API_GATEWAY_NAME'].ApiId" --output text)

if [[ -z "$API_ID" || "$API_ID" == "None" ]]; then
  API_ID=$(aws apigatewayv2 create-api \
    --name "$API_GATEWAY_NAME" \
    --protocol-type HTTP \
    --target "$LAMBDA_ARN" \
    --query 'ApiId' --output text)
  echo "API Gateway HTTP API created: $API_ID"
else
  echo "API Gateway HTTP API already exists: $API_ID"
fi

# Integration
INTEGRATION_ID=$(aws apigatewayv2 get-integrations --api-id "$API_ID" \
  --query 'Items[0].IntegrationId' --output text)

if [[ -z "$INTEGRATION_ID" || "$INTEGRATION_ID" == "None" ]]; then
  INTEGRATION_ID=$(aws apigatewayv2 create-integration \
    --api-id "$API_ID" \
    --integration-type AWS_PROXY \
    --integration-uri "$LAMBDA_ARN" \
    --payload-format-version 2.0 \
    --query 'IntegrationId' --output text)
  echo "API Gateway integration created: $INTEGRATION_ID"
else
  echo "API Gateway integration already exists: $INTEGRATION_ID"
fi

# Routes
for ROUTE in "ANY /{proxy+}" "ANY /"; do
  ROUTE_KEY="$ROUTE"
  if ! aws apigatewayv2 get-routes --api-id "$API_ID" \
    --query "Items[?RouteKey=='$ROUTE_KEY'].RouteKey" --output text | grep -q "$ROUTE_KEY"; then
    aws apigatewayv2 create-route \
      --api-id "$API_ID" \
      --route-key "$ROUTE_KEY" \
      --target "integrations/$INTEGRATION_ID" >/dev/null
    echo "Route $ROUTE_KEY created"
  else
    echo "Route $ROUTE_KEY already exists"
  fi
done

# Default stage
if ! aws apigatewayv2 get-stage --api-id "$API_ID" --stage-name '\$default' >/dev/null 2>&1; then
  aws apigatewayv2 create-stage \
    --api-id "$API_ID" \
    --stage-name '$default' \
    --auto-deploy >/dev/null 2>&1 || true
  echo "Default stage created"
else
  echo "Default stage already exists"
fi

# Add Lambda permission for API Gateway
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
aws lambda add-permission \
  --function-name "$LAMBDA_FUNCTION_NAME" \
  --statement-id apigateway-invoke \
  --action lambda:InvokeFunction \
  --principal apigateway.amazonaws.com \
  --source-arn "arn:aws:execute-api:$AWS_REGION:$ACCOUNT_ID:$API_ID/*" >/dev/null 2>&1 || true

API_ENDPOINT="https://${API_ID}.execute-api.${AWS_REGION}.amazonaws.com"

echo "API Gateway endpoint: $API_ENDPOINT"

# Update state file
jq --arg lambda_role_arn "$LAMBDA_ROLE_ARN" \
   --arg lambda_arn "$LAMBDA_ARN" \
   --arg api_id "$API_ID" \
   --arg api_endpoint "$API_ENDPOINT" \
   '. + {lambda_role_arn: $lambda_role_arn, lambda_arn: $lambda_arn, api_id: $api_id, api_endpoint: $api_endpoint}' \
   "$STATE_FILE" > "${STATE_FILE}.tmp" && mv "${STATE_FILE}.tmp" "$STATE_FILE"

echo "=== Lambda & API Gateway Setup Complete ==="

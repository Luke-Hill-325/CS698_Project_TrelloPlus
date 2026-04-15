#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

if [[ ! -f "$STATE_FILE" ]]; then
  echo "WARNING: State file not found. Nothing to tear down."
  exit 0
fi

echo "=== Tearing Down OLD Lambda/RDS Infrastructure ==="

API_ID=$(jq -r '.api_id // empty' "$STATE_FILE" 2>/dev/null || true)
REST_API_ID=$(jq -r '.rest_api_id // empty' "$STATE_FILE" 2>/dev/null || true)
LAMBDA_ARN=$(jq -r '.lambda_arn // empty' "$STATE_FILE" 2>/dev/null || true)
LAMBDA_ROLE_ARN=$(jq -r '.lambda_role_arn // empty' "$STATE_FILE" 2>/dev/null || true)
PROXY_ENDPOINT=$(jq -r '.proxy_endpoint // empty' "$STATE_FILE" 2>/dev/null || true)
DB_ARN=$(jq -r '.db_arn // empty' "$STATE_FILE" 2>/dev/null || true)
SECRET_ARN=$(jq -r '.secret_arn // empty' "$STATE_FILE" 2>/dev/null || true)
PROXY_ROLE_ARN=$(jq -r '.proxy_role_arn // empty' "$STATE_FILE" 2>/dev/null || true)
DB_SG=$(jq -r '.db_sg // empty' "$STATE_FILE" 2>/dev/null || true)
LAMBDA_SG=$(jq -r '.lambda_sg // empty' "$STATE_FILE" 2>/dev/null || true)
PUBLIC_SUBNETS=$(jq -r '.public_subnets // empty' "$STATE_FILE" 2>/dev/null || true)
PRIVATE_SUBNETS=$(jq -r '.private_subnets // empty' "$STATE_FILE" 2>/dev/null || true)
PUBLIC_RT=$(jq -r '.public_rt // empty' "$STATE_FILE" 2>/dev/null || true)
PRIVATE_RT=$(jq -r '.private_rt // empty' "$STATE_FILE" 2>/dev/null || true)
IGW_ID=$(jq -r '.igw_id // empty' "$STATE_FILE" 2>/dev/null || true)
VPC_ID=$(jq -r '.vpc_id // empty' "$STATE_FILE" 2>/dev/null || true)

# API Gateway
if [[ -n "$API_ID" ]]; then
  echo "Deleting API Gateway HTTP API $API_ID..."
  aws apigatewayv2 delete-api --api-id "$API_ID" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

if [[ -n "$REST_API_ID" ]]; then
  echo "Deleting API Gateway REST API $REST_API_ID..."
  aws apigateway delete-rest-api --rest-api-id "$REST_API_ID" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

# Lambda
if [[ -n "$LAMBDA_ARN" ]]; then
  echo "Deleting Lambda function flowboard-backend..."
  aws lambda delete-function --function-name "flowboard-backend" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

# Lambda IAM Role
if [[ -n "$LAMBDA_ROLE_ARN" ]]; then
  LAMBDA_ROLE_NAME=$(basename "$LAMBDA_ROLE_ARN")
  echo "Detaching policies from IAM role $LAMBDA_ROLE_NAME..."
  aws iam detach-role-policy --role-name "$LAMBDA_ROLE_NAME" --policy-arn arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole >/dev/null 2>/dev/null || true
  aws iam detach-role-policy --role-name "$LAMBDA_ROLE_NAME" --policy-arn arn:aws:iam::aws:policy/CloudWatchLogsFullAccess >/dev/null 2>/dev/null || true
  echo "Deleting IAM role $LAMBDA_ROLE_NAME..."
  aws iam delete-role --role-name "$LAMBDA_ROLE_NAME" >/dev/null 2>/dev/null || true
fi

# RDS Proxy
if [[ -n "$PROXY_ENDPOINT" ]]; then
  echo "Deleting RDS Proxy flowboard-proxy..."
  aws rds delete-db-proxy --db-proxy-name "flowboard-proxy" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

# RDS Instance
if [[ -n "$DB_ARN" ]]; then
  DB_INSTANCE_ID=$(basename "$DB_ARN")
  echo "Deleting RDS instance $DB_INSTANCE_ID..."
  aws rds delete-db-instance \
    --db-instance-identifier "$DB_INSTANCE_ID" \
    --skip-final-snapshot \
    --delete-automated-backups \
    --region "$AWS_REGION" >/dev/null 2>/dev/null || true
  echo "Waiting for RDS instance deletion..."
  aws rds wait db-instance-deleted --db-instance-identifier "$DB_INSTANCE_ID" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

# DB Subnet Group
echo "Deleting DB subnet group flowboard-db-subnets..."
aws rds delete-db-subnet-group --db-subnet-group-name "flowboard-db-subnets" --region "$AWS_REGION" >/dev/null 2>/dev/null || true

# Secrets Manager
if [[ -n "$SECRET_ARN" ]]; then
  echo "Deleting Secrets Manager secret..."
  aws secretsmanager delete-secret --secret-id "$SECRET_ARN" --force-delete-without-recovery --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

# RDS Proxy IAM Role
if [[ -n "$PROXY_ROLE_ARN" ]]; then
  PROXY_ROLE_NAME=$(basename "$PROXY_ROLE_ARN")
  echo "Deleting RDS Proxy IAM role $PROXY_ROLE_NAME..."
  aws iam detach-role-policy --role-name "$PROXY_ROLE_NAME" --policy-arn arn:aws:iam::aws:policy/SecretsManagerReadWrite >/dev/null 2>/dev/null || true
  aws iam delete-role --role-name "$PROXY_ROLE_NAME" >/dev/null 2>/dev/null || true
fi

# Security Groups
if [[ -n "$LAMBDA_SG" && "$LAMBDA_SG" != "null" ]]; then
  echo "Deleting Lambda security group $LAMBDA_SG..."
  aws ec2 delete-security-group --group-id "$LAMBDA_SG" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi
if [[ -n "$DB_SG" && "$DB_SG" != "null" ]]; then
  echo "Deleting DB security group $DB_SG..."
  aws ec2 delete-security-group --group-id "$DB_SG" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

# Subnets
for subnet in $(echo "$PUBLIC_SUBNETS" | jq -r '.[]' 2>/dev/null || true); do
  if [[ -n "$subnet" && "$subnet" != "null" ]]; then
    echo "Deleting public subnet $subnet..."
    aws ec2 delete-subnet --subnet-id "$subnet" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
  fi
done
for subnet in $(echo "$PRIVATE_SUBNETS" | jq -r '.[]' 2>/dev/null || true); do
  if [[ -n "$subnet" && "$subnet" != "null" ]]; then
    echo "Deleting private subnet $subnet..."
    aws ec2 delete-subnet --subnet-id "$subnet" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
  fi
done

# Route Tables
if [[ -n "$PUBLIC_RT" && "$PUBLIC_RT" != "null" ]]; then
  echo "Deleting public route table $PUBLIC_RT..."
  aws ec2 delete-route-table --route-table-id "$PUBLIC_RT" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi
if [[ -n "$PRIVATE_RT" && "$PRIVATE_RT" != "null" ]]; then
  echo "Deleting private route table $PRIVATE_RT..."
  aws ec2 delete-route-table --route-table-id "$PRIVATE_RT" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

# Internet Gateway
if [[ -n "$IGW_ID" && "$IGW_ID" != "null" ]]; then
  echo "Detaching and deleting Internet Gateway $IGW_ID..."
  aws ec2 detach-internet-gateway --internet-gateway-id "$IGW_ID" --vpc-id "$VPC_ID" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
  aws ec2 delete-internet-gateway --internet-gateway-id "$IGW_ID" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

# VPC
if [[ -n "$VPC_ID" && "$VPC_ID" != "null" ]]; then
  echo "Deleting VPC $VPC_ID..."
  aws ec2 delete-vpc --vpc-id "$VPC_ID" --region "$AWS_REGION" >/dev/null 2>/dev/null || true
fi

echo "Old infrastructure teardown complete"

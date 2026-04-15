#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

if [[ ! -f "$STATE_FILE" ]]; then
  echo "WARNING: State file not found. Nothing to tear down."
  exit 0
fi

echo "=== Tearing Down AWS Resources ==="

API_ID=$(jq -r '.api_id // empty' "$STATE_FILE")
LAMBDA_ARN=$(jq -r '.lambda_arn // empty' "$STATE_FILE")
LAMBDA_ROLE_ARN=$(jq -r '.lambda_role_arn // empty' "$STATE_FILE")
DB_PROXY_NAME=$(jq -r '.db_proxy_name // empty' "$STATE_FILE")
DB_INSTANCE_ID=$(jq -r '.db_instance_id // empty' "$STATE_FILE")
DB_ARN=$(jq -r '.db_arn // empty' "$STATE_FILE")
SECRET_ARN=$(jq -r '.secret_arn // empty' "$STATE_FILE")
PROXY_ROLE_ARN=$(jq -r '.proxy_role_arn // empty' "$STATE_FILE")
VPC_ID=$(jq -r '.vpc_id // empty' "$STATE_FILE")
IGW_ID=$(jq -r '.igw_id // empty' "$STATE_FILE")

DB_SG=$(jq -r '.db_sg // empty' "$STATE_FILE")
LAMBDA_SG=$(jq -r '.lambda_sg // empty' "$STATE_FILE")
PUB_RT=$(jq -r '.public_rt // empty' "$STATE_FILE")
PRIV_RT=$(jq -r '.private_rt // empty' "$STATE_FILE")
PUBLIC_SUBNETS=$(jq -r '.public_subnets // empty' "$STATE_FILE")
PRIVATE_SUBNETS=$(jq -r '.private_subnets // empty' "$STATE_FILE")

# API Gateway
if [[ -n "$API_ID" ]]; then
  echo "Deleting API Gateway $API_ID..."
  aws apigatewayv2 delete-api --api-id "$API_ID" >/dev/null 2>/dev/null || true
fi

# Lambda
if [[ -n "$LAMBDA_ARN" ]]; then
  echo "Deleting Lambda function $LAMBDA_FUNCTION_NAME..."
  aws lambda delete-function --function-name "$LAMBDA_FUNCTION_NAME" >/dev/null 2>/dev/null || true
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
if [[ -n "$DB_PROXY_NAME" && "$DB_PROXY_NAME" != "null" && "$DB_PROXY_NAME" != "" ]]; then
  echo "Deleting RDS Proxy $DB_PROXY_NAME..."
  aws rds delete-db-proxy --db-proxy-name "$DB_PROXY_NAME" >/dev/null 2>/dev/null || true
fi

# RDS Instance
if [[ -n "$DB_INSTANCE_ID" && "$DB_INSTANCE_ID" != "null" && "$DB_INSTANCE_ID" != "" ]]; then
  echo "Deleting RDS instance $DB_INSTANCE_ID..."
  aws rds delete-db-instance \
    --db-instance-identifier "$DB_INSTANCE_ID" \
    --skip-final-snapshot \
    --delete-automated-backups >/dev/null 2>/dev/null || true
  echo "Waiting for RDS instance deletion..."
  aws rds wait db-instance-deleted --db-instance-identifier "$DB_INSTANCE_ID" >/dev/null 2>/dev/null || true
fi

# DB Subnet Group
echo "Deleting DB subnet group..."
aws rds delete-db-subnet-group --db-subnet-group-name "$PROJECT_NAME-db-subnets" >/dev/null 2>/dev/null || true

# Secrets Manager
if [[ -n "$SECRET_ARN" && "$SECRET_ARN" != "null" && "$SECRET_ARN" != "" ]]; then
  echo "Deleting Secrets Manager secret..."
  aws secretsmanager delete-secret --secret-id "$SECRET_ARN" --force-delete-without-recovery >/dev/null 2>/dev/null || true
fi

# RDS Proxy IAM Role
if [[ -n "$PROXY_ROLE_ARN" && "$PROXY_ROLE_ARN" != "null" && "$PROXY_ROLE_ARN" != "" ]]; then
  PROXY_ROLE_NAME=$(basename "$PROXY_ROLE_ARN")
  echo "Deleting RDS Proxy IAM role $PROXY_ROLE_NAME..."
  aws iam detach-role-policy --role-name "$PROXY_ROLE_NAME" --policy-arn arn:aws:iam::aws:policy/SecretsManagerReadWrite >/dev/null 2>/dev/null || true
  aws iam delete-role --role-name "$PROXY_ROLE_NAME" >/dev/null 2>/dev/null || true
fi

# Security Groups
if [[ -n "$LAMBDA_SG" && "$LAMBDA_SG" != "null" && "$LAMBDA_SG" != "" ]]; then
  echo "Deleting Lambda security group $LAMBDA_SG..."
  aws ec2 delete-security-group --group-id "$LAMBDA_SG" >/dev/null 2>/dev/null || true
fi
if [[ -n "$DB_SG" && "$DB_SG" != "null" && "$DB_SG" != "" ]]; then
  echo "Deleting DB security group $DB_SG..."
  aws ec2 delete-security-group --group-id "$DB_SG" >/dev/null 2>/dev/null || true
fi

# Subnets
for subnet in $(echo "$PUBLIC_SUBNETS" | jq -r '.[]' 2>/dev/null || true); do
  if [[ -n "$subnet" && "$subnet" != "null" ]]; then
    echo "Deleting subnet $subnet..."
    aws ec2 delete-subnet --subnet-id "$subnet" >/dev/null 2>/dev/null || true
  fi
done
for subnet in $(echo "$PRIVATE_SUBNETS" | jq -r '.[]' 2>/dev/null || true); do
  if [[ -n "$subnet" && "$subnet" != "null" ]]; then
    echo "Deleting subnet $subnet..."
    aws ec2 delete-subnet --subnet-id "$subnet" >/dev/null 2>/dev/null || true
  fi
done

# Route Tables
if [[ -n "$PUB_RT" && "$PUB_RT" != "null" && "$PUB_RT" != "" ]]; then
  echo "Deleting public route table $PUB_RT..."
  aws ec2 delete-route-table --route-table-id "$PUB_RT" >/dev/null 2>/dev/null || true
fi
if [[ -n "$PRIV_RT" && "$PRIV_RT" != "null" && "$PRIV_RT" != "" ]]; then
  echo "Deleting private route table $PRIV_RT..."
  aws ec2 delete-route-table --route-table-id "$PRIV_RT" >/dev/null 2>/dev/null || true
fi

# Internet Gateway
if [[ -n "$IGW_ID" && "$IGW_ID" != "null" && "$IGW_ID" != "" ]]; then
  echo "Detaching and deleting Internet Gateway $IGW_ID..."
  aws ec2 detach-internet-gateway --internet-gateway-id "$IGW_ID" --vpc-id "$VPC_ID" >/dev/null 2>/dev/null || true
  aws ec2 delete-internet-gateway --internet-gateway-id "$IGW_ID" >/dev/null 2>/dev/null || true
fi

# VPC
if [[ -n "$VPC_ID" && "$VPC_ID" != "null" && "$VPC_ID" != "" ]]; then
  echo "Deleting VPC $VPC_ID..."
  aws ec2 delete-vpc --vpc-id "$VPC_ID" >/dev/null 2>/dev/null || true
fi

# Clean up state file
rm -f "$STATE_FILE"

echo "=== Teardown Complete ==="

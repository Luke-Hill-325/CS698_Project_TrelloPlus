#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

if [[ ! -f "$STATE_FILE" ]]; then
  echo "ERROR: State file not found. Run 01-setup-vpc.sh first."
  exit 1
fi

VPC_ID=$(jq -r '.vpc_id' "$STATE_FILE")
PRIVATE_SUBNETS=$(jq -r '.private_subnets | join(",")' "$STATE_FILE")
DB_SG=$(jq -r '.db_sg' "$STATE_FILE")

echo "=== Setting up RDS PostgreSQL + RDS Proxy ==="

# DB Subnet Group
if ! aws rds describe-db-subnet-groups --db-subnet-group-name "$PROJECT_NAME-db-subnets" >/dev/null 2>&1; then
  aws rds create-db-subnet-group \
    --db-subnet-group-name "$PROJECT_NAME-db-subnets" \
    --db-subnet-group-description "Private subnets for FlowBoard RDS" \
    --subnet-ids $(echo "$PRIVATE_SUBNETS" | tr ',' ' ') >/dev/null
  echo "DB Subnet Group created"
else
  echo "DB Subnet Group already exists"
fi

# RDS Instance
if ! aws rds describe-db-instances --db-instance-identifier "$DB_INSTANCE_ID" >/dev/null 2>&1; then
  aws rds create-db-instance \
    --db-instance-identifier "$DB_INSTANCE_ID" \
    --db-instance-class db.t3.micro \
    --engine postgres \
    --engine-version 15.17 \
    --allocated-storage 20 \
    --storage-type gp2 \
    --master-username "$DB_USERNAME" \
    --master-user-password "$DB_PASSWORD" \
    --db-name "$DB_NAME" \
    --vpc-security-group-ids "$DB_SG" \
    --db-subnet-group-name "$PROJECT_NAME-db-subnets" \
    --backup-retention-period 7 \
    --no-publicly-accessible \
    --storage-encrypted \
    --enable-performance-insights \
    --performance-insights-retention-period 7 \
    --tags "Key=Name,Value=$DB_INSTANCE_ID" >/dev/null
  echo "RDS instance $DB_INSTANCE_ID is being created (this takes ~10-15 minutes)..."
  aws rds wait db-instance-available --db-instance-identifier "$DB_INSTANCE_ID"
else
  echo "RDS instance $DB_INSTANCE_ID already exists"
fi

DB_ARN=$(aws rds describe-db-instances \
  --db-instance-identifier "$DB_INSTANCE_ID" \
  --query 'DBInstances[0].DBInstanceArn' --output text)
DB_ENDPOINT=$(aws rds describe-db-instances \
  --db-instance-identifier "$DB_INSTANCE_ID" \
  --query 'DBInstances[0].Endpoint.Address' --output text)

echo "RDS instance available: $DB_ENDPOINT"

# Secrets Manager secret
SECRET_NAME="$PROJECT_NAME/db-credentials"
if ! aws secretsmanager describe-secret --secret-id "$SECRET_NAME" >/dev/null 2>&1; then
  SECRET_ARN=$(aws secretsmanager create-secret \
    --name "$SECRET_NAME" \
    --description "FlowBoard RDS credentials" \
    --secret-string "{\"username\":\"$DB_USERNAME\",\"password\":\"$DB_PASSWORD\"}" \
    --query 'ARN' --output text)
  echo "Secrets Manager secret created: $SECRET_ARN"
else
  SECRET_ARN=$(aws secretsmanager describe-secret --secret-id "$SECRET_NAME" --query 'ARN' --output text)
  echo "Secrets Manager secret already exists: $SECRET_ARN"
fi

# IAM role for RDS Proxy
RDS_PROXY_ROLE_NAME="$PROJECT_NAME-rds-proxy-role"
if ! aws iam get-role --role-name "$RDS_PROXY_ROLE_NAME" >/dev/null 2>&1; then
  TRUST_POLICY='{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"rds.amazonaws.com"},"Action":"sts:AssumeRole"}]}'
  RDS_PROXY_ROLE_ARN=$(aws iam create-role \
    --role-name "$RDS_PROXY_ROLE_NAME" \
    --assume-role-policy-document "$TRUST_POLICY" \
    --query 'Role.Arn' --output text)
  aws iam attach-role-policy \
    --role-name "$RDS_PROXY_ROLE_NAME" \
    --policy-arn arn:aws:iam::aws:policy/SecretsManagerReadWrite >/dev/null
  sleep 10
  echo "RDS Proxy IAM role created"
else
  RDS_PROXY_ROLE_ARN=$(aws iam get-role --role-name "$RDS_PROXY_ROLE_NAME" --query 'Role.Arn' --output text)
  echo "RDS Proxy IAM role already exists"
fi

# RDS Proxy
if ! aws rds describe-db-proxies --db-proxy-name "$DB_PROXY_NAME" >/dev/null 2>&1; then
  aws rds create-db-proxy \
    --db-proxy-name "$DB_PROXY_NAME" \
    --engine-family POSTGRESQL \
    --auth "{\"AuthScheme\":\"SECRETS\",\"SecretArn\":\"$SECRET_ARN\",\"IAMAuth\":\"DISABLED\"}" \
    --role-arn "$RDS_PROXY_ROLE_ARN" \
    --vpc-subnet-ids $(echo "$PRIVATE_SUBNETS" | tr ',' ' ') \
    --vpc-security-group-ids "$DB_SG" >/dev/null
  echo "RDS Proxy $DB_PROXY_NAME is being created..."
  aws rds wait db-proxy-available --db-proxy-name "$DB_PROXY_NAME"
else
  echo "RDS Proxy $DB_PROXY_NAME already exists"
fi

PROXY_ENDPOINT=$(aws rds describe-db-proxies \
  --db-proxy-name "$DB_PROXY_NAME" \
  --query 'DBProxies[0].Endpoint' --output text)

# Register DB instance with proxy
aws rds register-db-proxy-targets \
  --db-proxy-name "$DB_PROXY_NAME" \
  --db-instance-identifiers "$DB_INSTANCE_ID" >/dev/null 2>&1 || true

echo "RDS Proxy available: $PROXY_ENDPOINT"

# Update state file
jq --arg db_arn "$DB_ARN" \
   --arg db_endpoint "$DB_ENDPOINT" \
   --arg secret_arn "$SECRET_ARN" \
   --arg proxy_role_arn "$RDS_PROXY_ROLE_ARN" \
   --arg proxy_endpoint "$PROXY_ENDPOINT" \
   '. + {db_arn: $db_arn, db_endpoint: $db_endpoint, secret_arn: $secret_arn, proxy_role_arn: $proxy_role_arn, proxy_endpoint: $proxy_endpoint}' \
   "$STATE_FILE" > "${STATE_FILE}.tmp" && mv "${STATE_FILE}.tmp" "$STATE_FILE"

echo "=== RDS Setup Complete ==="
echo "DB URL: jdbc:postgresql://$PROXY_ENDPOINT:5432/$DB_NAME"

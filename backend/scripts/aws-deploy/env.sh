#!/bin/bash
set -euo pipefail

# AWS Lambda Deployment Configuration
# Source this file before running other deployment scripts:
#   source backend/scripts/aws-deploy/env.sh

export AWS_REGION="${AWS_REGION:-us-east-1}"
export AWS_PAGER=""

# Deployment strategy:
#   No NAT Gateway (~$25-30/month). AI always runs in mock mode.
#   (External LLM access is not supported in this configuration.)

export PROJECT_NAME="flowboard"
export VPC_CIDR="10.0.0.0/16"

# State file for tracking created resource IDs (must be set before reading persisted credentials)
export STATE_FILE="${STATE_FILE:-$(pwd)/.aws-deploy-state.json}"

# Database credentials (auto-generated if not provided, persisted in state file)
export DB_NAME="flowboard"
export DB_USERNAME="flowboard"

if [[ -f "${STATE_FILE}" ]] && jq -e '.db_password' "${STATE_FILE}" >/dev/null 2>&1; then
  export DB_PASSWORD="$(jq -r '.db_password' "${STATE_FILE}")"
else
  export DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 32)}"
fi

# JWT settings (auto-generated if not provided, persisted in state file)
if [[ -f "${STATE_FILE}" ]] && jq -e '.jwt_secret' "${STATE_FILE}" >/dev/null 2>&1; then
  export JWT_SECRET="$(jq -r '.jwt_secret' "${STATE_FILE}")"
else
  export JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48 | tr -dc 'a-zA-Z0-9' | head -c 48)}"
fi
export JWT_EXPIRATION="86400000"

# AI mode: always mock for Lambda deployment
export AI_MOCK_ENABLED="true"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS:-*}"

# Derived resource names
export VPC_NAME="${PROJECT_NAME}-vpc"
export DB_INSTANCE_ID="${PROJECT_NAME}-db"
export DB_PROXY_NAME="${PROJECT_NAME}-proxy"
export LAMBDA_ROLE_NAME="${PROJECT_NAME}-lambda-role"
export LAMBDA_FUNCTION_NAME="${PROJECT_NAME}-backend"
export API_GATEWAY_NAME="${PROJECT_NAME}-api"

echo "=================================================="
echo "FlowBoard AWS Lambda Deployment Configuration"
echo "=================================================="
echo "Region:        $AWS_REGION"
echo "Mode:          economy (mock AI only)"
echo "State File:    $STATE_FILE"
echo "DB Password:   ${DB_PASSWORD:0:4}****"
echo "JWT Secret:    ${JWT_SECRET:0:4}****"
echo "AI Mock:       $AI_MOCK_ENABLED"
echo "=================================================="

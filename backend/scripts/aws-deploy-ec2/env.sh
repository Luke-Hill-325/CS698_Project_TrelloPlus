#!/bin/bash
set -euo pipefail

export AWS_REGION="${AWS_REGION:-us-east-1}"
export AWS_PAGER=""

export PROJECT_NAME="flowboard"

# Resolve script directory for env.sh itself
ENV_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export STATE_FILE="${ENV_SCRIPT_DIR}/../../../.aws-deploy-state.json"

export EC2_INSTANCE_TYPE="t3.micro"
export EC2_KEY_NAME="flowboard-ec2-key"

# Get latest Amazon Linux 2023 AMI for x86_64
export AMI_ID=$(aws ssm get-parameters \
  --names /aws/service/ami-amazon-linux-latest/al2023-ami-kernel-6.1-x86_64 \
  --region "$AWS_REGION" \
  --query 'Parameters[0].Value' \
  --output text)

# JWT settings (auto-generated if not provided, persisted in state file)
if [[ -f "${STATE_FILE}" ]] && jq -e '.jwt_secret' "${STATE_FILE}" >/dev/null 2>&1; then
  export JWT_SECRET="$(jq -r '.jwt_secret' "${STATE_FILE}")"
else
  export JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48 | tr -dc 'a-zA-Z0-9' | head -c 48)}"
fi

export AI_MOCK_ENABLED="true"

echo "=================================================="
echo "FlowBoard AWS EC2 Deployment Configuration"
echo "=================================================="
echo "Region:        $AWS_REGION"
echo "State File:    $STATE_FILE"
echo "AMI ID:        $AMI_ID"
echo "JWT Secret:    ${JWT_SECRET:0:4}****"
echo "AI Mock:       $AI_MOCK_ENABLED"
echo "=================================================="

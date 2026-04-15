#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

if [[ ! -f "$STATE_FILE" ]]; then
  echo "WARNING: State file not found. Nothing to tear down."
  exit 0
fi

echo "=== Tearing Down EC2 Infrastructure ==="

INSTANCE_ID=$(jq -r '.ec2_instance_id // empty' "$STATE_FILE" 2>/dev/null || true)
SG_ID=$(jq -r '.ec2_sg // empty' "$STATE_FILE" 2>/dev/null || true)
SUBNET_ID=$(jq -r '.ec2_public_subnet // empty' "$STATE_FILE" 2>/dev/null || true)
RT_ID=$(jq -r '.ec2_public_rt // empty' "$STATE_FILE" 2>/dev/null || true)
IGW_ID=$(jq -r '.ec2_igw_id // empty' "$STATE_FILE" 2>/dev/null || true)
VPC_ID=$(jq -r '.ec2_vpc_id // empty' "$STATE_FILE" 2>/dev/null || true)

# Terminate EC2 instance
if [[ -n "$INSTANCE_ID" && "$INSTANCE_ID" != "null" ]]; then
  echo "Terminating EC2 instance $INSTANCE_ID..."
  aws ec2 terminate-instances --instance-ids "$INSTANCE_ID" --region "$AWS_REGION" >/dev/null 2>&1 || true
  echo "Waiting for instance termination..."
  aws ec2 wait instance-terminated --instance-ids "$INSTANCE_ID" --region "$AWS_REGION" >/dev/null 2>&1 || true
fi

# Delete security group
if [[ -n "$SG_ID" && "$SG_ID" != "null" ]]; then
  echo "Deleting security group $SG_ID..."
  aws ec2 delete-security-group --group-id "$SG_ID" --region "$AWS_REGION" >/dev/null 2>&1 || true
fi

# Delete subnet
if [[ -n "$SUBNET_ID" && "$SUBNET_ID" != "null" ]]; then
  echo "Deleting subnet $SUBNET_ID..."
  aws ec2 delete-subnet --subnet-id "$SUBNET_ID" --region "$AWS_REGION" >/dev/null 2>&1 || true
fi

# Delete route table
if [[ -n "$RT_ID" && "$RT_ID" != "null" ]]; then
  echo "Deleting route table $RT_ID..."
  aws ec2 delete-route-table --route-table-id "$RT_ID" --region "$AWS_REGION" >/dev/null 2>&1 || true
fi

# Detach and delete IGW
if [[ -n "$IGW_ID" && "$IGW_ID" != "null" ]]; then
  echo "Detaching and deleting IGW $IGW_ID..."
  aws ec2 detach-internet-gateway --internet-gateway-id "$IGW_ID" --vpc-id "$VPC_ID" --region "$AWS_REGION" >/dev/null 2>&1 || true
  aws ec2 delete-internet-gateway --internet-gateway-id "$IGW_ID" --region "$AWS_REGION" >/dev/null 2>&1 || true
fi

# Delete VPC
if [[ -n "$VPC_ID" && "$VPC_ID" != "null" ]]; then
  echo "Deleting VPC $VPC_ID..."
  aws ec2 delete-vpc --vpc-id "$VPC_ID" --region "$AWS_REGION" >/dev/null 2>&1 || true
fi

# Delete key pair
echo "Deleting key pair $EC2_KEY_NAME..."
aws ec2 delete-key-pair --key-name "$EC2_KEY_NAME" --region "$AWS_REGION" >/dev/null 2>&1 || true
rm -f "${SCRIPT_DIR}/../../../flowboard-ec2-key.pem"

# Delete IAM instance profile and role
ROLE_NAME="flowboard-ec2-role"
INSTANCE_PROFILE_NAME="flowboard-ec2-profile"

echo "Removing role from instance profile..."
aws iam remove-role-from-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" --role-name "$ROLE_NAME" >/dev/null 2>&1 || true

echo "Deleting instance profile..."
aws iam delete-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" >/dev/null 2>&1 || true

echo "Detaching policies from IAM role..."
aws iam detach-role-policy --role-name "$ROLE_NAME" --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore >/dev/null 2>&1 || true
aws iam detach-role-policy --role-name "$ROLE_NAME" --policy-arn arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess >/dev/null 2>&1 || true

echo "Deleting IAM role..."
aws iam delete-role --role-name "$ROLE_NAME" >/dev/null 2>&1 || true

# Optionally delete S3 bucket
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text 2>/dev/null || echo "")
BUCKET_NAME="flowboard-ec2-deploy-$ACCOUNT_ID"
if [[ -n "$ACCOUNT_ID" ]]; then
  echo "Deleting S3 bucket $BUCKET_NAME (if exists)..."
  aws s3 rb "s3://$BUCKET_NAME" --force --region "$AWS_REGION" >/dev/null 2>&1 || true
fi

# Remove EC2 keys from state file
if [[ -f "$STATE_FILE" ]]; then
  jq 'del(.ec2_vpc_id, .ec2_igw_id, .ec2_public_subnet, .ec2_public_rt, .ec2_sg, .ec2_instance_id, .ec2_public_ip, .ec2_ami_id)' "$STATE_FILE" > "${STATE_FILE}.tmp" && mv "${STATE_FILE}.tmp" "$STATE_FILE" || true
fi

echo "EC2 infrastructure teardown complete"

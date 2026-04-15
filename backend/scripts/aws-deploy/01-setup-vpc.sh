#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

echo "=== Setting up VPC and Networking ==="

# Create VPC
VPC_ID=$(aws ec2 create-vpc \
  --cidr-block "$VPC_CIDR" \
  --tag-specifications "ResourceType=vpc,Tags=[{Key=Name,Value=$VPC_NAME}]" \
  --query 'Vpc.VpcId' --output text)
aws ec2 modify-vpc-attribute --vpc-id "$VPC_ID" --enable-dns-hostnames "{\"Value\":true}"
echo "VPC created: $VPC_ID"

# Create Internet Gateway
IGW_ID=$(aws ec2 create-internet-gateway \
  --tag-specifications "ResourceType=internet-gateway,Tags=[{Key=Name,Value=$VPC_NAME-igw}]" \
  --query 'InternetGateway.InternetGatewayId' --output text)
aws ec2 attach-internet-gateway --internet-gateway-id "$IGW_ID" --vpc-id "$VPC_ID"
echo "IGW created: $IGW_ID"

# Create subnets across two AZs
AZS=($(aws ec2 describe-availability-zones \
  --query 'AvailabilityZones[?State==`available`].ZoneName' --output text | tr '\t' '\n' | head -n 2))

PUBLIC_SUBNETS=()
PRIVATE_SUBNETS=()

for i in "${!AZS[@]}"; do
  AZ="${AZS[$i]}"
  IDX=$((i+1))

  PUB_SUBNET=$(aws ec2 create-subnet \
    --vpc-id "$VPC_ID" \
    --cidr-block "10.0.${IDX}.0/24" \
    --availability-zone "$AZ" \
    --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=$VPC_NAME-public-$IDX}]" \
    --query 'Subnet.SubnetId' --output text)
  aws ec2 modify-subnet-attribute --subnet-id "$PUB_SUBNET" --map-public-ip-on-launch
  PUBLIC_SUBNETS+=("$PUB_SUBNET")
  echo "Public subnet $IDX created: $PUB_SUBNET"

  PRIV_SUBNET=$(aws ec2 create-subnet \
    --vpc-id "$VPC_ID" \
    --cidr-block "10.0.1${IDX}.0/24" \
    --availability-zone "$AZ" \
    --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=$VPC_NAME-private-$IDX}]" \
    --query 'Subnet.SubnetId' --output text)
  PRIVATE_SUBNETS+=("$PRIV_SUBNET")
  echo "Private subnet $IDX created: $PRIV_SUBNET"
done

# Public route table
PUB_RT=$(aws ec2 create-route-table \
  --vpc-id "$VPC_ID" \
  --tag-specifications "ResourceType=route-table,Tags=[{Key=Name,Value=$VPC_NAME-public-rt}]" \
  --query 'RouteTable.RouteTableId' --output text)
aws ec2 create-route --route-table-id "$PUB_RT" --destination-cidr-block 0.0.0.0/0 --gateway-id "$IGW_ID" >/dev/null
for subnet in "${PUBLIC_SUBNETS[@]}"; do
  aws ec2 associate-route-table --route-table-id "$PUB_RT" --subnet-id "$subnet" >/dev/null
done
echo "Public route table created: $PUB_RT"

# Private route table
PRIV_RT=$(aws ec2 create-route-table \
  --vpc-id "$VPC_ID" \
  --tag-specifications "ResourceType=route-table,Tags=[{Key=Name,Value=$VPC_NAME-private-rt}]" \
  --query 'RouteTable.RouteTableId' --output text)
for subnet in "${PRIVATE_SUBNETS[@]}"; do
  aws ec2 associate-route-table --route-table-id "$PRIV_RT" --subnet-id "$subnet" >/dev/null
done
echo "Private route table created: $PRIV_RT"

# Security Groups
DB_SG=$(aws ec2 create-security-group \
  --group-name "$PROJECT_NAME-db-sg" \
  --description "RDS PostgreSQL access" \
  --vpc-id "$VPC_ID" \
  --query 'GroupId' --output text)
aws ec2 authorize-security-group-ingress \
  --group-id "$DB_SG" \
  --protocol tcp --port 5432 --source-group "$DB_SG" >/dev/null
echo "DB Security Group created: $DB_SG"

LAMBDA_SG=$(aws ec2 create-security-group \
  --group-name "$PROJECT_NAME-lambda-sg" \
  --description "Lambda VPC access" \
  --vpc-id "$VPC_ID" \
  --query 'GroupId' --output text)
# Allow Lambda to talk to RDS
aws ec2 authorize-security-group-ingress \
  --group-id "$DB_SG" \
  --protocol tcp --port 5432 --source-group "$LAMBDA_SG" >/dev/null
echo "Lambda Security Group created: $LAMBDA_SG"

# Save state
cat > "$STATE_FILE" <<EOF
{
  "vpc_id": "$VPC_ID",
  "igw_id": "$IGW_ID",
  "public_subnets": ["${PUBLIC_SUBNETS[0]}", "${PUBLIC_SUBNETS[1]}"],
  "private_subnets": ["${PRIVATE_SUBNETS[0]}", "${PRIVATE_SUBNETS[1]}"],
  "public_rt": "$PUB_RT",
  "private_rt": "$PRIV_RT",
  "db_sg": "$DB_SG",
  "lambda_sg": "$LAMBDA_SG",
  "db_password": "$DB_PASSWORD",
  "jwt_secret": "$JWT_SECRET"
}
EOF

echo "=== VPC Setup Complete ==="
echo "State saved to: $STATE_FILE"

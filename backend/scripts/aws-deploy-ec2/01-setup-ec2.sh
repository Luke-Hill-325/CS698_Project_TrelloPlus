#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

echo "=== Creating EC2 Infrastructure ==="

# Create IAM role and instance profile
ROLE_NAME="flowboard-ec2-role"
INSTANCE_PROFILE_NAME="flowboard-ec2-profile"

if ! aws iam get-role --role-name "$ROLE_NAME" >/dev/null 2>&1; then
  echo "Creating IAM role $ROLE_NAME..."
  aws iam create-role \
    --role-name "$ROLE_NAME" \
    --assume-role-policy-document '{
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Principal": { "Service": "ec2.amazonaws.com" },
          "Action": "sts:AssumeRole"
        }
      ]
    }' >/dev/null
fi

echo "Attaching policies to IAM role $ROLE_NAME..."
aws iam attach-role-policy --role-name "$ROLE_NAME" --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore >/dev/null 2>&1 || true
aws iam attach-role-policy --role-name "$ROLE_NAME" --policy-arn arn:aws:iam::aws:policy/AmazonS3ReadOnlyAccess >/dev/null 2>&1 || true

if ! aws iam get-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" >/dev/null 2>&1; then
  echo "Creating instance profile $INSTANCE_PROFILE_NAME..."
  aws iam create-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" >/dev/null
  echo "Adding role to instance profile..."
  aws iam add-role-to-instance-profile --instance-profile-name "$INSTANCE_PROFILE_NAME" --role-name "$ROLE_NAME" >/dev/null
  # Wait for instance profile propagation
  sleep 10
fi

# Create VPC
VPC_CIDR="10.0.0.0/16"
VPC_ID=$(aws ec2 create-vpc \
  --cidr-block "$VPC_CIDR" \
  --tag-specifications "ResourceType=vpc,Tags=[{Key=Name,Value=flowboard-ec2-vpc}]" \
  --region "$AWS_REGION" \
  --query 'Vpc.VpcId' \
  --output text)
echo "Created VPC: $VPC_ID"

aws ec2 modify-vpc-attribute --vpc-id "$VPC_ID" --enable-dns-hostnames --region "$AWS_REGION"

# Create and attach IGW
IGW_ID=$(aws ec2 create-internet-gateway \
  --tag-specifications "ResourceType=internet-gateway,Tags=[{Key=Name,Value=flowboard-ec2-igw}]" \
  --region "$AWS_REGION" \
  --query 'InternetGateway.InternetGatewayId' \
  --output text)
echo "Created IGW: $IGW_ID"

aws ec2 attach-internet-gateway --internet-gateway-id "$IGW_ID" --vpc-id "$VPC_ID" --region "$AWS_REGION"

# Get first availability zone
AZ=$(aws ec2 describe-availability-zones \
  --region "$AWS_REGION" \
  --filters "Name=state,Values=available" \
  --query 'AvailabilityZones[0].ZoneName' \
  --output text)

# Create public subnet
PUBLIC_SUBNET_ID=$(aws ec2 create-subnet \
  --vpc-id "$VPC_ID" \
  --cidr-block "10.0.1.0/24" \
  --availability-zone "$AZ" \
  --tag-specifications "ResourceType=subnet,Tags=[{Key=Name,Value=flowboard-ec2-public-subnet}]" \
  --region "$AWS_REGION" \
  --query 'Subnet.SubnetId' \
  --output text)
echo "Created public subnet: $PUBLIC_SUBNET_ID"

aws ec2 modify-subnet-attribute --subnet-id "$PUBLIC_SUBNET_ID" --map-public-ip-on-launch --region "$AWS_REGION"

# Create public route table
PUBLIC_RT_ID=$(aws ec2 create-route-table \
  --vpc-id "$VPC_ID" \
  --tag-specifications "ResourceType=route-table,Tags=[{Key=Name,Value=flowboard-ec2-public-rt}]" \
  --region "$AWS_REGION" \
  --query 'RouteTable.RouteTableId' \
  --output text)
echo "Created public route table: $PUBLIC_RT_ID"

aws ec2 create-route --route-table-id "$PUBLIC_RT_ID" --destination-cidr-block 0.0.0.0/0 --gateway-id "$IGW_ID" --region "$AWS_REGION" >/dev/null
aws ec2 associate-route-table --route-table-id "$PUBLIC_RT_ID" --subnet-id "$PUBLIC_SUBNET_ID" --region "$AWS_REGION" >/dev/null

# Create security group
SG_ID=$(aws ec2 create-security-group \
  --group-name "flowboard-ec2-sg" \
  --description "Security group for FlowBoard EC2 instance" \
  --vpc-id "$VPC_ID" \
  --region "$AWS_REGION" \
  --query 'GroupId' \
  --output text)
echo "Created security group: $SG_ID"

aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 22 --cidr 0.0.0.0/0 --region "$AWS_REGION" >/dev/null 2>&1 || true
aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 80 --cidr 0.0.0.0/0 --region "$AWS_REGION" >/dev/null 2>&1 || true
aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 443 --cidr 0.0.0.0/0 --region "$AWS_REGION" >/dev/null 2>&1 || true
aws ec2 authorize-security-group-ingress --group-id "$SG_ID" --protocol tcp --port 8080 --cidr 0.0.0.0/0 --region "$AWS_REGION" >/dev/null 2>&1 || true

# Create key pair
KEY_FILE="${SCRIPT_DIR}/../../../flowboard-ec2-key.pem"
if ! aws ec2 describe-key-pairs --key-names "$EC2_KEY_NAME" --region "$AWS_REGION" >/dev/null 2>&1; then
  echo "Creating key pair $EC2_KEY_NAME..."
  aws ec2 create-key-pair \
    --key-name "$EC2_KEY_NAME" \
    --region "$AWS_REGION" \
    --query 'KeyMaterial' \
    --output text > "$KEY_FILE"
  chmod 400 "$KEY_FILE"
else
  echo "Key pair $EC2_KEY_NAME already exists."
fi

# User data script
USER_DATA=$(cat <<'EOF'
#!/bin/bash
yum update -y
yum install -y java-21-amazon-corretto nginx
mkdir -p /opt/flowboard/data
chmod 755 /opt/flowboard/data
systemctl enable nginx
EOF
)

# Launch EC2 instance
INSTANCE_ID=$(aws ec2 run-instances \
  --image-id "$AMI_ID" \
  --instance-type "$EC2_INSTANCE_TYPE" \
  --key-name "$EC2_KEY_NAME" \
  --security-group-ids "$SG_ID" \
  --subnet-id "$PUBLIC_SUBNET_ID" \
  --iam-instance-profile "Name=$INSTANCE_PROFILE_NAME" \
  --user-data "$USER_DATA" \
  --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=flowboard-ec2}]" \
  --region "$AWS_REGION" \
  --query 'Instances[0].InstanceId' \
  --output text)
echo "Launched EC2 instance: $INSTANCE_ID"

echo "Waiting for instance to be running..."
aws ec2 wait instance-running --instance-ids "$INSTANCE_ID" --region "$AWS_REGION"

# Get public IP
PUBLIC_IP=$(aws ec2 describe-instances \
  --instance-ids "$INSTANCE_ID" \
  --region "$AWS_REGION" \
  --query 'Reservations[0].Instances[0].PublicIpAddress' \
  --output text)

echo "EC2 instance is running at $PUBLIC_IP"

# Update state file
if [[ -f "$STATE_FILE" ]]; then
  jq --arg vpc_id "$VPC_ID" \
     --arg igw_id "$IGW_ID" \
     --arg public_subnet "$PUBLIC_SUBNET_ID" \
     --arg public_rt "$PUBLIC_RT_ID" \
     --arg sg "$SG_ID" \
     --arg instance_id "$INSTANCE_ID" \
     --arg public_ip "$PUBLIC_IP" \
     --arg ami_id "$AMI_ID" \
     '. + {
       ec2_vpc_id: $vpc_id,
       ec2_igw_id: $igw_id,
       ec2_public_subnet: $public_subnet,
       ec2_public_rt: $public_rt,
       ec2_sg: $sg,
       ec2_instance_id: $instance_id,
       ec2_public_ip: $public_ip,
       ec2_ami_id: $ami_id
     }' "$STATE_FILE" > "${STATE_FILE}.tmp" && mv "${STATE_FILE}.tmp" "$STATE_FILE"
else
  cat > "$STATE_FILE" <<EOF
{
  "ec2_vpc_id": "$VPC_ID",
  "ec2_igw_id": "$IGW_ID",
  "ec2_public_subnet": "$PUBLIC_SUBNET_ID",
  "ec2_public_rt": "$PUBLIC_RT_ID",
  "ec2_sg": "$SG_ID",
  "ec2_instance_id": "$INSTANCE_ID",
  "ec2_public_ip": "$PUBLIC_IP",
  "ec2_ami_id": "$AMI_ID",
  "jwt_secret": "$JWT_SECRET"
}
EOF
fi

echo ""
echo "=== EC2 Infrastructure Created ==="
echo "Public IP: $PUBLIC_IP"
echo "SSH: ssh -i $KEY_FILE ec2-user@$PUBLIC_IP"

#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/env.sh"

if [[ ! -f "$STATE_FILE" ]]; then
  echo "ERROR: State file not found. Run 01-setup-ec2.sh first."
  exit 1
fi

EC2_PUBLIC_IP=$(jq -r '.ec2_public_ip // empty' "$STATE_FILE")
EC2_INSTANCE_ID=$(jq -r '.ec2_instance_id // empty' "$STATE_FILE")

if [[ -z "$EC2_PUBLIC_IP" || -z "$EC2_INSTANCE_ID" ]]; then
  echo "ERROR: EC2 instance info not found in state file. Run 01-setup-ec2.sh first."
  exit 1
fi

KEY_FILE="${SCRIPT_DIR}/../../../flowboard-ec2-key.pem"

echo "=== Building Backend ==="
cd "${SCRIPT_DIR}/../../../backend"
mvn clean package -DskipTests -Dspring.profiles.active=ec2

BACKEND_JAR="${SCRIPT_DIR}/../../../backend/target/flowboard-backend-1.0.0.jar"
if [[ ! -f "$BACKEND_JAR" ]]; then
  echo "ERROR: Backend JAR not found at $BACKEND_JAR"
  exit 1
fi

echo "=== Building Frontend ==="
cd "${SCRIPT_DIR}/../../.."
npm ci
VITE_API_BASE_URL=/api/v1 npm run build

if [[ ! -d "dist" ]]; then
  echo "ERROR: dist/ not found after build"
  exit 1
fi

echo "=== Uploading to S3 ==="
ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)
BUCKET_NAME="flowboard-ec2-deploy-$ACCOUNT_ID"

if ! aws s3api head-bucket --bucket "$BUCKET_NAME" 2>/dev/null; then
  echo "Creating S3 bucket $BUCKET_NAME..."
  aws s3 mb "s3://$BUCKET_NAME" --region "$AWS_REGION"
fi

aws s3 cp "$BACKEND_JAR" "s3://$BUCKET_NAME/flowboard-backend-1.0.0.jar"

DIST_TAR="/tmp/flowboard-frontend.tar.gz"
tar -czf "$DIST_TAR" -C dist .

aws s3 cp "$DIST_TAR" "s3://$BUCKET_NAME/flowboard-frontend.tar.gz"

echo "=== Deploying to EC2 ==="

# Create systemd service file locally
SERVICE_FILE="/tmp/flowboard.service"
cat > "$SERVICE_FILE" <<EOF
[Unit]
Description=FlowBoard Backend Service
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/flowboard/data/
Environment="JWT_SECRET=$JWT_SECRET"
Environment="AI_MOCK_ENABLED=true"
ExecStart=/usr/bin/java -jar -Dspring.profiles.active=ec2 /opt/flowboard/flowboard-backend-1.0.0.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF

# Create nginx config locally
NGINX_CONF="/tmp/nginx-flowboard.conf"
cat > "$NGINX_CONF" <<'EOF'
server {
    listen 80;
    server_name _;

    root /opt/flowboard/frontend;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/v1/ {
        proxy_pass http://127.0.0.1:8080/api/v1/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
EOF

# Wait for SSH to be available
echo "Waiting for SSH on $EC2_PUBLIC_IP..."
for i in {1..30}; do
  if ssh -o StrictHostKeyChecking=no -o ConnectTimeout=5 -i "$KEY_FILE" "ec2-user@$EC2_PUBLIC_IP" "echo ready" >/dev/null 2>&1; then
    echo "SSH is ready"
    break
  fi
  echo "SSH not ready yet, waiting..."
  sleep 10
done

# Download artifacts on EC2 via S3 (instance profile provides access)
ssh -o StrictHostKeyChecking=no -i "$KEY_FILE" "ec2-user@$EC2_PUBLIC_IP" <<DEPLOY
set -e
sudo mkdir -p /opt/flowboard/data /opt/flowboard/frontend
sudo chown -R ec2-user:ec2-user /opt/flowboard
aws s3 cp s3://$BUCKET_NAME/flowboard-backend-1.0.0.jar /opt/flowboard/flowboard-backend-1.0.0.jar
aws s3 cp s3://$BUCKET_NAME/flowboard-frontend.tar.gz /tmp/flowboard-frontend.tar.gz
tar -xzf /tmp/flowboard-frontend.tar.gz -C /opt/flowboard/frontend
rm -f /tmp/flowboard-frontend.tar.gz
DEPLOY

# Upload service file and nginx config
scp -o StrictHostKeyChecking=no -i "$KEY_FILE" "$SERVICE_FILE" "ec2-user@$EC2_PUBLIC_IP:/tmp/flowboard.service"
scp -o StrictHostKeyChecking=no -i "$KEY_FILE" "$NGINX_CONF" "ec2-user@$EC2_PUBLIC_IP:/tmp/nginx-flowboard.conf"

# Move files into place and restart services
ssh -o StrictHostKeyChecking=no -i "$KEY_FILE" "ec2-user@$EC2_PUBLIC_IP" <<DEPLOY
set -e
sudo mv /tmp/flowboard.service /etc/systemd/system/flowboard.service
sudo mv /tmp/nginx-flowboard.conf /etc/nginx/conf.d/flowboard.conf

# Remove default server block if present
sudo rm -f /etc/nginx/conf.d/default.conf

sudo systemctl daemon-reload
sudo systemctl enable flowboard
sudo systemctl restart flowboard

sudo nginx -t && sudo systemctl restart nginx
DEPLOY

echo "=== Deployment Complete ==="
echo "Public URL: http://$EC2_PUBLIC_IP/"

# Cleanup temp files
rm -f "$SERVICE_FILE" "$NGINX_CONF" "$DIST_TAR"

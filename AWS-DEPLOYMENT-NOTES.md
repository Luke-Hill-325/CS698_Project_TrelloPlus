# AWS Deployment Notes

## Architecture Overview

```
Internet → API Gateway → Lambda (in VPC, no NAT) → RDS Proxy → RDS PostgreSQL
                              ↓
                        ❌ Cannot reach internet
                        (no NAT Gateway for cost savings)
```

## Deployment Configuration

### VPC Setup
- **VPC**: `10.0.0.0/16` with 2 subnets in different AZs
- **NAT Gateway**: NOT deployed (saves ~$32/month)
- **Internet Gateway**: Only for RDS public access during initial setup (will be disabled)

### Lambda
- **Runtime**: Java 21
- **Memory**: 1024 MB
- **VPC**: Yes (required for RDS access)
- **Internet Access**: No (no NAT Gateway)

### Database
- **Type**: RDS PostgreSQL (db.t3.micro)
- **Connection**: Via RDS Proxy (connection pooling)
- **Public Access**: Initially enabled for migration, then disabled

## ⚠️ Important Limitation: External LLM Access

**Current Setup**: Lambda functions in VPC **without NAT Gateway** cannot reach the internet.

### What This Means

| Service | Can Lambda Reach It? | Workaround |
|---------|---------------------|------------|
| RDS PostgreSQL | ✅ Yes (via VPC) | N/A |
| OpenAI API | ❌ No (internet) | See options below |
| Other external LLMs | ❌ No (internet) | See options below |

### Options for External LLM Integration

If you need to call external LLM APIs (OpenAI, Anthropic, etc.), choose one:

#### Option A: VPC Endpoint (Best if AWS service)
If using AWS Bedrock or SageMaker:
```bash
# Create VPC endpoint for AWS services
aws ec2 create-vpc-endpoint \
  --vpc-id $VPC_ID \
  --service-name com.amazonaws.${AWS_REGION}.bedrock-runtime \
  --vpc-endpoint-type Interface \
  --subnet-ids $SUBNET1_ID $SUBNET2_ID \
  --security-group-ids $DB_SG_ID
```
**Cost**: ~$7/month per AZ

#### Option B: Add NAT Gateway (Full internet access)
```bash
# Create NAT Gateway (requires Elastic IP)
aws ec2 allocate-address --domain vpc
# ... then create NAT Gateway and update route tables
```
**Cost**: ~$32/month + data processing

#### Option C: Internal API Gateway (Recommended for external LLMs)
Create a separate Lambda **outside VPC** that calls external APIs:
```
VPC Lambda (your app) → API Gateway → External Lambda (no VPC) → OpenAI API
                              ↓
                       Public internet access
```
**Cost**: Only when called (~$0)

#### Option D: Mock Mode (Current)
Keep `AI_MOCK_ENABLED=true` in Lambda environment variables.
All AI features return mock responses (for demo/testing).

### Implementation Path

1. **Phase 1** (Now): Deploy without NAT Gateway, use mock AI
2. **Phase 2** (Later): Add Option C (internal API Gateway) for real LLM calls

## Cost Estimate (Monthly)

| Component | Cost |
|-----------|------|
| Lambda | ~$0-5 (pay-per-use) |
| RDS PostgreSQL (db.t3.micro) | ~$13 |
| RDS Proxy | ~$7 |
| Storage (20GB) | ~$2.30 |
| Data transfer | ~$0-2 |
| **Total** | **~$25-30/month** |

*With NAT Gateway: +$32/month = ~$60/month*

## Credentials File

Sensitive credentials are stored in:
```
aws-deployment-credentials.env
```

This file is gitignored. Keep it secure and delete after deployment.

## Deployment Steps

See deployment script output for step-by-step commands.

## Post-Deployment

### Run Database Migrations
After RDS is available:
```bash
source aws-deployment-credentials.env
export DB_URL=jdbc:postgresql://$(aws rds describe-db-proxies --db-proxy-name flowboard-proxy --query 'DBProxies[0].Endpoint' --output text):5432/flowboard
cd backend && mvn flyway:migrate -Dflyway.url=$DB_URL -Dflyway.user=flowboard -Dflyway.password=$DB_PASSWORD
```

### Test API
```bash
curl https://<API_ID>.execute-api.${AWS_REGION}.amazonaws.com/api/v1/auth/register \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"Password123!","username":"testuser","fullName":"Test User"}'
```

## Security Notes

- Database is in private subnet, not accessible from internet
- Lambda has no outbound internet access (defense in depth)
- JWT secret is stored in Lambda environment variables (consider AWS Secrets Manager for production)
- CORS is currently set to `*` (restrict to your frontend domain in production)

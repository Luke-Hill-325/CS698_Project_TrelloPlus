# RDS vs EC2 Cost Analysis & Switching Feasibility

> Generated after shutting down the `flowboard-db` RDS instance and `flowboard-proxy` RDS Proxy on 2026-04-15.

---

## 1. Current Architecture Cost (Lambda + RDS)

Based on the existing `AWS-DEPLOYMENT-NOTES.md` and AWS us-east-1 on-demand pricing:

| Component | Monthly Cost |
|-----------|-------------|
| RDS PostgreSQL (db.t3.micro) | ~$13.00 |
| RDS Proxy | ~$7.00 |
| Storage (20 GB gp2) | ~$2.30 |
| Lambda (low traffic) | ~$0–$5.00 |
| API Gateway HTTP API (low traffic) | ~$0–$1.00 |
| Data transfer | ~$0–$2.00 |
| **Total** | **~$22–$30/month** |

**Key limitation:** Lambda runs in a VPC without a NAT Gateway, so outbound internet is blocked. AI runs in mock mode only, and WebSocket real-time updates are not available via API Gateway HTTP API.

---

## 2. EC2 Alternative: Self-Managed PostgreSQL

Run a single EC2 instance that hosts both the Spring Boot application and PostgreSQL.

### Cost Breakdown

| Component | Spec | Monthly Cost |
|-----------|------|-------------|
| EC2 (t3.nano) | 2 vCPU, 0.5 GiB | ~$3.80 |
| EC2 (t3.micro) | 2 vCPU, 1 GiB | ~$7.60 |
| EC2 (t3.small) | 2 vCPU, 2 GiB | ~$15.20 |
| gp3 Storage | 20 GB | ~$1.60 |
| Data transfer out | First 100 GB free | ~$0 |
| **Total (t3.nano)** | | **~$5.40/month** |
| **Total (t3.micro)** | | **~$9.20/month** |
| **Total (t3.small)** | | **~$16.80/month** |

### Recommendation
- **t3.micro** is the sweet spot for a Spring Boot app + PostgreSQL co-located on one instance. It gives 1 GiB RAM, which is tight but workable for a small app.
- **t3.small** is safer if you want headroom for JVM heap + PostgreSQL shared buffers.

### Switching Challenges

| Challenge | Mitigation |
|-----------|-----------|
| **Process management** | Use `systemd` service or Docker Compose to keep the Spring Boot JAR running across reboots. |
| **Database migration** | `pg_dump` from RDS → `psql` restore on EC2. One-time operation. |
| **Backups** | Automate nightly `pg_dump` to S3, or use EBS snapshots. Not as hands-off as RDS automated backups. |
| **Security patches** | You must apply OS and PostgreSQL security updates yourself (unlike RDS managed patching). |
| **SSL/TLS termination** | Either front EC2 with an ALB/Cloudflare, or run certbot on the instance for Let's Encrypt. |
| **Static IP** | Attach an Elastic IP so the frontend doesn't break when the instance restarts. |
| **Networking** | Open port 8080 (or 443) to the internet, and lock down port 5432 to localhost only. |

### Operational Upside
- **WebSockets work natively** — no API Gateway limitations.
- **Real AI integration** — the instance has unrestricted outbound internet access (no NAT Gateway needed).
- **Simpler deployment model** — build a standard fat JAR and run it, no Lambda flattening or SnapStart required.

---

## 3. EC2 + SQLite Option

If simplicity is the top priority, SQLite on a single EC2 instance is viable for a small, single-tenant app.

### Cost Breakdown

| Component | Monthly Cost |
|-----------|-------------|
| EC2 (t3.nano) | ~$3.80 |
| gp3 Storage | ~$1.60 |
| **Total** | **~$5.40/month** |

### Why It Could Work
- **Zero DB config** — SQLite is a single file. No `postgresql.service`, no connection pools, no RDS Proxy.
- **Easy backups** — Just copy the `.db` file to S3.
- **Spring Boot support** — SQLite JDBC driver + Hibernate dialect exists (e.g., `org.hibernate.dialect.SQLiteDialect` or third-party dialects for Hibernate 6).

### Why It Might Break

| Risk | Detail |
|------|--------|
| **Hibernate/Flyway compatibility** | Your existing Flyway migrations are written for PostgreSQL. Switching to SQLite requires rewriting DDL (e.g., `SERIAL` → `INTEGER PRIMARY KEY AUTOINCREMENT`, `UUID` → `TEXT`, `JSONB` → `TEXT`). |
| **Write concurrency** | SQLite allows only one writer at a time. With a single Spring Boot instance this is usually fine, but any future scaling (load balancer + 2+ instances) becomes impossible. |
| **Feature gaps** | No `ALTER COLUMN`, no `FOREIGN KEY` enforcement by default in older versions, limited stored procedure support. |
| **Data migration** | You cannot `pg_dump` directly to SQLite. You must write a custom export/import script or use a tool like `pgloader`. |

### Verdict
- **SQLite is only recommended** if you are willing to:
  1. Rewrite Flyway migrations.
  2. Audit all JPA entities for SQLite-compatible types.
  3. Accept that you can never run more than one backend instance behind a load balancer.
- For a class project or small MVP, this trade-off might be acceptable. For anything that might scale, stick with PostgreSQL.

---

## 4. Side-by-Side Summary

| Approach | Monthly Cost | Operational Burden | Scalability | WebSockets | Real AI |
|----------|-------------|-------------------|-------------|------------|---------|
| **Lambda + RDS (old)** | ~$22–$30 | Low | Medium (Lambda auto-scales, DB doesn't) | ❌ No | ❌ Mock only |
| **EC2 + PostgreSQL** | ~$9–$17 | Medium | Low (vertical only) | ✅ Yes | ✅ Yes |
| **EC2 + SQLite** | ~$5–$6 | Low | None (single instance forever) | ✅ Yes | ✅ Yes |

---

## 5. Recommended Path Forward

1. **Short term:** Spin up a **t3.micro** EC2 instance, install PostgreSQL 15, and migrate the RDS data with `pg_dump`/`pg_restore`. This cuts infrastructure costs by roughly **50–65%** (~$22–$30 → ~$9/month).

2. **Deployment:** Run the Spring Boot fat JAR directly on the instance via `systemd` or Docker Compose. Remove the Lambda/API Gateway layer entirely.

3. **Skip SQLite for now** unless you are certain the app will never need horizontal scaling and you are willing to rewrite migrations. The cost savings over self-managed PostgreSQL on the same instance size is only ~$3–$4/month — not worth the engineering risk.

4. **Clean-up reminder:** The following AWS resources from the Lambda deployment are still running and will continue to incur charges unless torn down:
   - API Gateway (`la1rkh1aui`)
   - Lambda function (`flowboard-backend`)
   - VPC, subnets, and security groups
   - S3 bucket (`flowboard-lambda-deploy-896328677103`)

   If you fully commit to EC2, run `backend/scripts/aws-deploy/05-teardown.sh` (or delete the remaining resources manually) to stop all Lambda-related charges.

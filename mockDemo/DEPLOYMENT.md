# 🚀 ChaosLab Production Deployment Guide

## Quick Start (Docker)

### 1. Build Image
```bash
docker build -t chaoslab:latest .
```

### 2. Run with PostgreSQL
```bash
# Start PostgreSQL
docker run -d \
  --name chaoslab-db \
  -e POSTGRES_DB=chaoslab \
  -e POSTGRES_USER=chaoslab \
  -e POSTGRES_PASSWORD=CHANGE_ME_IN_PROD \
  -p 5432:5432 \
  postgres:16-alpine

# Run ChaosLab
docker run -d \
  --name chaoslab \
  --link chaoslab-db:postgres \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=postgres \
  -e DB_NAME=chaoslab \
  -e DB_USER=chaoslab \
  -e DB_PASSWORD=CHANGE_ME_IN_PROD \
  -e ENVIRONMENT=production \
  -p 8080:8080 \
  chaoslab:latest
```

### 3. Verify
```bash
# Health check
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/prometheus
```

---

## Environment Variables (Required)
```bash
# Database
DB_HOST=localhost              # PostgreSQL host
DB_NAME=chaoslab              # Database name
DB_USER=chaoslab              # Database user
DB_PASSWORD=<SECRET>          # Database password (NEVER commit this)

# Application
SPRING_PROFILES_ACTIVE=prod   # Use production config
ENVIRONMENT=production        # For metrics tagging
```

---

## Cloud Deployment Options

### AWS (ECS/Fargate)

**1. Push to ECR:**
```bash
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

docker tag chaoslab:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/chaoslab:latest
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/chaoslab:latest
```

**2. Create Task Definition:**
- Image: `<your-ecr-repo>/chaoslab:latest`
- Port: 8080
- Memory: 1GB
- CPU: 512
- Environment: Set `DB_HOST`, `DB_PASSWORD`, etc.

**3. Create RDS PostgreSQL:**
- Engine: PostgreSQL 16
- Instance: db.t3.small (start small)
- Storage: 20GB
- Multi-AZ: Yes (production)

**4. Deploy to Fargate:**
- Service: chaoslab
- Tasks: 2 (for HA)
- Load Balancer: ALB on port 8080

---

### Google Cloud (Cloud Run)
```bash
# Build and push
gcloud builds submit --tag gcr.io/PROJECT_ID/chaoslab

# Deploy
gcloud run deploy chaoslab \
  --image gcr.io/PROJECT_ID/chaoslab \
  --platform managed \
  --region us-central1 \
  --set-env-vars="SPRING_PROFILES_ACTIVE=prod,DB_HOST=<cloud-sql-ip>" \
  --set-secrets="DB_PASSWORD=chaoslab-db-password:latest" \
  --allow-unauthenticated
```

---

### Heroku (Easiest - Good for MVP)
```bash
# Login
heroku login

# Create app
heroku create chaoslab-prod

# Add PostgreSQL
heroku addons:create heroku-postgresql:mini

# Set config
heroku config:set SPRING_PROFILES_ACTIVE=prod
heroku config:set ENVIRONMENT=production

# Deploy
git push heroku main

# View logs
heroku logs --tail
```

**Note:** Heroku auto-sets `DATABASE_URL`. Update `application-prod.yml`:
```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/chaoslab}
```

---

## Database Setup (First Deployment)

### 1. Create Database
```sql
CREATE DATABASE chaoslab;
CREATE USER chaoslab WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE chaoslab TO chaoslab;
```

### 2. Flyway Runs Automatically
On first startup, Spring Boot will:
- Connect to empty database
- Run all migrations (V1 through V11)
- Create tables with proper indexes
- Insert default organization

**Verify migrations:**
```bash
# Connect to DB
psql -h <host> -U chaoslab -d chaoslab

# Check Flyway history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

### 3. Create First Organization (Optional)

Default org is created by migration, but to create a new one:
```bash
curl -X POST http://localhost:8080/api/v1/organizations \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Acme Corp",
    "plan": "enterprise"
  }'

# Response includes API key - SAVE THIS, it's shown only once:
{
  "id": 2,
  "name": "Acme Corp",
  "slug": "acme-corp",
  "apiKey": "ck_prod_AbCdEf1234567890...",  # SAVE THIS
  "plan": "enterprise",
  "maxRules": 999
}
```

---

## Monitoring Setup

### Prometheus + Grafana

**1. Prometheus config (`prometheus.yml`):**
```yaml
scrape_configs:
  - job_name: 'chaoslab'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['chaoslab:8080']
    scrape_interval: 15s
```

**2. Start Prometheus:**
```bash
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

**3. Add Grafana:**
```bash
docker run -d \
  --name grafana \
  --link prometheus:prometheus \
  -p 3000:3000 \
  grafana/grafana
```

**4. Grafana Dashboard:**
- Login: admin/admin
- Add Prometheus datasource: http://prometheus:9090
- Import dashboard or create custom:
    - `chaoslab_chaos_injected_total`
    - `chaoslab_api_auth_failures_total`
    - `chaoslab_experiment_failures_current`

---

## Backup Strategy

### Database Backups (PostgreSQL)

**Automated daily backups:**
```bash
# Add to crontab (daily at 2 AM)
0 2 * * * pg_dump -h localhost -U chaoslab chaoslab | gzip > /backups/chaoslab-$(date +\%Y\%m\%d).sql.gz

# Keep last 30 days
find /backups -name "chaoslab-*.sql.gz" -mtime +30 -delete
```

**Restore from backup:**
```bash
gunzip < chaoslab-20260215.sql.gz | psql -h localhost -U chaoslab chaoslab
```

**Cloud-specific:**
- **AWS RDS:** Enable automated backups (retention: 7-30 days)
- **GCP Cloud SQL:** Enable automated backups (retention: 7-365 days)
- **Heroku:** `heroku pg:backups:capture` (manual or scheduled)

---

## Scaling Considerations

### When to Scale (Indicators)

**Scale horizontally (add nodes) when:**
- CPU >70% sustained
- Response time >500ms p95
- Request queue growing
- Rate limit hits increasing

**Current single-node capacity:**
- ~1,000 requests/second (with chaos disabled)
- ~500 requests/second (with 10% chaos injection)
- ~100,000 chaos events/day

### How to Scale

**Step 1: Add Load Balancer**
```bash
# AWS ALB, GCP Load Balancer, or nginx
```

**Step 2: Add Redis (for shared cache)**
```yaml
# application-prod.yml
spring:
  cache:
    type: redis
  data:
    redis:
      host: ${REDIS_HOST}
      port: 6379
```

**Step 3: Run Multiple Instances**
```bash
# Scale to 3 instances
docker-compose up --scale chaoslab=3

# Or in cloud
aws ecs update-service --service chaoslab --desired-count 3
```

**Step 4: Database Read Replicas**
- Use for analytics queries
- Keep writes on primary

---

## Security Checklist

Before going live:

- [ ] Change default DB password (`DB_PASSWORD`)
- [ ] Use HTTPS (Let's Encrypt or cloud provider SSL)
- [ ] Set secure `application-prod.yml`:
    - `flyway.clean-disabled: true`
    - `spring.jpa.show-sql: false`
- [ ] Enable firewall (only ports 80/443 public)
- [ ] Review actuator endpoints (lock down `/actuator` in prod)
- [ ] Rotate API keys for default organization
- [ ] Set up log aggregation (CloudWatch/Stackdriver)
- [ ] Enable database encryption at rest
- [ ] Configure CORS if frontend is separate domain

---

## Troubleshooting

### App won't start

**Check logs:**
```bash
docker logs chaoslab
```

**Common issues:**
1. **Database connection failed**
    - Verify `DB_HOST`, `DB_USER`, `DB_PASSWORD`
    - Check PostgreSQL is running: `docker ps | grep postgres`
    - Check network: `docker network ls`

2. **Flyway migration failed**
    - Check existing schema: `\dt` in psql
    - Manually repair: `flyway repair`
    - Clean start: Drop DB and recreate

3. **Out of memory**
    - Increase Docker memory limit
    - Reduce `-XX:MaxRAMPercentage=75.0` to 50%

### Slow performance

**Check metrics:**
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/hikari.connections.active
```

**Common fixes:**
- Increase HikariCP pool: `maximum-pool-size: 50`
- Add database indexes (already done in migrations)
- Enable query cache: `spring.jpa.properties.hibernate.cache.use_query_cache=true`

### High memory usage

**Check heap:**
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.max
```

**Optimize JVM:**
```dockerfile
ENTRYPOINT ["java", \
    "-Xmx512m", \
    "-Xms256m", \
    "-XX:+UseG1GC", \
    "-jar", "app.jar"]
```

---

## Rollback Plan

**If deployment fails:**

1. **Keep old version running**
```bash
   # Don't stop old container until new one is verified
   docker run --name chaoslab-new ...
```

2. **Test new version**
```bash
   curl http://localhost:8081/actuator/health
```

3. **Switch traffic** (swap ports or update load balancer)

4. **If issues arise:**
```bash
   docker stop chaoslab-new
   docker start chaoslab-old
```

**Database rollback:**
- Flyway doesn't auto-rollback
- Keep backup before major migrations
- Manually revert schema if needed (rare)

---

## Cost Estimates (Monthly)

### Small (MVP - 100 users)
- **Heroku:** $25 (Hobby Dyno + Postgres)
- **AWS:** $30 (Fargate Spot + RDS t3.micro)
- **GCP:** $25 (Cloud Run + Cloud SQL)

### Medium (1,000 users)
- **AWS:** $150 (2x Fargate + RDS t3.small + ALB)
- **GCP:** $120 (Cloud Run autoscale + Cloud SQL)

### Large (10,000+ users)
- **AWS:** $500+ (ECS + RDS Multi-AZ + Redis)
- **GCP:** $400+ (GKE + Cloud SQL HA)

---

## Support & Maintenance

**Weekly:**
- Check error logs
- Review metrics dashboard
- Check disk space

**Monthly:**
- Update dependencies: `mvn versions:display-dependency-updates`
- Review security advisories
- Test backup restore
- Review and optimize slow queries

**Quarterly:**
- Load testing
- Security audit
- Database vacuum/optimize

---

## Emergency Contacts

**Critical Issues:**
1. Check health: `curl /actuator/health`
2. Check logs: `docker logs chaoslab --tail 100`
3. Enable kill switch: `POST /api/v1/chaos/control/disable`
4. Scale down if overwhelmed: Reduce task count

**Database Issues:**
1. Check connections: `SELECT count(*) FROM pg_stat_activity;`
2. Check locks: `SELECT * FROM pg_locks;`
3. Kill slow query: `SELECT pg_terminate_backend(<pid>);`

---

**Last Updated:** Phase 6 Complete  
**Maintained By:** Engineering Team  
**Questions?** Check logs first, then review troubleshooting section.
# AutoFlow Production Deployment Guide

**Version:** 2.0.0
**Target:** Production environments
**Prerequisites:** Docker, Kubernetes (optional), Terraform (optional)

---

## Table of Contents

1. [Deployment Architecture](#deployment-architecture)
2. [Prerequisites](#prerequisites)
3. [Option 1: Docker Compose](#option-1-docker-compose-recommended-for-small-teams)
4. [Option 2: Kubernetes](#option-2-kubernetes-recommended-for-enterprise)
5. [Option 3: Cloud-Native](#option-3-cloud-native)
6. [Environment Configuration](#environment-configuration)
7. [Security Hardening](#security-hardening)
8. [Monitoring & Logging](#monitoring--logging)
9. [Backup & Recovery](#backup--recovery)
10. [Scaling Considerations](#scaling-considerations)
11. [Troubleshooting](#troubleshooting)

---

## Deployment Architecture

### Production Components

```
┌─────────────────────────────────────────────────────────────┐
│                       Load Balancer                          │
│                     (HAProxy / NGINX)                        │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
        ▼                ▼                ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│  AutoFlow    │ │  AutoFlow    │ │  AutoFlow    │
│  Instance 1  │ │  Instance 2  │ │  Instance 3  │
│              │ │              │ │              │
│  (Spring     │ │  (Spring     │ │  (Spring     │
│   Boot)      │ │   Boot)      │ │   Boot)      │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                │
       └────────────────┼────────────────┘
                        │
        ┌───────────────┼───────────────┐
        │               │               │
        ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│    Neo4j     │ │    Oracle    │ │   Ollama     │
│   Cluster    │ │   Database   │ │   (LLM)      │
│              │ │              │ │              │
│  (Primary +  │ │ (RAC/Single) │ │  (GPU Node)  │
│   Replicas)  │ │              │ │              │
└──────────────┘ └──────────────┘ └──────────────┘
```

---

## Prerequisites

### Hardware Requirements

**Minimum (Dev/Test):**
- 4 CPU cores
- 16 GB RAM
- 100 GB SSD
- No GPU required (CPU inference)

**Recommended (Production):**
- 8 CPU cores (16 vCPUs)
- 32 GB RAM
- 500 GB SSD
- Optional: NVIDIA GPU (4GB VRAM) for faster embeddings

**High-Availability (Enterprise):**
- 3+ AutoFlow instances (8 vCPUs, 16 GB each)
- Neo4j cluster (3 nodes: 1 primary, 2 replicas)
- Oracle RAC (2+ nodes)
- Ollama GPU server (16GB VRAM)

### Software Requirements

- **OS:** Ubuntu 22.04 LTS / RHEL 8+ / Windows Server 2022
- **Java:** OpenJDK 17 LTS
- **Docker:** 24.0+ (if using containers)
- **Kubernetes:** 1.28+ (if using K8s)
- **Neo4j:** 5.15.0+
- **Oracle:** 19c / 21c
- **Ollama:** 0.1.17+ (or Gemini API key)

---

## Option 1: Docker Compose (Recommended for Small Teams)

### Step 1: Create Project Structure

```bash
mkdir -p /opt/autoflow
cd /opt/autoflow

# Create directories
mkdir -p neo4j/data neo4j/logs
mkdir -p oracle/data
mkdir -p ollama/models
mkdir -p autoflow/logs
mkdir -p backups
```

### Step 2: Create `docker-compose.yml`

```yaml
version: '3.8'

services:
  # Neo4j Graph Database
  neo4j:
    image: neo4j:5.15.0-enterprise
    container_name: autoflow-neo4j
    ports:
      - "7474:7474"  # HTTP
      - "7687:7687"  # Bolt
    environment:
      - NEO4J_AUTH=neo4j/${NEO4J_PASSWORD}
      - NEO4J_PLUGINS=["apoc", "graph-data-science"]
      - NEO4J_dbms_memory_heap_initial__size=2G
      - NEO4J_dbms_memory_heap_max__size=4G
      - NEO4J_dbms_memory_pagecache_size=2G
      - NEO4J_server_metrics_enabled=true
    volumes:
      - ./neo4j/data:/data
      - ./neo4j/logs:/logs
    networks:
      - autoflow-network
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "cypher-shell", "-u", "neo4j", "-p", "${NEO4J_PASSWORD}", "RETURN 1"]
      interval: 30s
      timeout: 10s
      retries: 5

  # Oracle Database
  oracle:
    image: container-registry.oracle.com/database/enterprise:21.3.0.0
    container_name: autoflow-oracle
    ports:
      - "1521:1521"
    environment:
      - ORACLE_SID=ORCL
      - ORACLE_PDB=ORCLPDB1
      - ORACLE_PWD=${ORACLE_PASSWORD}
      - ORACLE_CHARACTERSET=AL32UTF8
    volumes:
      - ./oracle/data:/opt/oracle/oradata
    networks:
      - autoflow-network
    restart: unless-stopped
    shm_size: 2g

  # Ollama (Local LLM)
  ollama:
    image: ollama/ollama:latest
    container_name: autoflow-ollama
    ports:
      - "11434:11434"
    environment:
      - OLLAMA_MODELS=/root/.ollama/models
    volumes:
      - ./ollama/models:/root/.ollama/models
    networks:
      - autoflow-network
    restart: unless-stopped
    # Uncomment if you have NVIDIA GPU
    # deploy:
    #   resources:
    #     reservations:
    #       devices:
    #         - driver: nvidia
    #           count: 1
    #           capabilities: [gpu]

  # AutoFlow Application
  autoflow:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: autoflow-app
    ports:
      - "8080:8080"
    environment:
      # Neo4j
      - NEO4J_URI=bolt://neo4j:7687
      - NEO4J_USER=neo4j
      - NEO4J_PASSWORD=${NEO4J_PASSWORD}

      # Oracle
      - DB_HOST=oracle
      - DB_SERVICE=ORCLPDB1
      - DB_USER=autoflow
      - DB_PASS=${DB_PASSWORD}

      # LLM Provider
      - LLM_PROVIDER=ollama
      - GEMINI_KEY=${GEMINI_KEY:-}

      # Ollama
      - OLLAMA_BASE_URL=http://ollama:11434

      # JVM Settings
      - JAVA_OPTS=-Xmx4g -Xms2g -XX:+UseG1GC

      # Logging
      - LOGGING_LEVEL_ROOT=INFO
      - LOGGING_LEVEL_AUTOFLOW=DEBUG
    volumes:
      - ./autoflow/logs:/app/logs
      - /tmp/autoflow-workspace:/tmp/autoflow-workspace
    networks:
      - autoflow-network
    depends_on:
      neo4j:
        condition: service_healthy
      oracle:
        condition: service_started
      ollama:
        condition: service_started
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 5

networks:
  autoflow-network:
    driver: bridge

volumes:
  neo4j-data:
  oracle-data:
  ollama-models:
```

### Step 3: Create `Dockerfile`

```dockerfile
# Multi-stage build for smaller image
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build
COPY pom.xml .
COPY src ./src

# Build the application (skip tests for faster builds)
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /build/target/ai-rag-orchestrator-*.jar app.jar

# Create non-root user
RUN addgroup -S autoflow && adduser -S autoflow -G autoflow
RUN chown -R autoflow:autoflow /app

USER autoflow

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Step 4: Create `.env` File

```bash
# .env (DO NOT commit to git!)
NEO4J_PASSWORD=strong_neo4j_password_here
ORACLE_PASSWORD=strong_oracle_password_here
DB_PASSWORD=autoflow_db_password_here
GEMINI_KEY=your_gemini_api_key_optional
```

### Step 5: Pull Ollama Models

```bash
# Start Ollama first
docker-compose up -d ollama

# Pull required models
docker exec -it autoflow-ollama ollama pull qwen2.5-coder:7b
docker exec -it autoflow-ollama ollama pull mxbai-embed-large

# Verify
docker exec -it autoflow-ollama ollama list
```

### Step 6: Deploy

```bash
# Build and start all services
docker-compose up -d

# View logs
docker-compose logs -f autoflow

# Check health
docker-compose ps
curl http://localhost:8080/actuator/health
```

### Step 7: Initialize Database

```bash
# Wait for Oracle to fully start (2-3 minutes)
docker logs -f autoflow-oracle
# Wait for: "DATABASE IS READY TO USE!"

# Create AutoFlow user and schema
docker exec -it autoflow-oracle sqlplus sys/your_password@ORCLPDB1 as sysdba

SQL> CREATE USER autoflow IDENTIFIED BY autoflow_db_password_here;
SQL> GRANT CONNECT, RESOURCE TO autoflow;
SQL> GRANT UNLIMITED TABLESPACE TO autoflow;
SQL> EXIT;
```

---

## Option 2: Kubernetes (Recommended for Enterprise)

### Step 1: Create Namespace

```bash
kubectl create namespace autoflow
kubectl config set-context --current --namespace=autoflow
```

### Step 2: Create Secrets

```bash
kubectl create secret generic autoflow-secrets \
  --from-literal=neo4j-password='strong_neo4j_password' \
  --from-literal=oracle-password='strong_oracle_password' \
  --from-literal=db-password='autoflow_db_password' \
  --from-literal=gemini-key='your_gemini_api_key_optional'
```

### Step 3: Deploy Neo4j (StatefulSet)

**File:** `k8s/neo4j-statefulset.yaml`

```yaml
apiVersion: v1
kind: Service
metadata:
  name: neo4j
  labels:
    app: neo4j
spec:
  ports:
  - port: 7687
    name: bolt
  - port: 7474
    name: http
  clusterIP: None
  selector:
    app: neo4j
---
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: neo4j
spec:
  serviceName: neo4j
  replicas: 1
  selector:
    matchLabels:
      app: neo4j
  template:
    metadata:
      labels:
        app: neo4j
    spec:
      containers:
      - name: neo4j
        image: neo4j:5.15.0
        ports:
        - containerPort: 7687
          name: bolt
        - containerPort: 7474
          name: http
        env:
        - name: NEO4J_AUTH
          valueFrom:
            secretKeyRef:
              name: autoflow-secrets
              key: neo4j-password
        - name: NEO4J_PLUGINS
          value: '["apoc"]'
        - name: NEO4J_dbms_memory_heap_max__size
          value: "4G"
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
          limits:
            memory: "8Gi"
            cpu: "4"
        volumeMounts:
        - name: neo4j-data
          mountPath: /data
  volumeClaimTemplates:
  - metadata:
      name: neo4j-data
    spec:
      accessModes: [ "ReadWriteOnce" ]
      resources:
        requests:
          storage: 100Gi
```

```bash
kubectl apply -f k8s/neo4j-statefulset.yaml
```

### Step 4: Deploy AutoFlow (Deployment)

**File:** `k8s/autoflow-deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: autoflow
  labels:
    app: autoflow
spec:
  replicas: 3  # High availability
  selector:
    matchLabels:
      app: autoflow
  template:
    metadata:
      labels:
        app: autoflow
    spec:
      containers:
      - name: autoflow
        image: your-registry/autoflow:2.0.0
        ports:
        - containerPort: 8080
        env:
        - name: NEO4J_URI
          value: "bolt://neo4j:7687"
        - name: NEO4J_USER
          value: "neo4j"
        - name: NEO4J_PASSWORD
          valueFrom:
            secretKeyRef:
              name: autoflow-secrets
              key: neo4j-password
        - name: DB_HOST
          value: "oracle-service"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: autoflow-secrets
              key: db-password
        - name: LLM_PROVIDER
          value: "ollama"
        - name: OLLAMA_BASE_URL
          value: "http://ollama:11434"
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
          limits:
            memory: "4Gi"
            cpu: "2"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
---
apiVersion: v1
kind: Service
metadata:
  name: autoflow
spec:
  type: LoadBalancer
  selector:
    app: autoflow
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
```

```bash
kubectl apply -f k8s/autoflow-deployment.yaml
```

### Step 5: Deploy Ollama (Deployment with GPU)

**File:** `k8s/ollama-deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ollama
spec:
  replicas: 1
  selector:
    matchLabels:
      app: ollama
  template:
    metadata:
      labels:
        app: ollama
    spec:
      nodeSelector:
        gpu: "true"  # Ensure GPU nodes
      containers:
      - name: ollama
        image: ollama/ollama:latest
        ports:
        - containerPort: 11434
        resources:
          limits:
            nvidia.com/gpu: 1  # Request 1 GPU
        volumeMounts:
        - name: ollama-models
          mountPath: /root/.ollama/models
      volumes:
      - name: ollama-models
        persistentVolumeClaim:
          claimName: ollama-models-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: ollama
spec:
  selector:
    app: ollama
  ports:
  - protocol: TCP
    port: 11434
    targetPort: 11434
```

```bash
kubectl apply -f k8s/ollama-deployment.yaml
```

### Step 6: Verify Deployment

```bash
# Check pods
kubectl get pods
kubectl logs -f deployment/autoflow

# Check services
kubectl get svc

# Access application
kubectl port-forward svc/autoflow 8080:80
curl http://localhost:8080/actuator/health
```

---

## Option 3: Cloud-Native

### AWS Deployment

**Architecture:**
- ECS Fargate (AutoFlow application)
- RDS Oracle (Conversation storage)
- Amazon MemoryDB for Redis (Neo4j alternative, if needed)
- Or: Self-managed Neo4j on EC2
- Bedrock (LLM alternative to Ollama/Gemini)

**Terraform Example:**

```hcl
# terraform/main.tf
provider "aws" {
  region = "us-east-1"
}

# ECS Cluster
resource "aws_ecs_cluster" "autoflow" {
  name = "autoflow-cluster"
}

# ECS Task Definition
resource "aws_ecs_task_definition" "autoflow" {
  family                   = "autoflow"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = "2048"
  memory                   = "4096"

  container_definitions = jsonencode([{
    name  = "autoflow"
    image = "your-ecr-repo/autoflow:2.0.0"
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = [
      { name = "NEO4J_URI", value = "bolt://${aws_instance.neo4j.private_ip}:7687" },
      { name = "LLM_PROVIDER", value = "gemini" },
      # ... other env vars
    ]
    secrets = [
      { name = "NEO4J_PASSWORD", valueFrom = aws_secretsmanager_secret.neo4j_password.arn },
      { name = "GEMINI_KEY", valueFrom = aws_secretsmanager_secret.gemini_key.arn }
    ]
  }])
}

# Application Load Balancer
resource "aws_lb" "autoflow" {
  name               = "autoflow-alb"
  internal           = false
  load_balancer_type = "application"
  subnets            = var.public_subnet_ids
}

# ... ECS Service, Target Group, etc.
```

---

## Environment Configuration

### Production `application.yml` Overrides

Create `application-prod.yml`:

```yaml
server:
  port: 8080
  shutdown: graceful
  tomcat:
    max-threads: 200
    accept-count: 100

spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # Increased for production
      minimum-idle: 10
      connection-timeout: 30000

  jpa:
    show-sql: false  # Disable in production
    open-in-view: false

app:
  llm-provider: ${LLM_PROVIDER:ollama}

  ollama:
    base-url: ${OLLAMA_BASE_URL:http://ollama:11434}
    timeout-seconds: 180  # Increased for production

  gemini:
    retry:
      max-attempts: 10  # More retries in production
      max-backoff-seconds: 120

logging:
  level:
    root: WARN
    com.purchasingpower.autoflow: INFO
  file:
    name: /app/logs/autoflow.log
    max-size: 100MB
    max-history: 30
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### Environment Variables (Production)

```bash
# Required
NEO4J_URI=bolt://neo4j-cluster:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=<from-secrets>
DB_HOST=oracle-prod
DB_SERVICE=ORCLPDB1
DB_USER=autoflow
DB_PASS=<from-secrets>
LLM_PROVIDER=ollama

# Optional
GEMINI_KEY=<from-secrets>
WORKSPACE_DIR=/data/autoflow-workspace

# JVM Tuning
JAVA_OPTS=-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200
```

---

## Security Hardening

### 1. Network Security

```yaml
# Docker Compose network isolation
networks:
  frontend:
    driver: bridge
  backend:
    driver: bridge
    internal: true  # No external access

services:
  autoflow:
    networks:
      - frontend
      - backend

  neo4j:
    networks:
      - backend  # Only accessible from backend
```

### 2. SSL/TLS Configuration

**Enable HTTPS in Spring Boot:**

```yaml
# application-prod.yml
server:
  ssl:
    enabled: true
    key-store: file:/app/config/keystore.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: autoflow
  port: 8443
```

**Generate Self-Signed Certificate (for testing):**

```bash
keytool -genkeypair -alias autoflow \
  -keyalg RSA -keysize 2048 \
  -storetype PKCS12 \
  -keystore keystore.p12 \
  -validity 3650
```

### 3. Secrets Management

**Using Kubernetes Secrets:**

```bash
kubectl create secret tls autoflow-tls \
  --cert=path/to/tls.crt \
  --key=path/to/tls.key
```

**Using AWS Secrets Manager:**

```java
// Fetch secrets at runtime
@Value("${aws.secretsmanager.secret-name}")
private String secretName;
```

### 4. Authentication & Authorization

**Enable Spring Security:**

```yaml
# application-prod.yml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://your-idp.com
```

---

## Monitoring & Logging

### 1. Prometheus Metrics

**Enable Spring Boot Actuator:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus,health,metrics
  metrics:
    export:
      prometheus:
        enabled: true
```

**Prometheus Scrape Config:**

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'autoflow'
    static_configs:
      - targets: ['autoflow:8080']
    metrics_path: '/actuator/prometheus'
```

### 2. Grafana Dashboard

**Key Metrics to Track:**
- Request rate (requests/sec)
- Response time (p50, p95, p99)
- Error rate (%)
- JVM heap usage
- Neo4j query time
- Embedding generation time

**Import Dashboard:**
```
Dashboard ID: 4701 (Spring Boot 2.x)
Or create custom dashboard with AutoFlow-specific metrics
```

### 3. Log Aggregation

**Option A: ELK Stack (Elasticsearch, Logstash, Kibana)**

```yaml
# docker-compose.yml
  elasticsearch:
    image: elasticsearch:8.11.0
    environment:
      - discovery.type=single-node
    ports:
      - "9200:9200"

  logstash:
    image: logstash:8.11.0
    volumes:
      - ./logstash.conf:/usr/share/logstash/pipeline/logstash.conf
    depends_on:
      - elasticsearch

  kibana:
    image: kibana:8.11.0
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch
```

**Option B: Cloud Logging**
- AWS CloudWatch
- Azure Monitor
- Google Cloud Logging

---

## Backup & Recovery

### 1. Neo4j Backup

**Manual Backup:**

```bash
# Using neo4j-admin
docker exec autoflow-neo4j neo4j-admin database dump neo4j \
  --to-path=/backups/neo4j-$(date +%Y%m%d).dump

# Copy to host
docker cp autoflow-neo4j:/backups/neo4j-20260105.dump ./backups/
```

**Automated Backup (Cron):**

```bash
# /etc/cron.daily/backup-neo4j.sh
#!/bin/bash
BACKUP_DIR=/opt/autoflow/backups
DATE=$(date +%Y%m%d)

docker exec autoflow-neo4j neo4j-admin database dump neo4j \
  --to-path=/data/backups/neo4j-$DATE.dump

# Rotate backups (keep last 7 days)
find $BACKUP_DIR -name "neo4j-*.dump" -mtime +7 -delete
```

### 2. Oracle Backup

**RMAN Backup:**

```sql
RMAN> BACKUP DATABASE PLUS ARCHIVELOG;
```

### 3. Application Configuration Backup

```bash
# Backup all configs
tar -czf autoflow-config-$(date +%Y%m%d).tar.gz \
  application.yml \
  application-prod.yml \
  src/main/resources/prompts/
```

### 4. Restore Procedure

**Neo4j Restore:**

```bash
# Stop Neo4j
docker-compose stop neo4j

# Restore from dump
docker exec autoflow-neo4j neo4j-admin database load neo4j \
  --from-path=/backups/neo4j-20260105.dump \
  --overwrite-destination=true

# Restart
docker-compose start neo4j
```

---

## Scaling Considerations

### Horizontal Scaling

**AutoFlow Application:**
- ✅ **Stateless:** Can scale horizontally easily
- Add more replicas behind load balancer
- Kubernetes HPA (Horizontal Pod Autoscaler):

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: autoflow-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: autoflow
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

**Neo4j Clustering:**
- Primary + Read Replicas
- Use Causal Cluster for write scalability

**Ollama:**
- ⚠️ **Stateful** (models are large)
- Use shared volume or model registry
- Scale by adding GPU nodes

### Vertical Scaling

**When to scale vertically:**
- Embedding generation is slow → Add GPU
- Neo4j queries are slow → Increase RAM
- JVM heap pressure → Increase heap size

**JVM Tuning:**

```bash
# For 16GB RAM instance
JAVA_OPTS="-Xmx12g -Xms8g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

---

## Troubleshooting

### Issue: Application won't start

**Check:**
1. Neo4j is running: `curl http://localhost:7474`
2. Oracle is ready: `docker logs autoflow-oracle | grep "DATABASE IS READY"`
3. Ollama has models: `docker exec autoflow-ollama ollama list`

**Logs:**
```bash
docker-compose logs -f autoflow
# Look for: "Started AutoFlowApplication"
```

### Issue: 429 Rate Limit from Gemini

**Fix:** Switch to Ollama
```bash
docker-compose down
# Edit .env: LLM_PROVIDER=ollama
docker-compose up -d
```

### Issue: Slow Embedding Generation

**Fix:** Add GPU support

```yaml
# docker-compose.yml
  ollama:
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
```

### Issue: Neo4j Out of Memory

**Fix:** Increase heap size

```yaml
  neo4j:
    environment:
      - NEO4J_dbms_memory_heap_max__size=8G  # Increased from 4G
```

---

## Production Checklist

Before going live:

- [ ] SSL/TLS enabled
- [ ] Secrets stored in vault (not .env files)
- [ ] Neo4j authentication enabled
- [ ] Oracle in RAC mode (high availability)
- [ ] Backups configured and tested
- [ ] Monitoring dashboards created
- [ ] Log aggregation configured
- [ ] Health checks configured
- [ ] Load testing completed
- [ ] Disaster recovery plan documented
- [ ] Runbook created for on-call team
- [ ] Security scan completed (OWASP ZAP, etc.)

---

## Support

For issues:
1. Check logs: `docker-compose logs -f autoflow`
2. Run diagnostics: `diagnose_neo4j.cypher`
3. Review [TROUBLESHOOTING.md](./docs/TROUBLESHOOTING.md)
4. Contact development team

---

**Last Updated:** 2026-01-05
**Version:** 2.0.0

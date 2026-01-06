# AutoFlow Troubleshooting Guide

**Version:** 2.0.0
**Last Updated:** 2026-01-05
**Audience:** Developers, DevOps, Support Team

---

## Table of Contents

1. [Quick Diagnostics](#quick-diagnostics)
2. [Application Startup Issues](#application-startup-issues)
3. [Database Connection Issues](#database-connection-issues)
4. [LLM Provider Issues](#llm-provider-issues)
5. [Indexing Issues](#indexing-issues)
6. [Search & Query Issues](#search--query-issues)
7. [Performance Issues](#performance-issues)
8. [Memory Issues](#memory-issues)
9. [API Errors](#api-errors)
10. [Docker/Container Issues](#dockercontainer-issues)

---

## Quick Diagnostics

### Run This First

```bash
# 1. Check if all services are running
docker-compose ps

# 2. Check application health
curl http://localhost:8080/actuator/health

# 3. Check Neo4j
curl http://localhost:7474

# 4. Check Ollama
curl http://localhost:11434/api/tags

# 5. Check logs for errors
docker-compose logs --tail=100 autoflow | grep -i error
```

### Health Check Expected Output

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "Oracle",
        "validationQuery": "isValid()"
      }
    },
    "neo4j": {
      "status": "UP"
    },
    "diskSpace": {
      "status": "UP"
    }
  }
}
```

---

## Application Startup Issues

### Issue: "Application failed to start"

**Symptom:**
```
ERROR o.s.boot.SpringApplication : Application run failed
java.lang.IllegalStateException: Failed to load ApplicationContext
```

**Common Causes:**

#### 1. Missing Environment Variables

**Check:**
```bash
docker exec -it autoflow-app env | grep -E 'NEO4J|DB_|GEMINI|OLLAMA'
```

**Fix:**
```bash
# Ensure .env file has all required vars
cat .env

# Required variables:
NEO4J_URI=bolt://neo4j:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=your_password
DB_HOST=oracle
DB_USER=autoflow
DB_PASS=your_password
```

#### 2. Neo4j Not Ready

**Check:**
```bash
docker logs autoflow-neo4j | tail -20
```

**Look for:**
```
Remote interface available at http://localhost:7474/
Started.
```

**Fix:**
```bash
# Wait for Neo4j to fully start (30-60 seconds)
docker-compose restart autoflow
```

#### 3. Oracle Not Ready

**Check:**
```bash
docker logs autoflow-oracle | grep "DATABASE IS READY"
```

**Fix:**
```bash
# Oracle takes 2-3 minutes to start
# Wait for: "DATABASE IS READY TO USE!"
# Then restart AutoFlow
docker-compose restart autoflow
```

#### 4. Port Already in Use

**Symptom:**
```
Web server failed to start. Port 8080 was already in use.
```

**Check:**
```bash
# Windows
netstat -ano | findstr :8080

# Linux/Mac
lsof -i :8080
```

**Fix:**
```bash
# Kill the process or change port in docker-compose.yml
ports:
  - "8081:8080"  # Use 8081 instead
```

---

## Database Connection Issues

### Issue: "Cannot connect to Neo4j"

**Symptom:**
```
org.neo4j.driver.exceptions.ServiceUnavailableException:
  Unable to connect to bolt://localhost:7687
```

**Check:**
1. Neo4j container running:
   ```bash
   docker ps | grep neo4j
   ```

2. Neo4j accessible:
   ```bash
   curl http://localhost:7474
   ```

3. Credentials correct:
   ```bash
   # Try connecting manually
   docker exec -it autoflow-neo4j cypher-shell -u neo4j -p your_password
   ```

**Fix:**

```bash
# Restart Neo4j
docker-compose restart neo4j

# Check logs
docker logs autoflow-neo4j

# Verify connection from AutoFlow container
docker exec -it autoflow-app bash
curl http://neo4j:7474
```

### Issue: "Oracle Connection Timeout"

**Symptom:**
```
java.sql.SQLRecoverableException: IO Error:
  Connection timed out
```

**Check:**
```bash
# Check if Oracle listener is running
docker exec -it autoflow-oracle lsnrctl status
```

**Fix:**

```bash
# Increase connection timeout in application.yml
spring:
  datasource:
    hikari:
      connection-timeout: 60000  # Increased from 30000
```

### Issue: "Too many connections"

**Symptom:**
```
HikariPool - Connection is not available, request timed out after 30000ms
```

**Check:**
```bash
# Check active connections
docker exec -it autoflow-oracle sqlplus / as sysdba

SQL> SELECT COUNT(*) FROM v$session WHERE username = 'AUTOFLOW';
```

**Fix:**

```yaml
# Reduce pool size in application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Reduced from 50
      minimum-idle: 5        # Reduced from 10
```

---

## LLM Provider Issues

### Issue: "Gemini 429 Rate Limit"

**Symptom:**
```
⚠️  Gemini Quota/Limit hit. Retrying (Attempt 1/12)
⚠️  Gemini Quota/Limit hit. Retrying (Attempt 2/12)
```

**Fix Option 1: Switch to Ollama**

```bash
# Stop application
docker-compose stop autoflow

# Edit .env
LLM_PROVIDER=ollama

# Restart
docker-compose up -d autoflow
```

**Fix Option 2: Increase Backoff**

```yaml
# application.yml
app:
  gemini:
    retry:
      max-attempts: 10
      initial-backoff-seconds: 30  # Increased from 10
      max-backoff-seconds: 180     # Increased from 65
```

**Fix Option 3: Use Tiered Models**

```yaml
app:
  llm-provider: hybrid  # Use Ollama for tool selection, Gemini for final reasoning
```

### Issue: "Ollama connection refused"

**Symptom:**
```
java.net.ConnectException: Connection refused: http://localhost:11434
```

**Check:**
1. Ollama running:
   ```bash
   docker ps | grep ollama
   curl http://localhost:11434/api/tags
   ```

2. Models downloaded:
   ```bash
   docker exec -it autoflow-ollama ollama list
   ```

**Fix:**

```bash
# Restart Ollama
docker-compose restart ollama

# Pull missing models
docker exec -it autoflow-ollama ollama pull qwen2.5-coder:7b
docker exec -it autoflow-ollama ollama pull mxbai-embed-large

# Verify
docker exec -it autoflow-ollama ollama list
```

### Issue: "Ollama timeout during embedding generation"

**Symptom:**
```
TimeoutException: Read timed out after 60000ms
```

**Fix:**

```yaml
# application.yml
app:
  ollama:
    timeout-seconds: 300  # Increased from 120
```

---

## Indexing Issues

### Issue: "Indexing fails for large repositories"

**Symptom:**
```
❌ Failed to parse/embed file X: OutOfMemoryError
```

**Fix:**

```bash
# Increase JVM heap
JAVA_OPTS="-Xmx8g -Xms4g"  # Increased from 4g/2g

# Or batch process files
# Already implemented in JavaParserServiceImpl
```

### Issue: "No embeddings generated"

**Check:**
```cypher
// In Neo4j Browser (http://localhost:7474)
MATCH (t:Type)
WHERE t.embedding IS NOT NULL
RETURN count(t);

// Expected: Should return > 0
// If returns 0, embeddings are not being generated
```

**Fix:**

1. Verify Ollama is running and has model:
   ```bash
   docker exec -it autoflow-ollama ollama list | grep mxbai
   ```

2. Check EmbeddingServiceImpl is being used:
   ```bash
   docker logs autoflow-app | grep "EmbeddingServiceImpl initialized"
   ```

3. Re-index the repository:
   ```bash
   # Delete old data
   curl -X POST http://localhost:8080/api/v1/knowledge/graph/query \
     -H "Content-Type: application/json" \
     -d '{"cypher": "MATCH (n) DETACH DELETE n"}'

   # Re-index
   curl -X POST http://localhost:8080/api/v1/index/repo \
     -H "Content-Type: application/json" \
     -d '{
       "repositoryUrl": "your-repo-url",
       "branch": "main"
     }'
   ```

### Issue: "Indexing stuck/hanging"

**Symptom:**
```
INFO  Cloning repository: https://github.com/...
INFO  Found 150 Java files
INFO  Parsing class: ClassA
... (no more logs)
```

**Check:**
```bash
# Check if Ollama is processing
docker logs -f autoflow-ollama

# Check CPU/Memory usage
docker stats
```

**Fix:**

```bash
# Restart the stuck container
docker-compose restart autoflow

# Check for specific file causing issue
docker logs autoflow-app | grep "Failed to parse"
```

---

## Search & Query Issues

### Issue: "Semantic search returns no results"

**Check:**
```cypher
// Verify vector index exists
SHOW INDEXES
WHERE name = 'type_embeddings';

// Check if nodes have embeddings
MATCH (t:Type)
WHERE t.embedding IS NOT NULL
RETURN count(t);
```

**Fix:**

1. **No vector index:**
   ```cypher
   CREATE VECTOR INDEX type_embeddings IF NOT EXISTS
   FOR (t:Type)
   ON (t.embedding)
   OPTIONS {indexConfig: {
     `vector.dimensions`: 1024,
     `vector.similarity_function`: 'cosine'
   }};
   ```

2. **No embeddings on nodes:**
   Re-index the repository (see "No embeddings generated" above)

3. **Wrong embedding dimension:**
   ```cypher
   // Check actual dimension
   MATCH (t:Type)
   WHERE t.embedding IS NOT NULL
   RETURN t.name, size(t.embedding) AS dim
   LIMIT 1;

   // Should be 1024
   // If wrong, drop index and recreate with correct dimension
   ```

### Issue: "Graph queries return empty results"

**Check:**
```cypher
// Verify data exists
MATCH (n) RETURN labels(n) AS label, count(n) AS count;

// Expected:
// Type: 150
// Method: 1000
// Field: 500
```

**Fix:**

```bash
# If no data, re-index
curl -X POST http://localhost:8080/api/v1/index/repo \
  -H "Content-Type: application/json" \
  -d '{"repositoryUrl": "...", "branch": "main"}'
```

### Issue: "Search returns wrong results"

**Symptom:**
```
Query: "authentication"
Results: Unrelated classes about users, not auth
```

**Fix:**

1. **Use exact match first (already implemented):**
   ```java
   // SearchServiceImpl uses hybrid search:
   // 1. Try exact match
   // 2. Fall back to fuzzy/semantic
   ```

2. **Improve descriptions:**
   ```java
   // DescriptionGeneratorImpl already:
   // - Includes class purpose
   // - Lists annotations
   // - Describes dependencies
   // Consider adding more context
   ```

3. **Adjust similarity threshold:**
   ```yaml
   # application.yml
   app:
     agents:
       scope-discovery:
         similarity:
           min-threshold: 0.6  # Increased from 0.5 (more strict)
   ```

---

## Performance Issues

### Issue: "Slow response times (>5 seconds)"

**Check:**

```bash
# 1. Check if Neo4j is slow
# Run in Neo4j Browser:
PROFILE MATCH (t:Type {name: 'UserService'}) RETURN t;

# 2. Check if Ollama is slow
docker logs -f autoflow-ollama

# 3. Check application metrics
curl http://localhost:8080/actuator/metrics/http.server.requests
```

**Fix:**

1. **Neo4j slow:**
   ```cypher
   // Create indexes
   CREATE INDEX type_name_index IF NOT EXISTS FOR (t:Type) ON (t.name);
   CREATE INDEX method_name_index IF NOT EXISTS FOR (m:Method) ON (m.name);
   ```

2. **Ollama slow (CPU inference):**
   ```bash
   # Add GPU support
   # See DEPLOYMENT_GUIDE.md for GPU configuration
   ```

3. **LLM slow:**
   ```yaml
   # Switch to faster model
   app:
     ollama:
       chat-model: qwen2.5-coder:1.5b  # Faster but less accurate
   ```

### Issue: "High CPU usage"

**Check:**
```bash
docker stats

# Look for containers using >80% CPU
```

**Fix:**

```yaml
# Limit CPU in docker-compose.yml
services:
  autoflow:
    deploy:
      resources:
        limits:
          cpus: '2.0'  # Max 2 CPU cores
```

### Issue: "Indexing takes >1 hour"

**Expected:**
- 100 files: ~2 minutes
- 500 files: ~8 minutes
- 1000 files: ~15 minutes

**If much slower:**

```bash
# Check Ollama performance
docker exec -it autoflow-ollama bash
time curl http://localhost:11434/api/embeddings \
  -d '{"model":"mxbai-embed-large","prompt":"test"}'

# Should complete in <1 second
```

**Fix:**

1. **Use GPU for Ollama** (10-100x faster)
2. **Batch processing** (already implemented in JavaParserServiceImpl)
3. **Skip test files:**
   ```java
   // Modify JavaParserServiceImpl to skip *Test.java
   ```

---

## Memory Issues

### Issue: "OutOfMemoryError: Java heap space"

**Symptom:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Check:**
```bash
# Current heap usage
curl http://localhost:8080/actuator/metrics/jvm.memory.used

# Configured heap
docker exec -it autoflow-app bash -c 'echo $JAVA_OPTS'
```

**Fix:**

```yaml
# docker-compose.yml
  autoflow:
    environment:
      - JAVA_OPTS=-Xmx8g -Xms4g  # Increased from 4g/2g
```

### Issue: "Neo4j out of memory"

**Symptom:**
```
Unable to allocate memory
```

**Check:**
```bash
docker exec -it autoflow-neo4j neo4j-admin server memory-recommendation
```

**Fix:**

```yaml
# docker-compose.yml
  neo4j:
    environment:
      - NEO4J_dbms_memory_heap_max__size=8G    # Increased
      - NEO4J_dbms_memory_pagecache_size=4G    # Increased
```

---

## API Errors

### Issue: "500 Internal Server Error"

**Check logs:**
```bash
docker logs --tail=100 autoflow-app | grep ERROR
```

**Common causes:**

1. **NullPointerException:**
   ```
   Likely missing data in Neo4j or improper Optional handling
   ```

2. **Database connection lost:**
   ```bash
   # Restart database
   docker-compose restart neo4j oracle
   ```

3. **LLM error:**
   ```bash
   # Check Ollama/Gemini logs
   docker logs autoflow-ollama
   ```

### Issue: "404 Not Found"

**Check:**
```bash
# Verify endpoint exists
curl -v http://localhost:8080/api/v1/chat

# Should return 405 Method Not Allowed (POST expected)
# NOT 404 Not Found
```

**Fix:**
```bash
# Ensure you're using correct method
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"test"}'
```

### Issue: "400 Bad Request"

**Symptom:**
```json
{
  "timestamp": "2026-01-05T10:00:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed"
}
```

**Fix:**
```bash
# Check request body format
# Required fields for /api/v1/chat:
{
  "message": "string (required)",
  "repositoryUrl": "string (optional)",
  "conversationId": "string (optional)"
}
```

---

## Docker/Container Issues

### Issue: "Container exits immediately"

**Check:**
```bash
docker logs autoflow-app
docker inspect autoflow-app
```

**Common causes:**

1. **Missing environment variables:**
   ```bash
   docker-compose config | grep environment
   ```

2. **Entrypoint error:**
   ```bash
   # Check Dockerfile ENTRYPOINT
   docker inspect autoflow-app | grep -A5 Entrypoint
   ```

### Issue: "Cannot connect to Docker daemon"

**Fix:**
```bash
# Start Docker Desktop (Windows/Mac)
# Or start Docker service (Linux)
sudo systemctl start docker
```

### Issue: "Volume mount permission denied"

**Symptom:**
```
mkdir: cannot create directory '/data': Permission denied
```

**Fix:**
```yaml
# docker-compose.yml - use named volumes instead
volumes:
  neo4j-data:  # Named volume

services:
  neo4j:
    volumes:
      - neo4j-data:/data  # Use named volume
```

---

## Diagnostic Scripts

### Script 1: Full System Check

```bash
#!/bin/bash
# check-system.sh

echo "=== AutoFlow System Check ==="

echo "1. Docker running..."
docker info >/dev/null 2>&1 && echo "✅ Docker OK" || echo "❌ Docker not running"

echo "2. Containers running..."
docker-compose ps

echo "3. Application health..."
curl -s http://localhost:8080/actuator/health | jq .status

echo "4. Neo4j health..."
curl -s http://localhost:7474 >/dev/null && echo "✅ Neo4j OK" || echo "❌ Neo4j down"

echo "5. Ollama health..."
curl -s http://localhost:11434/api/tags >/dev/null && echo "✅ Ollama OK" || echo "❌ Ollama down"

echo "6. Neo4j data..."
docker exec -it autoflow-neo4j cypher-shell -u neo4j -p password \
  "MATCH (n) RETURN labels(n)[0] AS label, count(n) AS count"

echo "7. Disk space..."
df -h | grep -E 'Filesystem|/dev/sd'

echo "=== End of Check ==="
```

### Script 2: Neo4j Diagnostics

```bash
#!/bin/bash
# diagnose-neo4j.sh

echo "=== Neo4j Diagnostics ==="

docker exec -it autoflow-neo4j cypher-shell -u neo4j -p password << EOF

// 1. Node counts
MATCH (n) RETURN labels(n)[0] AS label, count(n) AS count;

// 2. Embeddings check
MATCH (t:Type) WHERE t.embedding IS NOT NULL RETURN count(t) AS types_with_embeddings;
MATCH (m:Method) WHERE m.embedding IS NOT NULL RETURN count(m) AS methods_with_embeddings;

// 3. Embedding dimensions
MATCH (t:Type) WHERE t.embedding IS NOT NULL
RETURN t.name, size(t.embedding) AS dimension LIMIT 1;

// 4. Indexes
SHOW INDEXES;

// 5. Sample data
MATCH (t:Type) RETURN t.name, t.packageName, substring(t.description, 0, 50) LIMIT 5;

EOF

echo "=== End of Diagnostics ==="
```

Make executable:
```bash
chmod +x check-system.sh diagnose-neo4j.sh
```

---

## Getting Help

### 1. Check Documentation

- [DOCUMENTATION_INDEX.md](./DOCUMENTATION_INDEX.md) - All documentation
- [ARCHITECTURE_COMPLETE.md](./docs/ARCHITECTURE_COMPLETE.md) - System architecture
- [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Deployment instructions

### 2. Run Diagnostics

```bash
./check-system.sh
./diagnose-neo4j.sh
```

### 3. Collect Logs

```bash
# Collect all logs
mkdir -p logs-$(date +%Y%m%d)
docker-compose logs --no-color > logs-$(date +%Y%m%d)/all.log
docker logs autoflow-app > logs-$(date +%Y%m%d)/autoflow.log
docker logs autoflow-neo4j > logs-$(date +%Y%m%d)/neo4j.log
docker logs autoflow-ollama > logs-$(date +%Y%m%d)/ollama.log

# Zip and share
tar -czf autoflow-logs-$(date +%Y%m%d).tar.gz logs-$(date +%Y%m%d)/
```

### 4. Report Issue

Include:
- AutoFlow version (2.0.0)
- Docker version
- OS version
- Output of `check-system.sh`
- Relevant logs
- Steps to reproduce

---

**Last Updated:** 2026-01-05
**Version:** 2.0.0

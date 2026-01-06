# AutoFlow API Reference

**Version:** 2.0.0
**Base URL:** `http://localhost:8080`
**Content-Type:** `application/json`

---

## Table of Contents

1. [Authentication](#authentication)
2. [Chat API](#chat-api)
3. [Indexing API](#indexing-api)
4. [Search API](#search-api)
5. [Knowledge Graph API](#knowledge-graph-api)
6. [Monitoring & Health](#monitoring--health)
7. [Error Codes](#error-codes)
8. [Rate Limits](#rate-limits)
9. [Webhooks (Future)](#webhooks-future)

---

## Authentication

**Current:** No authentication required (local deployment)

**Production (Planned):**
- OAuth 2.0 / JWT tokens
- API keys
- SAML integration

---

## Chat API

### POST /api/v1/chat

**Purpose:** Send a message to the AI agent and get a streaming response

**Request Body:**
```json
{
  "message": "string (required)",
  "repositoryUrl": "string (optional)",
  "repositoryIds": ["string"] (optional),
  "conversationId": "string (optional)"
}
```

**Parameters:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `message` | string | ✅ Yes | User's question or request |
| `repositoryUrl` | string | ❌ No | Git repository URL to query |
| `repositoryIds` | array | ❌ No | List of repository IDs already indexed |
| `conversationId` | string | ❌ No | UUID for continuing a conversation |

**Response:** Server-Sent Events (SSE) stream

**Event Types:**
1. `status` - Agent status updates
2. `chunk` - Response text chunks (streaming)
3. `citation` - Source code references
4. `done` - Stream complete

**Example Request:**
```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "Explain the ChatController class",
    "repositoryUrl": "https://github.com/myorg/myrepo"
  }'
```

**Example Response (SSE Stream):**
```
event: status
data: {"step":"analyzing_request","message":"Analyzing your question..."}

event: status
data: {"step":"tool_selection","tool":"search_code","message":"Searching for ChatController..."}

event: status
data: {"step":"tool_execution","tool":"search_code","query":"ChatController"}

event: chunk
data: {"content":"The `ChatController` class is the main REST controller that handles chat requests. "}

event: chunk
data: {"content":"It exposes a POST endpoint at `/api/v1/chat` and uses Server-Sent Events (SSE) to stream responses. "}

event: citation
data: {"file":"src/main/java/com/purchasingpower/autoflow/api/ChatController.java","line":35,"snippet":"@PostMapping(\"/chat\")"}

event: chunk
data: {"content":"The controller delegates to `AutoFlowAgent` which decides which tools to use based on the user's question."}

event: done
data: {"conversationId":"550e8400-e29b-41d4-a716-446655440000","totalTokens":512,"toolsUsed":["search_code","explain_code"],"durationMs":3245}
```

**Status Events:**
```json
{
  "step": "analyzing_request" | "tool_selection" | "tool_execution" | "generating_response",
  "tool": "search_code" | "semantic_search" | "graph_query" | "explain_code" | "generate_code" | "index_repo",
  "message": "Human-readable status message",
  "query": "string (optional, for search queries)"
}
```

**Citation Events:**
```json
{
  "file": "src/main/java/.../ChatController.java",
  "line": 35,
  "snippet": "@PostMapping(\"/chat\")",
  "relevance": 0.95
}
```

**Done Event:**
```json
{
  "conversationId": "uuid",
  "totalTokens": 512,
  "toolsUsed": ["search_code", "explain_code"],
  "durationMs": 3245,
  "cost": 0.0012  // Optional, if using paid API
}
```

### Use Cases

#### 1. Project Explanation
```json
{
  "message": "Explain this project",
  "repositoryUrl": "https://github.com/spring-projects/spring-petclinic"
}
```

**Tools Used:** `discover_project`
**Expected Response:**
```
## Project Structure
This is a Spring Boot application demonstrating the Spring Framework...

### Main Application
- PetClinicApplication (main entry point)

### Controllers (4)
- OwnerController
- PetController
- VetController
- VisitController

### Services (3)
- ClinicService
- PetService
- VetService
...
```

#### 2. Code Search
```json
{
  "message": "Where is the authentication logic?",
  "repositoryUrl": "https://github.com/myorg/myapp"
}
```

**Tools Used:** `semantic_search`, `explain_code`
**Expected Response:**
```
Authentication is handled in the following components:

1. **JwtAuthenticationFilter** (src/main/java/.../JwtAuthenticationFilter.java:45)
   - Intercepts HTTP requests
   - Validates JWT tokens
   - Sets SecurityContext

2. **AuthenticationService** (src/main/java/.../AuthenticationServiceImpl.java:78)
   - Handles login/logout
   - Generates JWT tokens
   - Validates credentials

3. **SecurityConfig** (src/main/java/.../SecurityConfig.java:23)
   - Configures Spring Security
   - Defines authentication endpoints
```

#### 3. Code Generation
```json
{
  "message": "Generate a UserService with CRUD operations",
  "repositoryUrl": "https://github.com/myorg/myapp"
}
```

**Tools Used:** `generate_code`, `search_code` (for patterns)
**Expected Response:**
```java
// UserService.java
public interface UserService {
    Optional<User> findById(Long id);
    List<User> findAll();
    User save(User user);
    void deleteById(Long id);
}

// UserServiceImpl.java
@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository);
    }

    @Override
    public Optional<User> findById(Long id) {
        Preconditions.checkNotNull(id, "id cannot be null");
        log.debug("Finding user by id: {}", id);
        return userRepository.findById(id);
    }

    // ... other methods
}
```

---

## Indexing API

### POST /api/v1/index/repo

**Purpose:** Index a Git repository for code understanding

**Request Body:**
```json
{
  "repositoryUrl": "string (required)",
  "branch": "string (optional, default: main)",
  "accessToken": "string (optional, for private repos)",
  "forceReindex": "boolean (optional, default: false)"
}
```

**Parameters:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `repositoryUrl` | string | ✅ Yes | Git repository HTTPS URL |
| `branch` | string | ❌ No | Branch name (default: main) |
| `accessToken` | string | ❌ No | Git access token for private repos |
| `forceReindex` | boolean | ❌ No | Force re-index even if up-to-date |

**Response:**
```json
{
  "repositoryId": "uuid",
  "status": "success" | "failed" | "in_progress",
  "classesIndexed": 150,
  "methodsIndexed": 1200,
  "fieldsIndexed": 450,
  "relationshipsCreated": 3500,
  "embeddingsGenerated": 1350,
  "indexingTimeMs": 45000,
  "errors": []
}
```

**Example Request:**
```bash
curl -X POST http://localhost:8080/api/v1/index/repo \
  -H "Content-Type: application/json" \
  -d '{
    "repositoryUrl": "https://github.com/spring-projects/spring-petclinic",
    "branch": "main"
  }'
```

**Example Response:**
```json
{
  "repositoryId": "550e8400-e29b-41d4-a716-446655440000",
  "status": "success",
  "classesIndexed": 45,
  "methodsIndexed": 312,
  "fieldsIndexed": 156,
  "relationshipsCreated": 890,
  "embeddingsGenerated": 357,
  "indexingTimeMs": 45230,
  "errors": []
}
```

**Error Response:**
```json
{
  "repositoryId": null,
  "status": "failed",
  "classesIndexed": 0,
  "methodsIndexed": 0,
  "fieldsIndexed": 0,
  "relationshipsCreated": 0,
  "embeddingsGenerated": 0,
  "indexingTimeMs": 1200,
  "errors": [
    "Failed to clone repository: Authentication required",
    "Repository URL is invalid"
  ]
}
```

**Indexing Flow:**
```
1. Clone repository (2-5 seconds)
   ↓
2. Detect Java files (1 second)
   ↓
3. Parse each file with JavaParser (~10ms/file)
   ↓
4. Generate enriched descriptions (~20ms/file)
   ↓
5. Generate vector embeddings (~500ms/file)
   ↓
6. Store in Neo4j graph (~50ms/file)
   ↓
7. Create relationships (DEPENDS_ON, HAS_METHOD, etc.)
   ↓
8. Create vector indexes for semantic search
```

**Performance Expectations:**
| Files | Time | Notes |
|-------|------|-------|
| 10 | ~6 seconds | Fast |
| 100 | ~1 minute | Normal |
| 500 | ~5 minutes | Large project |
| 1000 | ~10 minutes | Very large |

### GET /api/v1/index/status/{repositoryId}

**Purpose:** Check indexing status

**Response:**
```json
{
  "repositoryId": "uuid",
  "status": "in_progress" | "completed" | "failed",
  "progress": 75,
  "currentFile": "src/main/java/com/example/UserService.java",
  "filesProcessed": 75,
  "totalFiles": 100,
  "estimatedTimeRemainingMs": 15000
}
```

---

## Search API

### POST /api/v1/search

**Purpose:** Direct code search (for testing, prefer /chat for production)

**Request Body:**
```json
{
  "query": "string (required)",
  "mode": "HYBRID | SEMANTIC | EXACT (optional, default: HYBRID)",
  "repositoryIds": ["string"] (optional),
  "limit": "number (optional, default: 10)",
  "minSimilarity": "number (optional, default: 0.7)"
}
```

**Parameters:**
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `query` | string | ✅ Yes | Search query |
| `mode` | enum | ❌ No | HYBRID (exact first, fuzzy fallback), SEMANTIC (vector only), EXACT (keyword only) |
| `repositoryIds` | array | ❌ No | Filter by repository IDs |
| `limit` | number | ❌ No | Max results (1-50, default: 10) |
| `minSimilarity` | number | ❌ No | Min similarity score (0.0-1.0, default: 0.7) |

**Response:**
```json
{
  "results": [
    {
      "entityType": "CLASS" | "METHOD" | "FIELD",
      "name": "ChatController",
      "fullyQualifiedName": "com.purchasingpower.autoflow.api.ChatController",
      "packageName": "com.purchasingpower.autoflow.api",
      "filePath": "src/main/java/com/purchasingpower/autoflow/api/ChatController.java",
      "description": "REST controller for handling chat requests. Supports Server-Sent Events (SSE) for streaming responses.",
      "sourceCode": "...",
      "similarityScore": 0.95,
      "matchType": "EXACT" | "SEMANTIC" | "FUZZY"
    }
  ],
  "totalResults": 15,
  "searchMode": "HYBRID",
  "executionTimeMs": 123
}
```

**Example Request (Exact Match):**
```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "ChatController",
    "mode": "EXACT"
  }'
```

**Example Request (Semantic Search):**
```bash
curl -X POST http://localhost:8080/api/v1/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "code that handles user authentication",
    "mode": "SEMANTIC",
    "limit": 5,
    "minSimilarity": 0.8
  }'
```

**Example Response:**
```json
{
  "results": [
    {
      "entityType": "CLASS",
      "name": "AuthenticationController",
      "fullyQualifiedName": "com.example.security.AuthenticationController",
      "packageName": "com.example.security",
      "filePath": "src/main/java/com/example/security/AuthenticationController.java",
      "description": "REST controller for user authentication. Provides login and logout endpoints. Generates JWT tokens upon successful authentication.",
      "sourceCode": "@RestController\n@RequestMapping(\"/api/auth\")\npublic class AuthenticationController {\n  ...\n}",
      "similarityScore": 0.92,
      "matchType": "SEMANTIC"
    },
    {
      "entityType": "CLASS",
      "name": "JwtTokenProvider",
      "fullyQualifiedName": "com.example.security.JwtTokenProvider",
      "packageName": "com.example.security",
      "filePath": "src/main/java/com/example/security/JwtTokenProvider.java",
      "description": "Utility service for JWT token generation and validation. Handles token expiration and user claims extraction.",
      "sourceCode": "@Service\npublic class JwtTokenProvider {\n  ...\n}",
      "similarityScore": 0.87,
      "matchType": "SEMANTIC"
    }
  ],
  "totalResults": 2,
  "searchMode": "SEMANTIC",
  "executionTimeMs": 234
}
```

---

## Knowledge Graph API

### POST /api/v1/knowledge/graph/query

**Purpose:** Execute raw Cypher queries on the Neo4j knowledge graph

**Security:** ⚠️ Production should restrict this endpoint (admin-only)

**Request Body:**
```json
{
  "cypher": "string (required)",
  "parameters": {"key": "value"} (optional)
}
```

**Example Request (Find all controllers):**
```bash
curl -X POST http://localhost:8080/api/v1/knowledge/graph/query \
  -H "Content-Type: application/json" \
  -d '{
    "cypher": "MATCH (t:Type) WHERE t.name =~ \".*Controller\" RETURN t.name, t.packageName LIMIT 10"
  }'
```

**Example Request (Parameterized query):**
```bash
curl -X POST http://localhost:8080/api/v1/knowledge/graph/query \
  -H "Content-Type: application/json" \
  -d '{
    "cypher": "MATCH (t:Type {name: $name}) RETURN t",
    "parameters": {
      "name": "ChatController"
    }
  }'
```

**Response:**
```json
{
  "results": [
    {
      "t.name": "ChatController",
      "t.packageName": "com.purchasingpower.autoflow.api"
    },
    {
      "t.name": "SearchController",
      "t.packageName": "com.purchasingpower.autoflow.api"
    }
  ],
  "executionTimeMs": 45
}
```

**Common Queries:**

#### 1. List all indexed repositories
```cypher
MATCH (t:Type)
RETURN DISTINCT t.repositoryId AS repoId, count(t) AS classes
```

#### 2. Find dependencies of a class
```cypher
MATCH (t:Type {name: 'ChatController'})-[r:DEPENDS_ON]->(dep:Type)
RETURN dep.name, dep.packageName
```

#### 3. Find all methods in a class
```cypher
MATCH (t:Type {name: 'ChatController'})-[r:HAS_METHOD]->(m:Method)
RETURN m.name, m.returnType, m.parameters
```

#### 4. Semantic search for similar classes
```cypher
MATCH (target:Type {name: 'ChatController'})
CALL db.index.vector.queryNodes('type_embeddings', 5, target.embedding)
YIELD node, score
RETURN node.name, node.packageName, score
ORDER BY score DESC
```

---

## Monitoring & Health

### GET /actuator/health

**Purpose:** Check application health

**Response (Healthy):**
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
      "status": "UP",
      "details": {
        "total": 499963174912,
        "free": 183792766976,
        "threshold": 10485760
      }
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

**Response (Unhealthy):**
```json
{
  "status": "DOWN",
  "components": {
    "neo4j": {
      "status": "DOWN",
      "details": {
        "error": "org.neo4j.driver.exceptions.ServiceUnavailableException: Unable to connect"
      }
    }
  }
}
```

### GET /actuator/metrics

**Purpose:** Get application metrics

**Available Metrics:**
- `http.server.requests` - Request count, duration
- `jvm.memory.used` - JVM memory usage
- `jvm.gc.pause` - Garbage collection pauses
- `logback.events` - Log events by level
- `system.cpu.usage` - CPU usage

**Example:**
```bash
curl http://localhost:8080/actuator/metrics/http.server.requests
```

**Response:**
```json
{
  "name": "http.server.requests",
  "description": "HTTP requests",
  "baseUnit": "seconds",
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 1523
    },
    {
      "statistic": "TOTAL_TIME",
      "value": 45.234
    },
    {
      "statistic": "MAX",
      "value": 3.456
    }
  ],
  "availableTags": [
    {
      "tag": "uri",
      "values": ["/api/v1/chat", "/api/v1/search", "/api/v1/index/repo"]
    },
    {
      "tag": "status",
      "values": ["200", "400", "500"]
    }
  ]
}
```

### GET /actuator/prometheus

**Purpose:** Prometheus scrape endpoint

**Response:** Prometheus text format
```
# HELP http_server_requests_seconds
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="POST",status="200",uri="/api/v1/chat",} 1234.0
http_server_requests_seconds_sum{method="POST",status="200",uri="/api/v1/chat",} 45.234
```

---

## Error Codes

| Status Code | Meaning | Example |
|-------------|---------|---------|
| **200** | Success | Request completed successfully |
| **400** | Bad Request | Missing required field, invalid JSON |
| **404** | Not Found | Endpoint doesn't exist |
| **500** | Internal Server Error | Neo4j connection failed, LLM error |
| **503** | Service Unavailable | Neo4j down, Ollama not responding |

**Error Response Format:**
```json
{
  "timestamp": "2026-01-05T10:30:45.123Z",
  "status": 500,
  "error": "Internal Server Error",
  "message": "Failed to connect to Neo4j",
  "path": "/api/v1/chat",
  "details": {
    "cause": "org.neo4j.driver.exceptions.ServiceUnavailableException",
    "stackTrace": "..."
  }
}
```

---

## Rate Limits

**Current:** No rate limits (local deployment)

**Production (Planned):**
- 100 requests/minute per IP
- 1000 requests/hour per user
- Burst limit: 10 requests/second

**Rate Limit Headers:**
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1609459200
```

---

## Webhooks (Future)

**Planned features:**

### POST /api/v1/webhooks/register

Register a webhook for indexing completion events

**Request:**
```json
{
  "url": "https://yourapp.com/webhook",
  "events": ["indexing.completed", "indexing.failed"],
  "secret": "webhook-secret-key"
}
```

**Webhook Payload:**
```json
{
  "event": "indexing.completed",
  "timestamp": "2026-01-05T10:30:45.123Z",
  "data": {
    "repositoryId": "uuid",
    "classesIndexed": 150,
    "duration": 45000
  },
  "signature": "sha256=..."
}
```

---

## Code Examples

### JavaScript (Fetch API)

```javascript
// Chat request
async function sendChatMessage(message) {
  const response = await fetch('http://localhost:8080/api/v1/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      message: message,
      repositoryUrl: 'https://github.com/myorg/myrepo'
    })
  });

  // Handle SSE stream
  const reader = response.body.getReader();
  const decoder = new TextDecoder();

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;

    const chunk = decoder.decode(value);
    const lines = chunk.split('\n');

    for (const line of lines) {
      if (line.startsWith('data: ')) {
        const data = JSON.parse(line.substring(6));
        console.log('Event:', data);
      }
    }
  }
}

// Usage
sendChatMessage('Explain this project');
```

### Python (Requests)

```python
import requests
import json

# Index repository
def index_repository(repo_url, branch='main'):
    response = requests.post(
        'http://localhost:8080/api/v1/index/repo',
        json={
            'repositoryUrl': repo_url,
            'branch': branch
        }
    )
    return response.json()

# Search code
def search_code(query, mode='HYBRID'):
    response = requests.post(
        'http://localhost:8080/api/v1/search',
        json={
            'query': query,
            'mode': mode,
            'limit': 10
        }
    )
    return response.json()

# Usage
result = index_repository('https://github.com/spring-projects/spring-petclinic')
print(f"Indexed {result['classesIndexed']} classes")

search_results = search_code('authentication logic', 'SEMANTIC')
for result in search_results['results']:
    print(f"Found: {result['name']} ({result['similarityScore']:.2f})")
```

### Java (Spring WebClient)

```java
@Service
public class AutoFlowClient {

    private final WebClient webClient;

    public AutoFlowClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("http://localhost:8080")
                .build();
    }

    public Mono<IndexResponse> indexRepository(String repoUrl, String branch) {
        return webClient.post()
                .uri("/api/v1/index/repo")
                .bodyValue(new IndexRequest(repoUrl, branch))
                .retrieve()
                .bodyToMono(IndexResponse.class);
    }

    public Flux<ChatEvent> chat(String message, String repoUrl) {
        return webClient.post()
                .uri("/api/v1/chat")
                .bodyValue(new ChatRequest(message, repoUrl))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(ChatEvent.class);
    }
}

// Usage
@Autowired
private AutoFlowClient autoFlowClient;

public void example() {
    // Index repository
    autoFlowClient.indexRepository("https://github.com/myorg/myrepo", "main")
            .subscribe(response ->
                System.out.println("Indexed " + response.getClassesIndexed() + " classes")
            );

    // Chat
    autoFlowClient.chat("Explain this project", "https://github.com/myorg/myrepo")
            .subscribe(event ->
                System.out.println("Event: " + event)
            );
}
```

---

## Postman Collection

**Import this collection for quick testing:**

```json
{
  "info": {
    "name": "AutoFlow API",
    "version": "2.0.0"
  },
  "item": [
    {
      "name": "Chat",
      "request": {
        "method": "POST",
        "url": "http://localhost:8080/api/v1/chat",
        "body": {
          "mode": "raw",
          "raw": "{\"message\":\"Explain this project\",\"repositoryUrl\":\"https://github.com/spring-projects/spring-petclinic\"}"
        }
      }
    },
    {
      "name": "Index Repository",
      "request": {
        "method": "POST",
        "url": "http://localhost:8080/api/v1/index/repo",
        "body": {
          "mode": "raw",
          "raw": "{\"repositoryUrl\":\"https://github.com/spring-projects/spring-petclinic\",\"branch\":\"main\"}"
        }
      }
    },
    {
      "name": "Search Code",
      "request": {
        "method": "POST",
        "url": "http://localhost:8080/api/v1/search",
        "body": {
          "mode": "raw",
          "raw": "{\"query\":\"ChatController\",\"mode\":\"HYBRID\",\"limit\":10}"
        }
      }
    },
    {
      "name": "Health Check",
      "request": {
        "method": "GET",
        "url": "http://localhost:8080/actuator/health"
      }
    }
  ]
}
```

---

**Last Updated:** 2026-01-05
**Version:** 2.0.0

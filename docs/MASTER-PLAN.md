# AutoFlow v2: Organizational Engineering Intelligence Platform

## Master Plan & Implementation Roadmap

**Created:** 2025-12-31
**Status:** Planning Complete - Ready for Execution
**Target:** Enterprise-grade platform for multi-repo understanding, assistance, and transformation

---

## Part 1: Vision & Use Cases

### Primary Use Cases

#### Use Case 1: Multi-Microservice Intelligence
```
Scenario: Developer joins team with 6-7 interconnected microservices
Questions they'll ask:
- "How does the order flow work across services?"
- "Which service handles payment validation?"
- "What happens if inventory-service is down?"
- "Where is the API contract between order and payment?"
- "Why does this service call that one twice?"
- "Who owns the shipping module?"
```

#### Use Case 2: Legacy Monolith Migration
```
Scenario: 15-year-old SpringMVC + JS/HTML monolith needs splitting
Tasks:
- "Map all frontend-backend dependencies"
- "Identify which JSP pages call which controllers"
- "Find all AJAX endpoints"
- "Propose microservice boundaries"
- "Generate migration plan with phases"
- "Refactor incrementally with validation"
```

#### Use Case 3: Day-to-Day Developer Assistance
```
- "Fix this null pointer in OrderService"
- "Add retry logic like we did in PaymentClient"
- "What tests cover this method?"
- "Show recent changes to authentication"
- "Create a new endpoint following our patterns"
```

---

## Part 2: Architecture Overview

### The Four Pillars

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        AUTOFLOW v2 ARCHITECTURE                          │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐    │
│  │   PILLAR 1  │  │   PILLAR 2  │  │   PILLAR 3  │  │   PILLAR 4  │    │
│  │  KNOWLEDGE  │  │   SEARCH    │  │    AGENT    │  │   ACTION    │    │
│  │    GRAPH    │  │   ENGINE    │  │    CORE     │  │   ENGINE    │    │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘    │
│         │                │                │                │            │
│         ▼                ▼                ▼                ▼            │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                     UNIFIED DATA LAYER                           │   │
│  │  Neo4j (Graph + Vectors) │ PostgreSQL (State) │ Redis (Cache)   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Pillar 1: Knowledge Graph (The Foundation)

**What it stores:**
```
ENTITIES:
├── Repository (name, url, type, language, domain)
├── Service (name, repo, ports, dependencies)
├── Class (fqn, type, annotations, purpose)
├── Method (signature, complexity, calls)
├── Endpoint (path, method, handler, request/response)
├── File (path, type, language)
├── Person (name, email, expertise_areas)
└── BusinessConcept (name, description, related_code)

RELATIONSHIPS:
├── CALLS (method → method, cross-service)
├── DEPENDS_ON (service → service)
├── EXPOSES (service → endpoint)
├── CONSUMES (service → endpoint)
├── OWNS (person → service/module)
├── EXPERT_IN (person → domain)
├── IMPLEMENTS (class → interface)
├── RELATED_TO (code → business_concept)
└── CHANGED_WITH (file → file, temporal)
```

### Pillar 2: Search Engine (The Intelligence)

**Three search modes:**
```
1. STRUCTURAL (Graph Queries)
   - "What calls PaymentService.process()?"
   - "Show dependency tree for order-service"
   - "Find all REST endpoints in auth module"

2. SEMANTIC (Vector Search)
   - "How does authentication work?"
   - "Find code related to payment validation"
   - "Show similar implementations to retry logic"

3. TEMPORAL (History Queries)
   - "What changed in auth last month?"
   - "Who modified this file recently?"
   - "Show evolution of this class"
```

### Pillar 3: Agent Core (The Brain)

**Single flexible agent with tools:**
```python
class AutoFlowAgent:
    tools = [
        # Knowledge tools
        "search_code",          # Semantic search
        "query_graph",          # Structural queries
        "get_dependencies",     # Service/class dependencies
        "get_history",          # Git history
        "get_ownership",        # Who owns what

        # Understanding tools
        "explain_code",         # Summarize code
        "explain_architecture", # High-level overview
        "trace_flow",          # Follow execution path
        "find_patterns",       # Detect patterns

        # Action tools
        "generate_code",       # Create new code
        "modify_code",         # Edit existing
        "create_pr",           # Open pull request
        "run_tests",           # Validate changes
        "batch_update",        # Multi-repo changes
    ]
```

### Pillar 4: Action Engine (The Executor)

**Capabilities:**
```
SINGLE REPO:
- Generate code with full context
- Apply changes with validation
- Run tests, fix failures
- Create PR with description

MULTI REPO:
- Batch updates across services
- Cross-repo refactoring
- API contract updates
- Dependency upgrades

MIGRATION (Special):
- Analyze monolith structure
- Propose service boundaries
- Generate migration plan
- Execute incrementally
```

---

## Part 3: What to Keep, Remove, Add

### KEEP (Good Foundations)

| Component | Location | Why Keep |
|-----------|----------|----------|
| Neo4j Integration | `storage/Neo4jGraphStore.java` | Solid graph foundation |
| AST Parser | `parser/EntityExtractor.java` | Good Java parsing |
| Git Operations | `service/GitOperationsService.java` | Clone, branch, commit works |
| Spring Boot Setup | `configuration/*` | Enterprise-ready |
| Maven Build | `service/compilation/*` | Build validation works |
| LLM Client | `client/GeminiClient.java` | Clean API wrapper |

### REMOVE (Not Needed)

| Component | Location | Why Remove |
|-----------|----------|------------|
| 13 Workflow Agents | `workflow/agents/*` | Replace with single agent + tools |
| LangGraph4j Workflow | `workflow/AutoFlowWorkflow.java` | Too rigid, replace with agent loop |
| Workflow State Machine | `workflow/state/*` | Overcomplicated for conversations |
| Pinecone References | Various | Dead code, incomplete removal |
| Complex DTOs | `model/dto/*` | Simplify to essentials |

### ADD (New Capabilities)

| Component | Purpose | Priority |
|-----------|---------|----------|
| Vector Index | Semantic search in Neo4j | P0 |
| Multi-Repo Manager | Handle multiple repos | P0 |
| Conversation Memory | Stateful chat | P0 |
| Tool-Based Agent | Flexible agent with tools | P0 |
| Cross-Repo Graph | Service relationships | P1 |
| Temporal Index | Git history analysis | P1 |
| Migration Analyzer | Monolith analysis | P2 |
| Batch Executor | Multi-repo operations | P2 |

---

## Part 4: Implementation Phases

### Phase 0: Cleanup & Foundation (Week 1)

#### Day 1-2: Code Cleanup
```
1. Remove dead code:
   - Delete unused Pinecone references
   - Remove 13 agent files (keep CodeIndexer logic)
   - Clean up workflow state machine

2. Fix critical bugs:
   - Jackson LocalDateTime deserialization
   - Pipe | parsing in metadata filter
   - Conversation history persistence

3. Simplify structure:
   autoflow/
   ├── core/           # Core domain models
   ├── knowledge/      # Graph, vectors, indexing
   ├── search/         # Query execution
   ├── agent/          # Agent + tools
   ├── action/         # Code gen, PR creation
   └── api/            # REST endpoints
```

#### Day 3-4: Foundation Setup
```
1. Neo4j schema update:
   - Add repoId to all nodes
   - Add embedding property for vectors
   - Create vector index

2. Multi-repo support:
   - Repository entity in graph
   - RepoManager service
   - Cross-repo relationships

3. Basic conversation:
   - Conversation entity
   - Message history
   - Context tracking
```

#### Day 5: Verification
```
- All tests pass
- Can index a single repo
- Can query the graph
- Conversation persists
```

---

### Phase 1: Knowledge Layer (Week 2)

#### 1.1 Enhanced Graph Schema

```cypher
// Repository node
CREATE (r:Repository {
    id: $id,
    name: $name,
    url: $url,
    language: $language,
    domain: $domain,
    indexed_at: datetime()
})

// Service node (for microservices)
CREATE (s:Service {
    id: $id,
    name: $name,
    repo_id: $repoId,
    type: $type,  // 'api', 'worker', 'gateway'
    port: $port
})

// Cross-repo relationship
MATCH (s1:Service), (s2:Service)
WHERE s1.name = 'order-service' AND s2.name = 'payment-service'
CREATE (s1)-[:CALLS {
    via: 'REST',
    endpoint: '/api/payments',
    method: 'POST'
}]->(s2)
```

#### 1.2 Vector Search Implementation

```java
// EnrichedEmbeddingService.java
public class EnrichedEmbeddingService {

    public String createEnrichedRepresentation(ClassNode node) {
        StringBuilder repr = new StringBuilder();
        repr.append("Class: ").append(node.getName()).append("\n");
        repr.append("Purpose: ").append(node.getSummary()).append("\n");
        repr.append("Domain: ").append(inferDomain(node)).append("\n");
        repr.append("Type: ").append(inferType(node)).append("\n");
        repr.append("Key Methods: ").append(getMethodSummaries(node)).append("\n");
        return repr.toString();
    }

    public void indexWithEmbedding(ClassNode node) {
        String enriched = createEnrichedRepresentation(node);
        List<Double> embedding = geminiClient.createEmbedding(enriched);
        neo4jStore.storeWithEmbedding(node, embedding);
    }
}
```

#### 1.3 Hybrid Search

```java
// HybridSearchService.java
public class HybridSearchService {

    public List<SearchResult> search(String query, SearchConfig config) {
        // 1. Classify query type
        QueryType type = classifyQuery(query);

        // 2. Execute appropriate searches
        List<SearchResult> results = new ArrayList<>();

        if (type.needsStructural()) {
            results.addAll(structuralSearch(query));
        }
        if (type.needsSemantic()) {
            results.addAll(semanticSearch(query));
        }
        if (type.needsTemporal()) {
            results.addAll(temporalSearch(query));
        }

        // 3. Merge, dedupe, rank
        return rankResults(mergeResults(results), query);
    }
}
```

---

### Phase 2: Agent Core (Week 3)

#### 2.1 Tool-Based Agent Architecture

```java
// AutoFlowAgent.java
public class AutoFlowAgent {

    private final List<Tool> tools;
    private final ConversationMemory memory;
    private final LLMClient llm;

    public AgentResponse process(String userMessage, String conversationId) {
        // 1. Load conversation context
        Conversation conv = memory.load(conversationId);
        conv.addUserMessage(userMessage);

        // 2. Build prompt with tools
        String prompt = buildAgentPrompt(conv, tools);

        // 3. Agent loop
        while (true) {
            LLMResponse response = llm.generate(prompt);

            if (response.hasToolCall()) {
                // Execute tool and add result to context
                ToolResult result = executeTool(response.getToolCall());
                conv.addToolResult(result);
                prompt = buildFollowUpPrompt(conv);
            } else {
                // Final response
                conv.addAssistantMessage(response.getText());
                memory.save(conv);
                return AgentResponse.of(response.getText());
            }
        }
    }
}
```

#### 2.2 Core Tools

```java
// SearchTool.java
@Tool(name = "search_code", description = "Search for code using natural language")
public class SearchTool {
    public ToolResult execute(Map<String, Object> params) {
        String query = (String) params.get("query");
        String repoFilter = (String) params.get("repo");  // optional

        List<SearchResult> results = searchService.search(query, repoFilter);
        return ToolResult.success(formatResults(results));
    }
}

// GraphQueryTool.java
@Tool(name = "query_dependencies", description = "Find what depends on or is depended by a class/service")
public class GraphQueryTool {
    public ToolResult execute(Map<String, Object> params) {
        String entity = (String) params.get("entity");
        String direction = (String) params.get("direction");  // "upstream" or "downstream"

        List<Dependency> deps = graphService.getDependencies(entity, direction);
        return ToolResult.success(formatDependencies(deps));
    }
}

// ExplainTool.java
@Tool(name = "explain_code", description = "Explain what a piece of code does")
public class ExplainTool {
    public ToolResult execute(Map<String, Object> params) {
        String filePath = (String) params.get("file");
        String className = (String) params.get("class");

        String code = getCode(filePath, className);
        String context = getContext(className);  // dependencies, usages

        String explanation = llm.explain(code, context);
        return ToolResult.success(explanation);
    }
}
```

#### 2.3 Conversation Memory

```java
// ConversationMemory.java
public class ConversationMemory {

    public Conversation load(String id) {
        // Load from database
        ConversationEntity entity = repo.findById(id);
        if (entity == null) {
            return Conversation.create(id);
        }
        return Conversation.fromEntity(entity);
    }

    public void save(Conversation conv) {
        ConversationEntity entity = conv.toEntity();
        repo.save(entity);
    }

    // Summarize old messages to fit context window
    public String getCondensedHistory(Conversation conv, int maxTokens) {
        if (conv.estimateTokens() < maxTokens) {
            return conv.format();
        }

        // Keep recent messages, summarize old ones
        List<Message> recent = conv.getRecentMessages(5);
        String summary = summarizeOldMessages(conv.getOlderMessages());

        return "Previous context: " + summary + "\n\nRecent:\n" + format(recent);
    }
}
```

---

### Phase 3: Multi-Repo Intelligence (Week 4)

#### 3.1 Multi-Repo Indexing

```java
// MultiRepoIndexer.java
public class MultiRepoIndexer {

    public void indexOrganization(List<String> repoUrls) {
        // Phase 1: Index each repo
        for (String url : repoUrls) {
            indexSingleRepo(url);
        }

        // Phase 2: Discover cross-repo relationships
        discoverServiceDependencies();
        discoverSharedContracts();
        discoverAPIUsages();
    }

    private void discoverServiceDependencies() {
        // Find REST client calls to other services
        String cypher = """
            MATCH (caller:Class)-[:HAS_METHOD]->(m:Method)
            WHERE m.sourceCode CONTAINS 'RestTemplate'
               OR m.sourceCode CONTAINS 'WebClient'
               OR m.sourceCode CONTAINS 'FeignClient'
            RETURN caller, m
            """;

        // Parse URLs to identify target services
        // Create CALLS relationships across repos
    }
}
```

#### 3.2 Service Topology

```java
// ServiceTopologyService.java
public class ServiceTopologyService {

    public ServiceTopology getTopology() {
        String cypher = """
            MATCH (s1:Service)-[r:CALLS]->(s2:Service)
            RETURN s1, r, s2
            """;

        // Build topology graph
        return buildTopology(neo4j.query(cypher));
    }

    public List<ServicePath> traceRequest(String entryPoint, String operation) {
        // Trace how a request flows through services
        String cypher = """
            MATCH path = (entry:Endpoint {path: $entryPoint})
                   -[:HANDLED_BY]->(:Method)
                   -[:CALLS*1..10]->(:Method)
            RETURN path
            """;

        return buildPaths(neo4j.query(cypher, Map.of("entryPoint", entryPoint)));
    }
}
```

#### 3.3 Cross-Repo Search

```java
// CrossRepoSearchService.java
public class CrossRepoSearchService {

    public List<SearchResult> searchAcrossRepos(String query) {
        // Search all repos
        List<SearchResult> results = new ArrayList<>();

        // Vector search across all repos
        String cypher = """
            CALL db.index.vector.queryNodes('code_embeddings', 50, $embedding)
            YIELD node, score
            RETURN node, score
            ORDER BY score DESC
            """;

        List<Double> embedding = embeddingService.embed(query);
        return neo4j.query(cypher, Map.of("embedding", embedding));
    }

    public ServiceResult findServiceForCapability(String capability) {
        // "Which service handles payments?"
        // Uses semantic search + service metadata
    }
}
```

---

### Phase 4: Legacy Monolith Analyzer (Week 5)

#### 4.1 Monolith Structure Analysis

```java
// MonolithAnalyzer.java
public class MonolithAnalyzer {

    public MonolithStructure analyze(String repoPath) {
        MonolithStructure structure = new MonolithStructure();

        // 1. Identify all layers
        structure.setControllers(findControllers());    // @Controller, @RestController
        structure.setServices(findServices());          // @Service
        structure.setRepositories(findRepositories());  // @Repository
        structure.setViews(findViews());                // JSP, HTML, JS files

        // 2. Map controller-to-view relationships
        structure.setMvcMappings(analyzeMvcMappings());

        // 3. Find AJAX endpoints
        structure.setAjaxEndpoints(findAjaxEndpoints());

        // 4. Identify shared state
        structure.setSessionUsage(analyzeSessionUsage());
        structure.setSharedBeans(findSharedBeans());

        return structure;
    }

    private List<MvcMapping> analyzeMvcMappings() {
        // Find which JSPs are returned by which controllers
        // Parse ModelAndView returns
        // Track @RequestMapping to view name
    }

    private List<AjaxEndpoint> findAjaxEndpoints() {
        // Parse JS files for $.ajax, fetch, XMLHttpRequest
        // Match to @ResponseBody endpoints
    }
}
```

#### 4.2 Migration Planner

```java
// MigrationPlanner.java
public class MigrationPlanner {

    public MigrationPlan planMigration(MonolithStructure structure) {
        MigrationPlan plan = new MigrationPlan();

        // 1. Propose service boundaries
        List<ProposedService> services = proposeServiceBoundaries(structure);
        plan.setProposedServices(services);

        // 2. Identify frontend-backend split points
        FrontendBackendSplit split = analyzeSplitPoints(structure);
        plan.setSplit(split);

        // 3. Identify shared dependencies
        List<SharedDependency> shared = findSharedDependencies(structure);
        plan.setSharedDependencies(shared);

        // 4. Create phased migration plan
        List<MigrationPhase> phases = createPhases(services, split, shared);
        plan.setPhases(phases);

        // 5. Identify risks
        List<MigrationRisk> risks = identifyRisks(structure);
        plan.setRisks(risks);

        return plan;
    }

    private List<ProposedService> proposeServiceBoundaries(MonolithStructure structure) {
        // Use domain analysis to suggest service boundaries
        // Group by:
        // - Package structure
        // - Entity relationships
        // - Transaction boundaries
        // - Team ownership
    }
}
```

#### 4.3 Incremental Migration Executor

```java
// MigrationExecutor.java
public class MigrationExecutor {

    public MigrationResult executePhase(MigrationPlan plan, int phaseIndex) {
        MigrationPhase phase = plan.getPhases().get(phaseIndex);
        MigrationResult result = new MigrationResult();

        for (MigrationStep step : phase.getSteps()) {
            switch (step.getType()) {
                case EXTRACT_INTERFACE:
                    extractInterface(step);
                    break;
                case CREATE_SERVICE:
                    createNewService(step);
                    break;
                case MOVE_CODE:
                    moveCodeToNewService(step);
                    break;
                case UPDATE_REFERENCES:
                    updateReferences(step);
                    break;
                case ADD_API_CLIENT:
                    addApiClient(step);
                    break;
            }

            // Validate after each step
            ValidationResult validation = validate(step);
            if (!validation.isSuccess()) {
                result.setFailed(step, validation);
                return result;
            }
        }

        result.setSuccess(true);
        return result;
    }
}
```

---

### Phase 5: Action Engine (Week 6)

#### 5.1 Code Generation with Context

```java
// ContextAwareCodeGenerator.java
public class ContextAwareCodeGenerator {

    public GeneratedCode generate(CodeGenerationRequest request) {
        // 1. Gather rich context
        Context context = contextBuilder.build(request);
        context.addSimilarImplementations(findSimilarCode(request));
        context.addPatterns(detectPatterns(request.getTargetRepo()));
        context.addConventions(getRepoConventions(request.getTargetRepo()));

        // 2. Generate with context
        String prompt = buildPrompt(request, context);
        String generated = llm.generate(prompt);

        // 3. Validate
        ValidationResult validation = validate(generated, request);

        // 4. Return with metadata
        return GeneratedCode.builder()
            .code(generated)
            .validation(validation)
            .context(context)
            .build();
    }
}
```

#### 5.2 Batch Operations

```java
// BatchOperationExecutor.java
public class BatchOperationExecutor {

    public BatchResult executeBatch(BatchOperation operation) {
        List<String> repos = resolveTargetRepos(operation);
        BatchResult result = new BatchResult();

        // Parallel execution with rate limiting
        ExecutorService executor = Executors.newFixedThreadPool(5);
        List<Future<RepoResult>> futures = new ArrayList<>();

        for (String repo : repos) {
            futures.add(executor.submit(() -> {
                try {
                    return executeOnRepo(operation, repo);
                } catch (Exception e) {
                    return RepoResult.failed(repo, e);
                }
            }));
        }

        // Collect results
        for (Future<RepoResult> future : futures) {
            result.add(future.get());
        }

        // Create summary PR or report
        if (operation.isCreatePRs()) {
            result.setPRs(createPRs(result.getSuccessful()));
        }

        return result;
    }
}
```

---

## Part 5: API Design

### REST Endpoints

```yaml
# Conversation API
POST   /api/v1/chat                    # Send message
GET    /api/v1/chat/{id}/history       # Get history
DELETE /api/v1/chat/{id}               # End conversation

# Search API
POST   /api/v1/search                  # Hybrid search
GET    /api/v1/search/repos            # List indexed repos
POST   /api/v1/search/graph            # Raw graph query

# Knowledge API
POST   /api/v1/index/repo              # Index a repo
GET    /api/v1/repos/{id}/structure    # Get repo structure
GET    /api/v1/repos/{id}/services     # Get services (microservices)
GET    /api/v1/topology                # Cross-repo topology

# Action API
POST   /api/v1/generate                # Generate code
POST   /api/v1/migrate/analyze         # Analyze monolith
POST   /api/v1/migrate/plan            # Create migration plan
POST   /api/v1/batch                   # Batch operation

# SSE Stream
GET    /api/v1/stream/{conversationId} # Real-time updates
```

### Simplified Request/Response

```java
// ChatRequest.java
public record ChatRequest(
    String message,
    String conversationId,  // null for new conversation
    List<String> repoContext  // optional: limit to specific repos
) {}

// ChatResponse.java
public record ChatResponse(
    String conversationId,
    String response,
    List<Citation> citations,  // code references
    List<SuggestedAction> actions  // "Generate code", "Create PR"
) {}
```

---

## Part 6: Testing Strategy

### Test Repositories

Create 6-7 microservices to test against:

```
test-microservices/
├── api-gateway/           # Entry point, routing
├── user-service/          # User management, auth
├── order-service/         # Order processing
├── payment-service/       # Payment handling
├── inventory-service/     # Stock management
├── notification-service/  # Email, SMS
└── shared-contracts/      # DTOs, interfaces
```

### Test Scenarios

```yaml
Scenario 1: Understanding
  - "How does order processing work?"
  - "What services are involved in checkout?"
  - "Show me the flow when a user places an order"
  Expected: Accurate cross-service explanation

Scenario 2: Finding
  - "Where is payment validation?"
  - "Who owns the inventory module?"
  - "Find all retry implementations"
  Expected: Correct code references

Scenario 3: Fixing
  - "This endpoint returns 500, here's the stack trace"
  - "Add null check in OrderService.process()"
  Expected: Targeted fix with context

Scenario 4: Creating
  - "Add a new endpoint to get order history"
  - "Implement caching like we did in user-service"
  Expected: Code following existing patterns

Scenario 5: Cross-repo
  - "Update the order API contract in all consumers"
  - "Find all services affected if payment-service is down"
  Expected: Accurate cross-repo analysis
```

---

## Part 7: File Structure (Target)

```
autoflow/
├── src/main/java/com/autoflow/
│   ├── AutoFlowApplication.java
│   │
│   ├── core/                      # Domain models
│   │   ├── model/
│   │   │   ├── Repository.java
│   │   │   ├── Service.java
│   │   │   ├── CodeEntity.java
│   │   │   └── Conversation.java
│   │   └── exception/
│   │
│   ├── knowledge/                 # Knowledge layer
│   │   ├── graph/
│   │   │   ├── Neo4jGraphStore.java
│   │   │   ├── GraphSchema.java
│   │   │   └── CrossRepoLinker.java
│   │   ├── embedding/
│   │   │   ├── EmbeddingService.java
│   │   │   └── EnrichmentService.java
│   │   ├── indexing/
│   │   │   ├── RepoIndexer.java
│   │   │   ├── MultiRepoIndexer.java
│   │   │   └── IncrementalIndexer.java
│   │   └── temporal/
│   │       └── GitHistoryService.java
│   │
│   ├── search/                    # Search layer
│   │   ├── HybridSearchService.java
│   │   ├── SemanticSearch.java
│   │   ├── StructuralSearch.java
│   │   └── QueryClassifier.java
│   │
│   ├── agent/                     # Agent layer
│   │   ├── AutoFlowAgent.java
│   │   ├── ConversationMemory.java
│   │   ├── tools/
│   │   │   ├── SearchTool.java
│   │   │   ├── GraphQueryTool.java
│   │   │   ├── ExplainTool.java
│   │   │   ├── GenerateTool.java
│   │   │   └── DependencyTool.java
│   │   └── prompt/
│   │       └── PromptBuilder.java
│   │
│   ├── action/                    # Action layer
│   │   ├── codegen/
│   │   │   ├── CodeGenerator.java
│   │   │   └── ContextBuilder.java
│   │   ├── migration/
│   │   │   ├── MonolithAnalyzer.java
│   │   │   ├── MigrationPlanner.java
│   │   │   └── MigrationExecutor.java
│   │   └── batch/
│   │       └── BatchExecutor.java
│   │
│   ├── api/                       # REST API
│   │   ├── ChatController.java
│   │   ├── SearchController.java
│   │   ├── KnowledgeController.java
│   │   └── ActionController.java
│   │
│   └── infrastructure/            # Infrastructure
│       ├── config/
│       ├── llm/
│       │   └── GeminiClient.java
│       └── git/
│           └── GitService.java
│
└── src/main/resources/
    ├── application.yml
    └── prompts/
        ├── agent-system.txt
        ├── explain-code.txt
        └── generate-code.txt
```

---

## Part 8: Timeline

```
Week 1: Cleanup & Foundation
├── Day 1-2: Remove dead code, fix critical bugs
├── Day 3-4: Set up new structure, multi-repo support
└── Day 5: Verification & testing

Week 2: Knowledge Layer
├── Day 1-2: Enhanced graph schema, vector index
├── Day 3-4: Hybrid search implementation
└── Day 5: Testing with sample repos

Week 3: Agent Core
├── Day 1-2: Tool-based agent architecture
├── Day 3-4: Core tools implementation
└── Day 5: Conversation memory & testing

Week 4: Multi-Repo Intelligence
├── Day 1-2: Multi-repo indexing
├── Day 3-4: Service topology & cross-repo search
└── Day 5: Integration testing

Week 5: Legacy Monolith Analyzer
├── Day 1-2: Monolith structure analysis
├── Day 3-4: Migration planner
└── Day 5: Testing with real monolith

Week 6: Action Engine & Polish
├── Day 1-2: Code generation with context
├── Day 3-4: Batch operations
└── Day 5: End-to-end testing, documentation
```

---

## Part 9: Success Criteria

### MVP Definition

```
✅ Can index multiple repos and understand relationships
✅ Can answer questions about architecture across repos
✅ Remembers conversation context
✅ Search actually finds relevant code
✅ Can generate code following existing patterns
✅ Can analyze monolith and propose migration
```

### Quality Bar

```
- Response time: < 5 seconds for simple queries
- Accuracy: > 90% relevant results in top 5
- Context: Handles 10+ turn conversations
- Coverage: Works with Java, JavaScript, TypeScript
- Scale: Handles 50+ repos, 1M+ lines of code
```

---

## Ready to Execute?

This plan is comprehensive but achievable in 6 weeks.

**Start with Phase 0 (Week 1)?**
- Clean up dead code
- Fix critical bugs
- Set up new structure

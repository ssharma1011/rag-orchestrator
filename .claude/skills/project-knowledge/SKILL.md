---
name: project-knowledge
description: AutoFlow v2 project context - vision, architecture, current state, and what we're building. Use when working on this codebase, understanding goals, planning features, or when any context about the project is needed.
---

# AutoFlow v2: Organizational Engineering Intelligence Platform

**Last Updated**: 2026-01-06
**Version**: 2.0.0-SNAPSHOT
**Status**: Active Development - Phase 1 (Knowledge Layer) + Context-Aware Tools ✅

---

## What We're Building

An **enterprise-grade platform for multi-repo understanding, assistance, and transformation** that goes far beyond Cursor/Copilot by providing:

- **Full codebase context** via Neo4j dependency graph (not limited context windows)
- **Cross-repo intelligence** (understand 6-7 interconnected microservices)
- **Orchestrated multi-step workflows** (not ad-hoc completions)
- **Batch operations** across 500+ repos overnight
- **Audit trails** and policy enforcement for enterprise compliance
- **Context-aware tools** that learn from conversation history and user feedback ✨ NEW

---

## Primary Use Cases

### 1. Multi-Microservice Intelligence
Developer joins team with 6-7 interconnected microservices and asks:
- "How does the order flow work across services?"
- "Which service handles payment validation?"
- "What happens if inventory-service is down?"
- "Where is the API contract between order and payment?"

### 2. Legacy Monolith Migration
15-year-old SpringMVC + JS/HTML monolith needs splitting:
- Map all frontend-backend dependencies
- Identify which JSP pages call which controllers
- Propose microservice boundaries
- Generate migration plan with phases

### 3. Day-to-Day Developer Assistance
- "Fix this null pointer in OrderService"
- "Add retry logic like we did in PaymentClient"
- "What tests cover this method?"
- "Create a new endpoint following our patterns"
- **NEW**: "Give me a better/more detailed answer" → Tools automatically adapt! ✨

---

## The Four Pillars Architecture

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│   PILLAR 1  │  │   PILLAR 2  │  │   PILLAR 3  │  │   PILLAR 4  │
│  KNOWLEDGE  │  │   SEARCH    │  │    AGENT    │  │   ACTION    │
│    GRAPH    │  │   ENGINE    │  │    CORE     │  │   ENGINE    │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       │                │                │                │
       ▼                ▼                ▼                ▼
┌─────────────────────────────────────────────────────────────────┐
│                     UNIFIED DATA LAYER                          │
│  Neo4j (Graph + Vectors) │ PostgreSQL (State) │ Redis (Cache)   │
└─────────────────────────────────────────────────────────────────┘
```

### Pillar 1: Knowledge Graph (Neo4j)
**Entities:** Repository, Service, Class, Method, Endpoint, File, Person, BusinessConcept
**Relationships:** CALLS, DEPENDS_ON, EXPOSES, CONSUMES, OWNS, EXPERT_IN, IMPLEMENTS, CHANGED_WITH

### Pillar 2: Search Engine (Three Modes)
1. **STRUCTURAL** - Graph queries: "What calls PaymentService.process()?"
2. **SEMANTIC** - Vector similarity: "How does authentication work?"
3. **TEMPORAL** - History queries: "What changed in auth last month?"

### Pillar 3: Agent Core (Single Flexible Agent) ✅ ENHANCED
**AutoFlowAgent** with **context-aware tools**:
- `search_code` - Keyword search with **query refinement** ✨
- `semantic_search` - Vector similarity search
- `discover_project` - **3 modes**: Normal/Deep/Expanded ✨
- `graph_query` - Cypher queries
- `dependency_analysis` - Dependency graphs
- `explain_code` - Code explanations
- `generate_code` - Code generation
- `web_search` - External web search

**New Capabilities (2026-01-06):**
- ✅ Tools track execution history per conversation
- ✅ Tools detect user feedback ("better", "more detail", "improve")
- ✅ Tools adapt behavior based on context
- ✅ Automatic alternative tool chaining for comprehensive results

### Pillar 4: Action Engine
- Single repo: Generate code with context, run tests, create PRs
- Multi repo: Batch updates, cross-repo refactoring
- Migration: Analyze monolith, propose boundaries, execute incrementally

---

## Recent Major Improvements (2026-01-06)

### 🎉 Context-Aware Tools System

**Problem Solved:** Tools were running the same static queries every time, ignoring user feedback and previous results.

**Solution Implemented (4 Layers):**

#### **Layer 1: Execution Tracking**
- `ToolContext` now tracks execution history per conversation
- Detects feedback phrases: "better", "more detail", "improve", "different", "expand"
- Stores last 50 tool executions with results
- API: `context.getToolExecutionCount()`, `context.hasNegativeFeedback()`, `context.getLastToolResult()`

#### **Layer 2: Context-Aware DiscoverProjectTool**
**3 Discovery Modes:**
- **NORMAL** (1st execution): Core Spring annotations (`@Service`, `@Controller`, etc.)
- **DEEP** (user wants "better"): + Patterns (`@Component`, `@Bean`, `@Async`, `@EventListener`)
- **EXPANDED** (3+ executions): + Tests, DTOs, integrations (`@Test`, `@Data`, `@FeignClient`)

**Mode Selection:**
```java
if (executionCount > 0 && hasNegativeFeedback) → DEEP mode
else if (executionCount > 1) → EXPANDED mode
else → NORMAL mode
```

#### **Layer 3: Query Refinement in SearchTool**
Generates multiple query variations:
```
"authentication" → ["authentication", "authenticationImpl",
                    "authenticationService", "IAuthentication", ...]
```
- Merges results from all variations
- Deduplicates and sorts by score
- Logs which queries were used

#### **Layer 4: Progressive Tool Chaining**
Automatically runs alternative tools when user wants better results:
```
discover_project → also runs [search_code, dependency_analysis]
search_code → also runs [semantic_search, graph_query]
explain_code → also runs [dependency_analysis, graph_query]
```
- Merges all perspectives into comprehensive response
- Only activates when `hasNegativeFeedback && executionCount > 0`

**Example Flow:**
```
User: "Explain the codebase"
→ discover_project (NORMAL mode, 50 results)

User: "Give me a BETTER explanation"
→ discover_project (DEEP mode, 80 results)
→ ALSO runs: search_code, dependency_analysis
→ Merges all 3 outputs → comprehensive answer
```

**Files Modified:**
- `agent/ToolContext.java` - Added tracking methods
- `agent/impl/ToolContextImpl.java` - Implemented tracking
- `agent/tools/DiscoverProjectTool.java` - 3 modes
- `agent/tools/SearchTool.java` - Query refinement
- `agent/AutoFlowAgent.java` - Alternative tool chaining

### 🧹 Workspace Cleanup Fix

**Problem Solved:** Cloned repositories created temp directories (`workspace/{uuid}`) but were never cleaned up → disk space leak.

**Solution Implemented:**
- Added `cleanupWorkspace(File)` to `GitOperationsService`
- Updated `Neo4jIndexingServiceImpl` with try-finally block
- Workspaces now deleted after indexing (success or failure)

**Files Modified:**
- `service/GitOperationsService.java`
- `service/impl/GitOperationsServiceImpl.java`
- `knowledge/impl/Neo4jIndexingServiceImpl.java`

---

## Current State & Status

### ✅ What's Working

| Feature | Status | Details |
|---------|--------|---------|
| Chat API | ✅ WORKING | `POST /api/v1/chat` replaces old Workflow API |
| Async Processing | ✅ WORKING | Returns conversationId immediately, processes in background |
| SSE Streaming | ✅ WORKING | Real-time updates via `GET /api/v1/chat/{id}/stream` |
| Lazy Indexing | ✅ WORKING | Only indexes when agent uses code tools (not for "hi") |
| Conversation Persistence | ✅ WORKING | Messages saved to DB with bidirectional FK |
| **Context-Aware Tools** | ✅ **NEW** | Tools adapt based on feedback and execution history |
| **Workspace Cleanup** | ✅ **NEW** | Temp directories automatically deleted after indexing |

### ⚠️ What's Still Broken

| Issue | Status | Impact | Priority |
|-------|--------|--------|----------|
| Semantic search is keyword CONTAINS, not embeddings | 🔴 BROKEN | Search doesn't find conceptually related code | HIGH |
| Neo4j pipe `\|` treated as literal | 🔴 BROKEN | Multi-term queries return 0 results | MEDIUM |
| Embeddings exist but never called | 🔴 BROKEN | `GeminiClient.createEmbedding()` unused | HIGH |

### The Fundamental Problem
Pinecone was removed but semantic search capability was never replaced. The "semantic_search" strategy calls `fullTextSearch()` which does `CONTAINS` matching, not embedding similarity.

```
PROMPT SAYS:        "semantic_search - Embedding-based similarity"
IMPLEMENTATION:     cypherQueryService.fullTextSearch() → CONTAINS keyword
EMBEDDINGS:         GeminiClient.createEmbedding() exists but NEVER CALLED
```

---

## Implementation Roadmap

### Phase 0: Cleanup & Foundation ✅ DONE
- ✅ Remove dead Pinecone references
- ✅ Fix critical bugs (Jackson, pipe parsing)
- ✅ Set up new package structure
- ✅ **Implement workspace cleanup** (2026-01-06)

### Phase 1: Knowledge Layer 🟡 IN PROGRESS
- ⏳ Add vector embeddings to Neo4j nodes
- ⏳ Create enriched representations before embedding
- ⏳ Implement hybrid retrieval (graph + vector)
- ✅ **Context-aware tools** (2026-01-06)

### Phase 2: Agent Core ✅ MOSTLY DONE
- ✅ Tool-based agent architecture (AutoFlowAgent + 8 tools)
- ✅ Conversation memory that persists (via Chat API)
- ✅ Lazy indexing (only when tools need it)
- ✅ SSE streaming for real-time updates
- ✅ **Context-aware tool execution** (2026-01-06)
- ✅ **Progressive tool chaining** (2026-01-06)

### Phase 3: Multi-Repo Intelligence ⏳ PLANNED
- Multi-repo indexing
- Cross-repo relationships
- Service topology discovery

### Phase 4: Legacy Monolith Analyzer ⏳ PLANNED
- Structure analysis
- Migration planner
- Incremental executor

### Phase 5: Action Engine ⏳ PLANNED
- Context-aware code generation
- Batch operations across repos

---

## Key Files to Understand

### Core Entry Points
| Purpose | File | Description |
|---------|------|-------------|
| REST API | `api/ChatController.java` | Chat API (replaces WorkflowController) |
| SSE Streaming | `service/ChatStreamService.java` | Real-time updates |
| Agent Core | `agent/AutoFlowAgent.java` | Tool-based agent with **alternative chaining** ✨ |

### Tools (Context-Aware) ✨
| Tool | File | New Capability |
|------|------|----------------|
| Project Discovery | `agent/tools/DiscoverProjectTool.java` | **3 modes** (Normal/Deep/Expanded) |
| Code Search | `agent/tools/SearchTool.java` | **Query refinement** (6+ variations) |
| Semantic Search | `agent/tools/SemanticSearchTool.java` | Vector similarity |
| Graph Query | `agent/tools/GraphQueryTool.java` | Cypher queries |
| Dependencies | `agent/tools/DependencyTool.java` | Dependency graphs |
| Explain Code | `agent/tools/ExplainTool.java` | Code explanations |
| Code Generation | `agent/tools/CodeGenTool.java` | Pattern-following generation |
| Web Search | `agent/tools/WebSearchTool.java` | External search |

### Tool Infrastructure ✨
| Purpose | File | Description |
|---------|------|-------------|
| Tool Context | `agent/ToolContext.java` | Interface for **execution tracking** |
| Context Impl | `agent/impl/ToolContextImpl.java` | **Tracks history, detects feedback** |
| Tool Interface | `agent/Tool.java` | Base tool interface |
| Tool Interceptor | `agent/ToolInterceptor.java` | Before/after hooks |

### Knowledge & Search
| Purpose | File | Description |
|---------|------|-------------|
| Graph Storage | `knowledge/impl/Neo4jGraphStoreImpl.java` | Neo4j operations |
| Indexing | `knowledge/impl/Neo4jIndexingServiceImpl.java` | **With cleanup** ✨ |
| Search (broken) | `service/CypherQueryService.java` | CONTAINS, not embeddings |
| Embeddings (unused) | `client/GeminiClient.java:90-128` | Needs integration |

### Git & Workspace ✨
| Purpose | File | Description |
|---------|------|-------------|
| Git Operations | `service/GitOperationsService.java` | **Added cleanupWorkspace()** |
| Git Impl | `service/impl/GitOperationsServiceImpl.java` | **Cleanup implementation** |

### Configuration
| Purpose | File | Description |
|---------|------|-------------|
| Main Config | `resources/application.yml` | ~150+ config values |
| Prompts | `resources/prompts/*.yaml` | LLM prompt templates |

### Documentation (Recent)
| Purpose | File | What's In It |
|---------|------|-------------|
| Context-Aware Tools | `CONTEXT_AWARE_TOOLS_IMPLEMENTATION.md` | Full technical implementation |
| Quick Start | `CONTEXT_AWARE_TOOLS_QUICKSTART.md` | Developer guide |
| Architecture | `docs/ARCHITECTURE_COMPLETE.md` | Complete system design |
| Project Guide | `CLAUDE.md` | Build commands, standards |

---

## What Needs to Happen Next

### Immediate Fixes (High Priority)
1. ~~Add `JavaTimeModule` to Jackson ObjectMapper for LocalDateTime~~ ✅ Fixed
2. ~~Implement workspace cleanup~~ ✅ Fixed (2026-01-06)
3. Fix pipe `|` parsing in `CypherQueryService.java` to generate OR conditions
4. Actually call `GeminiClient.createEmbedding()` during indexing
5. Update UI to use Chat API endpoints

### True Semantic Search Implementation
1. Add `embedding` property to Class/Method nodes in Neo4j
2. Create vector index: `CREATE VECTOR INDEX class_embedding_index FOR (c:Class) ON c.embedding`
3. Create enriched text representations before embedding (not raw code)
4. Update `executeSemanticSearch()` to use `db.index.vector.queryNodes()`

### Enriched Embedding (Critical)
Instead of embedding raw code, embed semantic descriptions:
```
Class: PaymentProcessor
Purpose: Handles credit card transactions
Domain: Payment Processing
Annotations: @Service, @Transactional
Dependencies: OrderRepository, PaymentGateway
```

### Context-Aware Tools Enhancements (Future)
1. **LLM-Driven Query Refinement**: Use LLM to generate smarter queries based on context
2. **Execution Analytics**: Track which modes/alternatives work best
3. **Adaptive Thresholds**: Auto-tune when to use DEEP vs EXPANDED mode
4. **Scheduled Cleanup**: Background job to delete orphaned workspaces (>24h old)
5. **Tool Performance Metrics**: Track execution time, success rate per tool/mode

---

## Coding Standards (MANDATORY)

When generating or modifying code, ALWAYS follow these rules:

### Method Limits
- **Max 20 lines** per method
- **Max complexity 10**
- **Max 4 parameters** (use parameter objects)
- **Max nesting depth 3**

### Patterns

1. **Interface + Implementation** - ALWAYS create both files:
```java
// MyService.java (interface)
public interface MyService {
    ToolResult execute(Map<String, Object> params, ToolContext context);
}

// MyServiceImpl.java (implementation)
@Service
public class MyServiceImpl implements MyService {
    @Override
    public ToolResult execute(Map<String, Object> params, ToolContext context) {
        // Implementation
    }
}
```

2. **Strategy Pattern** - No if-else chains, use Map<Type, Strategy> routing or switch expressions

3. **Immutable DTOs** - Use `@Value @Builder` from Lombok

4. **Optional over null** - Never return null, always return `Optional<T>`

5. **Fail-fast** - Use `Preconditions.checkNotNull()` at method entry

6. **No hardcoded values** - Everything in `application.yml`

7. **Context-Aware Tools** - New tools should check execution history and adapt:
```java
int executionCount = context.getToolExecutionCount("my_tool");
boolean needsBetter = context.hasNegativeFeedback();
// Adapt behavior based on context
```

### File Organization
```
autoflow/
├── core/           # Domain models & interfaces
├── knowledge/      # Graph, vectors, indexing
├── search/         # Query execution
├── agent/          # Agent + context-aware tools ✨
│   ├── tools/      # Individual tools (context-aware)
│   └── impl/       # ToolContextImpl (tracking) ✨
├── action/         # Code gen, PR creation
├── service/        # Business logic
│   └── impl/       # Service implementations
├── client/         # External clients (LLM, Git)
└── api/            # REST endpoints
```

---

## Success Criteria

```
✅ Can index multiple repos and understand relationships
✅ Can answer questions about architecture across repos
✅ Remembers conversation context
✅ Tools adapt based on user feedback ✨ NEW
✅ No workspace disk leaks ✨ NEW
⏳ Search actually finds relevant code (semantic, not just keywords) - IN PROGRESS
⏳ Can generate code following existing patterns
⏳ Can analyze monolith and propose migration
```

---

## Quality Bar
- Response time: < 5 seconds for simple queries
- Accuracy: > 90% relevant results in top 5
- Context: Handles 10+ turn conversations
- **Adaptability**: Tools improve with user feedback ✨ NEW
- Coverage: Works with Java, JavaScript, TypeScript, **SAP Hybris**
- Scale: Handles 50+ repos, 1M+ lines of code

---

## SAP Commerce Cloud (Hybris) Intelligence

AutoFlow operates in high-entropy enterprise environments with SAP Commerce Cloud (Hybris). This requires special handling.

### The Problem Landscape - What Developers Actually Ask

| The Ask | The Underlying Complexity | Solution Strategy |
|---------|---------------------------|-------------------|
| "Fix the checkout flow" | Is it in acceleratorstorefront? An ImpEx change? A custom extension? | Hybrid Trace: Correlate intent with Hybris Extension Graph + FlexSearch logs |
| "Why is this field null?" | Did it come from DB, external ERP, or missing Hybris attribute? | Value Path Traversal: DB schema → Hybris ItemType → Java Model → API |
| "Upgrade this library" | Breaking changes in shared modules affecting 20+ repos | Topological Propagation across dependency graph |
| "Document this mess" | 10-year-old code, no comments, vague Jira from 2018 | Synthesized Intent: Merge Git history + Jira tickets + code behavior |

### Dealing with the "Hybris Bomb"

SAP Commerce changes the game. AutoFlow must adapt:

**1. XML as Code**
- The system parses `items.xml`, `beans.xml`, `*-spring.xml` - not just `.java`
- When user asks "Add a field to User", system knows to:
  1. Edit `items.xml` first
  2. Trigger `ant build` (which takes ~10 min)
  3. Then update Java DTOs

**2. ImpEx Awareness**
- Much of "code" in Hybris is actually data (ImpEx files)
- Index `.impex` files as Temporal Layer artifacts
- Can answer: "When was the price row logic changed?" via ImpEx import history

**3. The Extension Mesh**
- Understand `extensioninfo.xml` to know which custom extensions override core platform
- Track dependency chains across extensions

### Advanced Scenario: Cross-Platform Refactoring

**User:** "We are migrating from Hybris internal CMS to a Headless React frontend. Move the banner logic."

**Decomposition:**
1. Identify CMS Component in Hybris (Structural Layer)
2. Identify the OCC (Omni-Commerce Connect) API endpoint exposing this component
3. Use Cross-Repo Mesh to find where React repo calls this OCC API

**Execution Loop:**
1. Refactor Hybris side to "Headless-Only" mode
2. Generate TypeScript types in React repo to match new Hybris JSON response

**Verification:**
1. Run Hybris JUnit tests
2. Run React Cypress/Jest tests to ensure the "handshake" isn't broken

### Problem-Solution Alignment Matrix

| Developer Problem | Agent Solution Strategy |
|-------------------|-------------------------|
| "Needle in Haystack" Search | Multi-Repo Breadth: Search Capabilities, not text. (e.g., "Price Calculation" across 5 Hybris extensions + 2 Go microservices) |
| "Fear of Breaking" | Guardrail Impact Report: "This change affects 14 classes and 2 API Contracts. Service 'Inventory' will need deployment." |
| "How do I build this?" | Onboarding Mode: "I see you're trying to use a new service. In this org, we use ServiceLayer Decorator pattern. Here's an example from Repo X." |
| "Stage vs Prod" Mystery | Deployment Correlation: "The issue isn't your code; the 'Winter Release' deployment to Stage included a DB schema change that hasn't hit Prod yet." |

### Self-Healing for Hybris Build Failures

In Hybris, build failures are often XML configuration issues, not Java syntax errors.

**Strategy:** BuildValidatorAgent is trained on Hybris Build Logs
**Action:** If it sees `Duplicate extension error` or `Missing attribute in items.xml`, it goes straight to XML config, fixes the dependency, and re-triggers build.

### Meta-Expertise Feature (Layer 5 - Social)

The agent acts as a Traffic Controller:

**User:** "I'm stuck on this Hybris promotion logic."
**Agent:** "I can't find a clear answer in the docs, but I see that @ssharma has committed 80% of the code in this extension and solved 3 similar Jira tickets. You should reach out to them. Here is a summary of what I've tried so far for you to share."

### Hybris-Specific Indexing Requirements

When indexing a Hybris project, AutoFlow must:

1. **Parse items.xml** - Extract type definitions, attributes, relations
2. **Parse *-spring.xml** - Understand bean wiring and overrides
3. **Parse extensioninfo.xml** - Track extension dependencies
4. **Index ImpEx files** - Understand data setup and configuration
5. **Map Facade → Service → DAO chains** - Full call graph
6. **Detect FlexibleSearch patterns** - Query optimization opportunities

### Hybris Extension Structure

```
myextension/
├── extensioninfo.xml           # Extension metadata, dependencies
├── resources/
│   ├── myextension-items.xml   # Data model definitions (THE SOURCE OF TRUTH)
│   ├── myextension-spring.xml  # Spring bean definitions
│   └── impex/                  # Data import files
├── src/                        # Java source
│   ├── facades/                # Facade layer (business interface)
│   ├── services/               # Service layer (business logic)
│   ├── daos/                   # DAO layer (FlexibleSearchQuery)
│   ├── populators/             # Model → DTO conversion
│   └── interceptors/           # Model lifecycle hooks
└── web/                        # Storefront controllers, views
```

### Key Hybris Patterns

**Converter + Populator (Data Transformation):**
```
Model → Converter → [Populator1, Populator2, ...] → DTO
```

**FlexibleSearch (Data Access):**
```java
"SELECT {pk} FROM {Product} WHERE {code} = ?code"
```

**Interceptor (Lifecycle Hooks):**
- PrepareInterceptor (before save - normalize data)
- ValidateInterceptor (before save - validate)
- LoadInterceptor (after load)
- RemoveInterceptor (before delete)

---

## Build & Run Commands

```bash
# Build (skip tests for speed)
mvn clean install -DskipTests

# Run application
mvn spring-boot:run

# Run all tests
mvn test

# Code quality
mvn checkstyle:check
mvn pmd:check
```

## Required Services

- **Neo4j**: `bolt://localhost:7687` (user: neo4j)
- **Oracle DB**: `localhost:1521/XE` (user: autoflow)
- **Environment variables**: `GEMINI_KEY`, `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`

---

## Quick Reference: Context-Aware Tools

### ToolContext API
```java
// Track execution
context.recordToolExecution("tool_name", result, null);

// Check history
int count = context.getToolExecutionCount("tool_name");
boolean needsBetter = context.hasNegativeFeedback();
Object lastResult = context.getLastToolResult("tool_name");

// Store custom data
context.setVariable("key", value);
Object value = context.getVariable("key");
```

### Creating a Context-Aware Tool
```java
@Component
public class MyTool implements Tool {
    public ToolResult execute(Map<String, Object> params, ToolContext ctx) {
        // 1. Check execution history
        int count = ctx.getToolExecutionCount("my_tool");
        boolean needsBetter = ctx.hasNegativeFeedback();

        // 2. Adapt behavior
        if (count > 0 && needsBetter) {
            return executeEnhancedMode(params, ctx);
        }

        // 3. Execute and record
        ToolResult result = executeNormalMode(params, ctx);
        ctx.recordToolExecution("my_tool", result, null);
        return result;
    }
}
```

### Alternative Tool Mapping
Add to `AutoFlowAgent.getAlternativeTools()`:
```java
case "my_tool" -> List.of("search_code", "graph_query");
```

---

## Documentation Map

### Getting Started
- `CLAUDE.md` - Build commands, coding standards
- `QUICK_START.md` - Quick start guide
- `USAGE_GUIDE.md` - How to use the system

### Architecture & Design
- `docs/ARCHITECTURE_COMPLETE.md` - Complete architecture
- `docs/VISUAL_DIAGRAMS.md` - Visual diagrams
- `docs/CODEBASE-UNDERSTANDING-DESIGN.md` - Design philosophy

### New Features (2026-01-06)
- `CONTEXT_AWARE_TOOLS_IMPLEMENTATION.md` - Full technical details
- `CONTEXT_AWARE_TOOLS_QUICKSTART.md` - Developer quick start

### API & Configuration
- `API_REFERENCE.md` - REST API reference
- `docs/PROMPT_CATALOG.md` - LLM prompts
- `application.yml` - Configuration (~150+ values)

### Deployment & Operations
- `DEPLOYMENT_GUIDE.md` - Deployment instructions
- `TROUBLESHOOTING_GUIDE.md` - Common issues
- `TEST_PLAN.md` - Testing strategy

### Project Status
- `docs/TODO.md` - Current TODO list
- `IMPLEMENTATION_STATUS.md` - Implementation status

---

**Last Major Update**: 2026-01-06 - Context-Aware Tools & Workspace Cleanup
**Next Focus**: True semantic search with vector embeddings
**Contact**: Check git history for contributors

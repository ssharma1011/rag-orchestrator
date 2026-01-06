# AutoFlow Visual Diagrams for Tech Team Presentation

**Purpose**: Copy-paste ready diagrams for PowerPoint/Confluence
**Format**: ASCII art + Mermaid (for rendering)

---

## 1. High-Level System Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│                          USERS / DEVELOPERS                        │
│                                                                    │
│    IDEs, CLI, Web UI, Slack Bots, CI/CD Pipelines                │
└─────────────────────────┬──────────────────────────────────────────┘
                          │ REST API
                          │ (HTTP/HTTPS)
                          ▼
┌────────────────────────────────────────────────────────────────────┐
│                     AUTOFLOW RAG PLATFORM                          │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │  API LAYER (Spring Boot)                                     │ │
│  │  • ChatController       - Conversational interface           │ │
│  │  • SearchController     - Code search API                    │ │
│  │  • KnowledgeController  - Repository management             │ │
│  └──────────────────────────┬───────────────────────────────────┘ │
│                             │                                      │
│  ┌──────────────────────────▼───────────────────────────────────┐ │
│  │  AGENT ORCHESTRATION LAYER                                   │ │
│  │                                                              │ │
│  │  ┌────────────────┐   ┌──────────────┐   ┌───────────────┐ │ │
│  │  │ AutoFlowAgent  │───│ LangGraph4j  │───│ Tool Executor │ │ │
│  │  │ (Coordinator)  │   │ (Workflow)   │   │ (8 tools)     │ │ │
│  │  └────────────────┘   └──────────────┘   └───────────────┘ │ │
│  │                                                              │ │
│  │  ┌───────────────────────────────────────────────────────┐ │ │
│  │  │ Conversation Memory (Oracle DB)                       │ │ │
│  │  │ • Message history                                     │ │ │
│  │  │ • User context                                        │ │ │
│  │  │ • Session state                                       │ │ │
│  │  └───────────────────────────────────────────────────────┘ │ │
│  └──────────────────────────┬───────────────────────────────────┘ │
│                             │                                      │
│  ┌──────────────────────────▼───────────────────────────────────┐ │
│  │  KNOWLEDGE LAYER                                             │ │
│  │                                                              │ │
│  │  ┌──────────────┐  ┌────────────────┐  ┌─────────────────┐ │ │
│  │  │ Neo4j Graph  │  │ Vector Index   │  │ Embedding Model │ │ │
│  │  │              │  │ (1024D)        │  │ (Ollama)        │ │ │
│  │  │ • Type nodes │  │ • Cosine       │  │ mxbai-embed-    │ │ │
│  │  │ • Method     │  │   similarity   │  │ large           │ │ │
│  │  │   nodes      │  │ • Type index   │  └─────────────────┘ │ │
│  │  │ • Relations  │  │ • Method index │                      │ │ │
│  │  └──────────────┘  └────────────────┘                      │ │ │
│  │                                                              │ │
│  │  ┌──────────────────────────────────────────────────────┐  │ │
│  │  │ Indexing Pipeline                                    │  │ │
│  │  │ Git Clone → JavaParser → Descriptions → Embeddings  │  │ │
│  │  └──────────────────────────────────────────────────────┘  │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌──────────────────────────────────────────────────────────────┐ │
│  │  LLM LAYER (Provider Abstraction)                            │ │
│  │                                                              │ │
│  │  ┌────────────────┐            ┌─────────────────┐          │ │
│  │  │ Ollama (Local) │            │ Gemini (Cloud)  │          │ │
│  │  │                │            │                 │          │ │
│  │  │ • qwen2.5-     │            │ • gemini-2.0-   │          │ │
│  │  │   coder:7b     │            │   flash-lite    │          │ │
│  │  │ • No cost      │            │ • 60 RPM limit  │          │ │
│  │  │ • No rate      │            │ • API costs     │          │ │
│  │  │   limits       │            │                 │          │ │
│  │  └────────────────┘            └─────────────────┘          │ │
│  └──────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
                          │
                          │ External Integrations
                          ▼
┌────────────────────────────────────────────────────────────────────┐
│              EXTERNAL SYSTEMS                                      │
│  • Git (GitHub, Bitbucket, GitLab)                                │
│  • Jira (Issue tracking)                                          │
│  • Docker (Build environments)                                    │
└────────────────────────────────────────────────────────────────────┘
```

---

## 2. Request Flow (Step-by-Step)

```
USER                CHAT           AUTOFLOW        TOOLS          NEO4J         LLM
 │                CONTROLLER        AGENT                                   (Ollama)
 │                    │               │              │             │           │
 │ POST /chat        │               │              │             │           │
 │ "Explain          │               │              │             │           │
 │  ChatController"  │               │              │             │           │
 ├──────────────────>│               │              │             │           │
 │                   │               │              │             │           │
 │                   │ processAsync  │              │             │           │
 │                   ├──────────────>│              │             │           │
 │                   │               │              │             │           │
 │                   │               │ Build prompt │             │           │
 │                   │               │ with tools   │             │           │
 │                   │               ├────────────────────────────────────────>│
 │                   │               │              │             │   "Which  │
 │                   │               │              │             │    tool?" │
 │                   │               │              │             │           │
 │                   │               │<────────────────────────────────────────┤
 │                   │               │  "Use search_code tool"    │   LLM     │
 │                   │               │              │             │  Response │
 │                   │               │              │             │           │
 │                   │               │ Check if     │             │           │
 │                   │               │ indexed?     │             │           │
 │                   │               ├─────────────────────────────>          │
 │                   │               │              │ MATCH (r:Repository)    │
 │                   │               │              │ WHERE r.url=...         │
 │                   │               │<─────────────────────────────          │
 │                   │               │  "Not indexed"              │           │
 │                   │               │              │             │           │
 │                   │               │ Trigger      │             │           │
 │                   │               │ indexing     │             │           │
 │                   │               ├─────────────>│             │           │
 │                   │               │              │ Clone repo  │           │
 │                   │               │              │ Parse files │           │
 │                   │               │              │ Generate    │           │
 │                   │               │              │ embeddings ────────────>│
 │                   │               │              │             │  Embed    │
 │                   │               │              │<────────────────────────┤
 │                   │               │              │ [0.12, ...] │  Vector   │
 │                   │               │              │             │           │
 │                   │               │              │ Store in    │           │
 │                   │               │              │ Neo4j      │           │
 │                   │               │              ├────────────>│           │
 │                   │               │              │ CREATE (:Type)          │
 │                   │               │              │ SET embedding=[...]     │
 │                   │               │<─────────────┤             │           │
 │                   │               │  "Indexed"   │             │           │
 │                   │               │              │             │           │
 │                   │               │ Execute      │             │           │
 │                   │               │ search_code  │             │           │
 │                   │               ├─────────────>│             │           │
 │                   │               │              │ Build query │           │
 │                   │               │              │ (HYBRID)    │           │
 │                   │               │              ├────────────>│           │
 │                   │               │              │ MATCH (t:Type)          │
 │                   │               │              │ WHERE toLower(t.name)   │
 │                   │               │              │   = 'chatcontroller'    │
 │                   │               │              │<────────────┤           │
 │                   │               │<─────────────┤  Found!     │           │
 │                   │               │  ChatController.java        │           │
 │                   │               │  + source code              │           │
 │                   │               │              │             │           │
 │                   │               │ Build prompt │             │           │
 │                   │               │ with results │             │           │
 │                   │               ├────────────────────────────────────────>│
 │                   │               │              │             │  "Explain │
 │                   │               │              │             │   this:   │
 │                   │               │              │             │   [code]" │
 │                   │               │<────────────────────────────────────────┤
 │                   │               │  "ChatController is a REST..."  LLM     │
 │                   │<──────────────┤              │             │  Response │
 │                   │  Final        │              │             │           │
 │                   │  response     │              │             │           │
 │<──────────────────┤               │              │             │           │
 │ SSE stream        │               │              │             │           │
 │ COMPLETE event    │               │              │             │           │
```

---

## 3. Neo4j Graph Schema (Visual)

```
                    ┌─────────────────────┐
                    │  (:Repository)      │
                    │                     │
                    │  id: UUID           │
                    │  url: String        │
                    │  name: String       │
                    │  branch: String     │
                    │  lastIndexedAt      │
                    └──────────┬──────────┘
                               │
                               │ (1:N)
                               │
                ┌──────────────┴──────────────┐
                │                             │
                ▼                             ▼
    ┌───────────────────┐         ┌───────────────────┐
    │  (:Type)          │         │  (:Type)          │
    │  CLASS            │         │  INTERFACE        │
    │                   │         │                   │
    │  name: String     │         │  name: String     │
    │  fqn: String      │         │  fqn: String      │
    │  sourceCode:      │         │  sourceCode:      │
    │    String         │         │    String         │
    │  embedding:       │         │  embedding:       │
    │    List<Double>   │         │    List<Double>   │
    │    [1024D]        │         │    [1024D]        │
    └─────┬─────┬───────┘         └───────────────────┘
          │     │                           ▲
          │     │                           │
          │     │ [:DECLARES]               │ [:IMPLEMENTS]
          │     │                           │
          │     ▼                           │
          │  ┌───────────────────┐          │
          │  │  (:Method)        │          │
          │  │                   ├──────────┘
          │  │  name: String     │
          │  │  signature: Str   │
          │  │  returnType: Str  │
          │  │  sourceCode: Str  │
          │  │  embedding:       │
          │  │    List<Double>   │
          │  │    [1024D]        │
          │  └─────┬─────────────┘
          │        │
          │        │ [:CALLS]
          │        │
          │        ▼
          │  ┌───────────────────┐
          │  │  (:Method)        │
          │  │  anotherMethod()  │
          │  └───────────────────┘
          │
          │ [:DECLARES]
          │
          ▼
    ┌───────────────────┐
    │  (:Field)         │
    │                   │
    │  name: String     │
    │  type: String     │
    │  lineNumber: Int  │
    └───────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  VECTOR INDEXES (Cosine Similarity)                         │
│                                                              │
│  type_embedding_index                                       │
│  ├─ Dimension: 1024                                         │
│  ├─ Similarity: Cosine                                      │
│  └─ For: (:Type).embedding                                  │
│                                                              │
│  method_embedding_index                                     │
│  ├─ Dimension: 1024                                         │
│  ├─ Similarity: Cosine                                      │
│  └─ For: (:Method).embedding                                │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  RELATIONSHIPS                                               │
│                                                              │
│  (Type)-[:DECLARES]->(Method)    - Class has method         │
│  (Type)-[:DECLARES]->(Field)     - Class has field          │
│  (Type)-[:EXTENDS]->(Type)       - Inheritance              │
│  (Type)-[:IMPLEMENTS]->(Type)    - Interface impl           │
│  (Method)-[:CALLS]->(Method)     - Method calls             │
│  (Type)-[:ANNOTATED_BY]->(Annotation) - @RestController     │
└─────────────────────────────────────────────────────────────┘
```

---

## 4. Embedding Pipeline (Detailed)

```
INPUT: Java Source File
─────────────────────────────────────────────────────────────
package com.example.api;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        return conversationService.processMessage(request);
    }
}
─────────────────────────────────────────────────────────────

                    │
                    │ STEP 1: Parse (JavaParser)
                    ▼

PARSED: JavaClass Model
─────────────────────────────────────────────────────────────
JavaClass {
  name: "ChatController"
  fqn: "com.example.api.ChatController"
  packageName: "com.example.api"
  kind: "CLASS"
  annotations: ["RestController", "RequestMapping"]
  methods: [
    JavaMethod {
      name: "chat"
      signature: "chat(ChatRequest)"
      returnType: "ResponseEntity<ChatResponse>"
      annotations: ["PostMapping"]
      body: "return conversationService.processMessage(request);"
    }
  ]
  fields: [
    JavaField {
      name: "conversationService"
      type: "ConversationService"
      annotations: ["Autowired"]
    }
  ]
}
─────────────────────────────────────────────────────────────

                    │
                    │ STEP 2: Generate Description
                    ▼

DESCRIPTION: Rich Text
─────────────────────────────────────────────────────────────
Class: ChatController
Package: com.example.api
Kind: CLASS
Purpose: REST API controller for chat functionality
Annotations: @RestController, @RequestMapping("/api/chat")
Methods: chat
Fields: conversationService
Responsibilities:
- Handle HTTP POST requests to /api/chat
- Delegate to ConversationService for processing
- Return ChatResponse wrapped in ResponseEntity
─────────────────────────────────────────────────────────────

                    │
                    │ STEP 3: Generate Embedding (Ollama)
                    ▼

EMBEDDING: 1024D Vector
─────────────────────────────────────────────────────────────
HTTP POST http://localhost:11434/api/embeddings
{
  "model": "mxbai-embed-large",
  "prompt": "Class: ChatController\nPackage: com.example.api..."
}

Response:
{
  "embedding": [
    0.123456, 0.234567, 0.345678, 0.456789,  // dimensions 0-3
    0.567890, 0.678901, 0.789012, 0.890123,  // dimensions 4-7
    ...                                       // dimensions 8-1019
    0.901234, 0.012345, 0.123456, 0.234567   // dimensions 1020-1023
  ]
}
─────────────────────────────────────────────────────────────

                    │
                    │ STEP 4: Store in Neo4j
                    ▼

CYPHER: Create Nodes
─────────────────────────────────────────────────────────────
// Create Type node
CREATE (t:Type {
  id: '550e8400-e29b-41d4-a716-446655440000',
  repositoryId: 'abc-123',
  name: 'ChatController',
  fqn: 'com.example.api.ChatController',
  packageName: 'com.example.api',
  kind: 'CLASS',
  description: 'Class: ChatController...',
  embedding: [0.123456, 0.234567, ..., 0.234567],
  sourceCode: 'package com.example.api...',
  filePath: 'src/main/java/com/example/api/ChatController.java'
})

// Create Method node
CREATE (m:Method {
  id: '650e8400-e29b-41d4-a716-446655440000',
  repositoryId: 'abc-123',
  name: 'chat',
  signature: 'chat(ChatRequest)',
  returnType: 'ResponseEntity<ChatResponse>',
  description: 'Method: chat...',
  embedding: [0.234567, 0.345678, ..., 0.345678],
  sourceCode: 'public ResponseEntity<ChatResponse> chat...'
})

// Create relationship
MATCH (t:Type {id: '550e8400-e29b-41d4-a716-446655440000'})
MATCH (m:Method {id: '650e8400-e29b-41d4-a716-446655440000'})
CREATE (t)-[:DECLARES]->(m)
─────────────────────────────────────────────────────────────

                    │
                    │ RESULT
                    ▼

NEO4J GRAPH
─────────────────────────────────────────────────────────────
(:Type {name: "ChatController", embedding: [1024D vector]})
  │
  └─[:DECLARES]→(:Method {name: "chat", embedding: [1024D vector]})
─────────────────────────────────────────────────────────────
```

---

## 5. Search Mechanisms Comparison

```
╔══════════════════════════════════════════════════════════════════╗
║                    SEARCH MODE: HYBRID                           ║
╚══════════════════════════════════════════════════════════════════╝

User Query: "ChatController"

┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: Exact Match (Fast Path)                                │
│ ─────────────────────────────────────────────────────────────── │
│ MATCH (t:Type)                                                  │
│ WHERE toLower(t.name) = toLower("chatcontroller")               │
│ RETURN t                                                        │
│                                                                 │
│ Performance: ~50ms (index-backed)                               │
│ Neo4j Uses: B-Tree index on t.name                             │
└─────────────────────────────────────────────────────────────────┘
                    │
                    │ Found? YES
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│ RESULT                                                          │
│ ─────────────────────────────────────────────────────────────── │
│ ChatController.java                                             │
│ - FQN: com.example.api.ChatController                           │
│ - Source: [full code]                                           │
│ - Score: 1.0 (perfect match)                                    │
└─────────────────────────────────────────────────────────────────┘

╔══════════════════════════════════════════════════════════════════╗
║                   SEARCH MODE: SEMANTIC                          ║
╚══════════════════════════════════════════════════════════════════╝

User Query: "code that handles chat messages"

┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: Generate Query Embedding                               │
│ ─────────────────────────────────────────────────────────────── │
│ OllamaClient.embed("code that handles chat messages")          │
│                                                                 │
│ Result: [0.234, 0.567, 0.890, ..., 0.123]  // 1024 dimensions  │
│                                                                 │
│ Performance: ~100ms                                             │
└─────────────────────────────────────────────────────────────────┘
                    │
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│ STEP 2: Vector Similarity Search                               │
│ ─────────────────────────────────────────────────────────────── │
│ CALL db.index.vector.queryNodes(                               │
│   'type_embedding_index',                                      │
│   10,                           // top 10 results               │
│   [0.234, 0.567, ..., 0.123]   // query embedding              │
│ ) YIELD node, score                                            │
│ WHERE score > 0.65              // filter low matches           │
│ RETURN node.name, node.fqn, node.sourceCode, score             │
│ ORDER BY score DESC                                             │
│                                                                 │
│ Performance: ~200ms                                             │
│ Algorithm: Cosine similarity in vector space                   │
└─────────────────────────────────────────────────────────────────┘
                    │
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────┐
│ RESULTS (Ranked by Similarity)                                 │
│ ─────────────────────────────────────────────────────────────── │
│ 1. ChatController        (score: 0.89) ✅                       │
│    - "REST controller for chat functionality"                  │
│                                                                 │
│ 2. MessageHandler        (score: 0.76) ✅                       │
│    - "Processes incoming messages"                             │
│                                                                 │
│ 3. ConversationService   (score: 0.72) ✅                       │
│    - "Manages chat conversations"                              │
│                                                                 │
│ 4. UserService           (score: 0.42) ❌ (filtered)            │
│    - Score below 0.65 threshold                                │
└─────────────────────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────────────────┐
│ WHY SEMANTIC SEARCH WORKS                                     │
│ ─────────────────────────────────────────────────────────────│
│                                                               │
│ Query: "code that handles chat messages"                     │
│ ↓ Embedding ↓                                                │
│ Vector Space: [0.234, 0.567, ...]                            │
│                                                               │
│ ChatController embedding: [0.240, 0.561, ...]                │
│ ↓ Cosine Similarity ↓                                        │
│ cos(θ) = (A·B) / (||A|| ||B||) = 0.89                        │
│                                                               │
│ The vectors are "close" in 1024D space!                      │
│                                                               │
│ Even though "ChatController" doesn't contain "handles"       │
│ or "messages", the embeddings understand the MEANING.        │
└───────────────────────────────────────────────────────────────┘
```

---

## 6. Tool Execution Flow

```
┌──────────────────────────────────────────────────────────────┐
│ LLM Decision: "I'll use the search_code tool"               │
└───────────────────┬──────────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ Parse Tool Call                                              │
│ ───────────────────────────────────────────────────────────│
│ {                                                            │
│   "tool": "search_code",                                     │
│   "parameters": {                                            │
│     "query": "ChatController",                               │
│     "mode": "hybrid"                                         │
│   }                                                          │
│ }                                                            │
└───────────────────┬──────────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ IndexingInterceptor.beforeToolExecution()                   │
│ ───────────────────────────────────────────────────────────│
│ IF tool.requiresIndexedRepo() == true:                       │
│   1. Check Neo4j for repository                             │
│   2. If not indexed:                                         │
│      a. Clone repository                                     │
│      b. Parse Java files                                     │
│      c. Generate embeddings                                  │
│      d. Store in Neo4j                                       │
│   3. Update ToolContext.repositoryIds                        │
└───────────────────┬──────────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ SearchCodeTool.execute(params, context)                     │
│ ───────────────────────────────────────────────────────────│
│ 1. Extract query from params                                │
│ 2. Call SearchService.search()                              │
│ 3. SearchService decides mode (HYBRID)                      │
│ 4. Execute Neo4j query                                      │
│ 5. Return SearchResult[]                                    │
└───────────────────┬──────────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ Format Tool Result for LLM                                   │
│ ───────────────────────────────────────────────────────────│
│ "Tool: search_code                                           │
│  Query: ChatController                                       │
│  Results: 1 found                                            │
│                                                              │
│  1. ChatController.java                                      │
│     Location: src/main/java/com/example/api/                │
│     Source Code:                                             │
│     ```java                                                  │
│     package com.example.api;                                 │
│     @RestController                                          │
│     public class ChatController {                            │
│       @PostMapping                                           │
│       public ResponseEntity<ChatResponse> chat(...) {...}    │
│     }                                                        │
│     ```"                                                     │
└───────────────────┬──────────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ AutoFlowAgent sends to LLM (next iteration)                 │
│ ───────────────────────────────────────────────────────────│
│ Prompt: "You searched and found ChatController.java.        │
│          Based on this code, explain how it works."          │
└───────────────────┬──────────────────────────────────────────┘
                    │
                    ▼
┌──────────────────────────────────────────────────────────────┐
│ LLM Final Response                                           │
│ ───────────────────────────────────────────────────────────│
│ "ChatController is the main REST API controller for chat    │
│  functionality. It's annotated with @RestController and      │
│  @RequestMapping("/api/chat"), which means it handles HTTP   │
│  requests to the /api/chat endpoint.                         │
│                                                              │
│  The chat() method is the main handler, annotated with      │
│  @PostMapping, so it processes POST requests. It receives   │
│  a ChatRequest object and delegates processing to the       │
│  ConversationService, returning a ChatResponse wrapped in   │
│  a ResponseEntity."                                          │
└──────────────────────────────────────────────────────────────┘
```

---

## 7. Conversation State Management

```
┌──────────────────────────────────────────────────────────────┐
│                    ORACLE DATABASE                           │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ TABLE: conversations                                   │ │
│  │ ────────────────────────────────────────────────────── │ │
│  │ conversation_id (PK)  VARCHAR(36)                      │ │
│  │ user_id               VARCHAR(255)                     │ │
│  │ repository_url        VARCHAR(500)                     │ │
│  │ repository_name       VARCHAR(255)                     │ │
│  │ mode                  VARCHAR(20)  (EXPLORE/DEBUG/...) │ │
│  │ is_active             BOOLEAN                          │ │
│  │ created_at            TIMESTAMP                        │ │
│  │ last_activity         TIMESTAMP                        │ │
│  └────────────────────────────────────────────────────────┘ │
│                             │                                │
│                             │ 1:N                            │
│                             ▼                                │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ TABLE: conversation_messages                           │ │
│  │ ────────────────────────────────────────────────────── │ │
│  │ id (PK)              BIGINT                             │ │
│  │ conversation_id (FK) VARCHAR(36)                        │ │
│  │ role                 VARCHAR(20)  (user/assistant)      │ │
│  │ content              CLOB                               │ │
│  │ timestamp            TIMESTAMP                          │ │
│  └────────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘

EXAMPLE DATA:
─────────────────────────────────────────────────────────────────
conversations:
conversation_id: abc-123
user_id: john.doe@company.com
repository_url: https://github.com/company/app
mode: EXPLORE
is_active: true
created_at: 2026-01-04 10:00:00
last_activity: 2026-01-04 10:05:30

conversation_messages:
1. role: user
   content: "Explain how ChatController works"
   timestamp: 2026-01-04 10:00:00

2. role: assistant
   content: "Let me search for ChatController..."
   timestamp: 2026-01-04 10:00:05

3. role: assistant
   content: "ChatController is a REST API controller..."
   timestamp: 2026-01-04 10:00:15
─────────────────────────────────────────────────────────────────

┌──────────────────────────────────────────────────────────────┐
│ CONVERSATION CONTEXT BUILDING                                │
│ ────────────────────────────────────────────────────────────│
│                                                              │
│ When user sends new message:                                │
│ 1. Fetch conversation by ID                                 │
│ 2. Load last N messages (e.g., 10)                          │
│ 3. Build prompt:                                            │
│                                                              │
│    System: "You are a code assistant..."                    │
│    History:                                                 │
│      User: "Explain how ChatController works"               │
│      Assistant: "ChatController is a REST API..."           │
│    Current:                                                 │
│      User: "What methods does it have?"                     │
│                                                              │
│ LLM has full context to answer follow-up questions!         │
└──────────────────────────────────────────────────────────────┘
```

---

## 8. Performance & Scalability

```
┌─────────────────────────────────────────────────────────────┐
│                  PERFORMANCE METRICS                         │
│ ──────────────────────────────────────────────────────────  │
│                                                              │
│  Operation                  Latency        Throughput        │
│  ──────────────────────────────────────────────────────────│
│  Exact match search         ~50ms          200 req/sec      │
│  Fuzzy search (CONTAINS)    ~500ms         20 req/sec       │
│  Vector search              ~200ms         50 req/sec       │
│  Repository indexing        ~30s/100 files 1 repo/min       │
│  Embedding generation       ~100ms/text    10 embed/sec     │
│  LLM response (7B local)    ~2-5s          10 req/sec       │
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                 SCALABILITY ARCHITECTURE                     │
│ ──────────────────────────────────────────────────────────  │
│                                                              │
│  Layer              Current         Scale to 1000 users     │
│  ──────────────────────────────────────────────────────────│
│  API (Spring Boot)  Single server   Load balancer + 3 pods  │
│  Neo4j              Single instance  Cluster (3 nodes)      │
│  Ollama             Single GPU       GPU cluster (5 nodes)  │
│  Oracle DB          Single instance  RAC cluster            │
│                                                              │
│  Estimated Cost:                                            │
│  - Current: $200/month (cloud + DB license)                │
│  - Scaled:  $2000/month (includes GPU servers)             │
│                                                              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│                   CACHING STRATEGY                           │
│ ──────────────────────────────────────────────────────────  │
│                                                              │
│  Cache Layer         TTL        Hit Rate       Benefit      │
│  ──────────────────────────────────────────────────────────│
│  Search results      1 hour     70%            -30% Neo4j   │
│  Embeddings          Forever    90%            -90% Ollama  │
│  LLM responses       1 day      40%            -40% LLM     │
│  Repository metadata 1 week     95%            -95% Git     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

These diagrams are ready to be copied into:
- PowerPoint presentations
- Confluence documentation
- Architecture review meetings
- Technical onboarding materials

**Rendering ASCII as Images**:
1. Copy-paste into https://asciiflow.com
2. Export as PNG
3. Add to presentation

**Converting to Mermaid** (for live diagrams):
Many of these can be rendered as Mermaid diagrams in GitHub/Confluence.

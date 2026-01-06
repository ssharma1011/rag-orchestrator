# AutoFlow RAG Orchestrator - Complete Architecture Guide

**Version:** 2.0.0
**Date:** January 2026
**Audience:** Technical Team / Stakeholders

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [System Overview](#system-overview)
3. [Architecture Diagrams](#architecture-diagrams)
4. [Request Flow](#request-flow)
5. [Neo4j Graph Schema](#neo4j-graph-schema)
6. [Embedding Pipeline](#embedding-pipeline)
7. [Search Mechanisms](#search-mechanisms)
8. [Tool System](#tool-system)
9. [API Reference](#api-reference)
10. [Configuration](#configuration)
11. [Deployment](#deployment)

---

## Executive Summary

AutoFlow is an **enterprise RAG (Retrieval-Augmented Generation) system** that enables:
- 🤖 Conversational code understanding
- 🔍 Semantic code search (meaning-based, not keyword)
- 📊 Knowledge graph of codebases (Neo4j)
- 🎯 Autonomous code generation following team patterns
- 🌐 Multi-repository intelligence

**Key Technologies:**
- **LLM**: Ollama (local) or Gemini (cloud)
- **Graph DB**: Neo4j (code relationships)
- **Embeddings**: 1024D vectors (mxbai-embed-large)
- **Framework**: Spring Boot + LangGraph4j

**Business Value:**
- Reduce onboarding time by 70% (instant codebase understanding)
- Eliminate context-switching (ask AI instead of Slack/docs)
- Enforce coding standards automatically
- Cross-repo impact analysis for refactoring

---

## System Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     USER / DEVELOPER                         │
│                                                              │
│  "Explain how authentication works"                         │
│  "Find all REST controllers"                                │
│  "Generate a new UserService following our patterns"        │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    REST API LAYER                            │
│  /api/v1/chat          - Conversational interface           │
│  /api/v1/search        - Direct code search                 │
│  /api/v1/index         - Repository indexing                │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                   AGENT ORCHESTRATOR                         │
│                                                              │
│  AutoFlowAgent (LangGraph4j)                                │
│  ├─ Conversation Management                                 │
│  ├─ Tool Selection (LLM-driven)                             │
│  ├─ Context Building                                        │
│  └─ Response Streaming (SSE)                                │
└──────────────────────────┬───────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌──────────────┐
│  TOOL SYSTEM  │  │  LLM PROVIDER │  │ CONVERSATION │
│               │  │               │  │  STORAGE     │
│ • search_code │  │ • Ollama      │  │              │
│ • semantic_   │  │   (Local 7B)  │  │ • Oracle DB  │
│   search      │  │               │  │ • Messages   │
│ • discover_   │  │ • Gemini      │  │ • History    │
│   project     │  │   (Cloud)     │  │ • Context    │
│ • graph_query │  │               │  │              │
│ • explain     │  └───────────────┘  └──────────────┘
│ • generate_   │
│   code        │
└───────┬───────┘
        │
        ▼
┌───────────────────────────────────────────────────────────────┐
│              KNOWLEDGE LAYER (Neo4j + Embeddings)             │
│                                                               │
│  ┌─────────────┐   ┌──────────────┐   ┌──────────────┐      │
│  │  INDEXING   │→  │   GRAPH DB   │←  │   SEARCH     │      │
│  │  PIPELINE   │   │   (Neo4j)    │   │   ENGINE     │      │
│  │             │   │              │   │              │      │
│  │ • Clone     │   │ • Type nodes │   │ • Hybrid     │      │
│  │ • Parse     │   │ • Method     │   │ • Semantic   │      │
│  │ • Embed     │   │   nodes      │   │ • Vector     │      │
│  │ • Store     │   │ • Relations  │   │ • Keyword    │      │
│  └─────────────┘   │ • Embeddings │   └──────────────┘      │
│                    └──────────────┘                          │
└───────────────────────────────────────────────────────────────┘
```

---

## Architecture Diagrams

### 1. Complete Request Flow

```
┌──────────────────────────────────────────────────────────────────┐
│ STEP 1: User Sends Message                                       │
└──────────────────────────────────────────────────────────────────┘
        │
        │ POST /api/v1/chat
        │ { "message": "Explain ChatController", "repositoryUrl": "..." }
        ▼
┌──────────────────────────────────────────────────────────────────┐
│ ChatController.chat()                                             │
│ ─────────────────────────────────────────────────────────────────│
│ 1. Validate request (message required)                           │
│ 2. Get/Create Conversation (conversationService)                 │
│ 3. Save user message to DB                                       │
│ 4. Submit to CompletableFuture.runAsync()                        │
│ 5. Return { conversationId, streamUrl }                          │
└───────────────────────────┬──────────────────────────────────────┘
                            │
                            │ Async Processing
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│ STEP 2: AutoFlowAgent.processMessage()                           │
└──────────────────────────────────────────────────────────────────┘
        │
        │ 1. Get conversation with history
        │ 2. Create ToolContext (repoUrl, branch, repoIds)
        │ 3. Enter Agent Loop (max 10 iterations)
        ▼
┌──────────────────────────────────────────────────────────────────┐
│ AGENT LOOP                                                        │
│ ─────────────────────────────────────────────────────────────────│
│                                                                   │
│  ┌─────────────────────────────────────────────────────┐         │
│  │ 1. Build LLM Prompt                                 │         │
│  │    ├─ System prompt (role, capabilities)            │         │
│  │    ├─ Tool descriptions (JSON schema)               │         │
│  │    ├─ Conversation history (context)                │         │
│  │    └─ Current user message                          │         │
│  └─────────────────┬───────────────────────────────────┘         │
│                    │                                              │
│                    ▼                                              │
│  ┌─────────────────────────────────────────────────────┐         │
│  │ 2. Call LLM (Ollama qwen2.5-coder:7b)              │         │
│  │    Request: "User asks: Explain ChatController"     │         │
│  │    Response: "I'll use the search_code tool..."     │         │
│  └─────────────────┬───────────────────────────────────┘         │
│                    │                                              │
│                    ▼                                              │
│  ┌─────────────────────────────────────────────────────┐         │
│  │ 3. Parse LLM Response                               │         │
│  │    ├─ Contains tool call? → Execute tool            │         │
│  │    └─ Final answer? → Return to user                │         │
│  └─────────────────┬───────────────────────────────────┘         │
│                    │                                              │
│                    │ Tool call detected                           │
│                    ▼                                              │
└────────────────────────────────────────────────────────────────────┘
        │
        │ Tool: search_code, params: { query: "ChatController" }
        ▼
┌──────────────────────────────────────────────────────────────────┐
│ STEP 3: Tool Execution (with Interceptors)                       │
└──────────────────────────────────────────────────────────────────┘
        │
        ▼
┌──────────────────────────────────────────────────────────────────┐
│ IndexingInterceptor.beforeToolExecution()                        │
│ ─────────────────────────────────────────────────────────────────│
│ 1. Check: Is repo indexed? (query Neo4j by URL)                 │
│ 2. If NOT indexed:                                               │
│    ├─ Clone repository (GitOperationsService)                   │
│    ├─ Parse Java files (JavaParserService)                      │
│    ├─ Generate embeddings (EmbeddingService)                    │
│    ├─ Store in Neo4j (Neo4jGraphStoreImpl)                      │
│    └─ Emit SSE events (indexing progress)                       │
│ 3. Update ToolContext.repositoryIds                             │
└───────────────────────────┬──────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│ SearchCodeTool.execute()                                         │
│ ─────────────────────────────────────────────────────────────────│
│ 1. Tokenize query: "ChatController" → ["chatcontroller"]        │
│ 2. Build Cypher query (HYBRID mode):                            │
│    a. Try EXACT match: WHERE toLower(name) = 'chatcontroller'   │
│    b. Fallback FUZZY: WHERE name CONTAINS 'chatcontroller'      │
│ 3. Execute on Neo4j                                              │
│ 4. Return SearchResult[] with:                                  │
│    - fullyQualifiedName                                          │
│    - sourceCode                                                  │
│    - filePath                                                    │
│    - score                                                       │
└───────────────────────────┬──────────────────────────────────────┘
                            │
                            │ Tool Result: Found ChatController.java
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│ AGENT LOOP (Iteration 2)                                         │
│ ─────────────────────────────────────────────────────────────────│
│ 1. Build prompt with tool result:                               │
│    "Tool search_code returned: ChatController is a REST..."      │
│ 2. Call LLM again                                                │
│ 3. LLM synthesizes answer from search results                    │
│ 4. LLM returns final response (no more tool calls)               │
└───────────────────────────┬──────────────────────────────────────┘
                            │
                            ▼
┌──────────────────────────────────────────────────────────────────┐
│ STEP 4: Response & Persistence                                   │
│ ─────────────────────────────────────────────────────────────────│
│ 1. Save assistant response to conversation                       │
│ 2. Emit SSE COMPLETE event with final answer                     │
│ 3. Update conversation.lastActivity                              │
└──────────────────────────────────────────────────────────────────┘
        │
        │ SSE Stream to Client
        ▼
┌──────────────────────────────────────────────────────────────────┐
│ USER RECEIVES RESPONSE                                            │
│                                                                   │
│ "ChatController is the main REST API for chat functionality.    │
│  It handles POST requests to /api/v1/chat, processes messages   │
│  through AutoFlowAgent, and streams responses via SSE..."        │
└──────────────────────────────────────────────────────────────────┘
```

### 2. SSE Event Flow

```
┌──────────────────────────────────────────────────────────────────┐
│ Client: GET /api/v1/chat/{conversationId}/stream                 │
└──────────────────────────┬───────────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────────┐
│ ChatStreamService                                                 │
│ ─────────────────────────────────────────────────────────────────│
│ • Maintains SseEmitter per conversation                          │
│ • Buffers events (for late connections)                          │
│ • 15-minute timeout                                              │
│ • Auto-cleanup on disconnect                                     │
└───────────────────────────┬──────────────────────────────────────┘
                            │
                            ▼
        ┌───────────────────┴───────────────────┐
        │                                       │
        ▼                                       ▼
┌──────────────────┐                  ┌──────────────────┐
│ Event: CONNECTED │                  │ Event: THINKING  │
│ ────────────────│                  │ ────────────────│
│ timestamp        │                  │ message:         │
│ conversationId   │                  │ "Analyzing..."   │
└──────────────────┘                  └──────────────────┘
        │                                       │
        ▼                                       ▼
┌──────────────────┐                  ┌──────────────────┐
│ Event: TOOL      │                  │ Event: COMPLETE  │
│ ────────────────│                  │ ────────────────│
│ toolName:        │                  │ content:         │
│ "search_code"    │                  │ "ChatController  │
│ status: running  │                  │  is a REST..."   │
└──────────────────┘                  └──────────────────┘
```

---

## Neo4j Graph Schema

### Node Types

```cypher
// Repository metadata
(:Repository {
  id: UUID,
  url: String,
  name: String,
  branch: String,
  lastIndexedAt: DateTime,
  lastIndexedCommit: String
})

// Classes, Interfaces, Enums
(:Type {
  id: UUID,
  repositoryId: UUID,
  name: String,                    // "ChatController"
  fqn: String,                     // "com.example.api.ChatController"
  packageName: String,             // "com.example.api"
  filePath: String,                // "src/main/java/.../ChatController.java"
  kind: String,                    // "CLASS", "INTERFACE", "ENUM"
  description: String,             // "REST controller for chat..."
  embedding: List<Double>[1024],   // Vector embedding
  sourceCode: String,              // Full source code
  startLine: Integer,
  endLine: Integer
})

// Methods
(:Method {
  id: UUID,
  repositoryId: UUID,
  name: String,                    // "chat"
  signature: String,               // "chat(ChatRequest)"
  returnType: String,              // "ResponseEntity"
  description: String,             // "Handles POST requests..."
  embedding: List<Double>[1024],   // Vector embedding
  sourceCode: String,              // Method source code
  startLine: Integer,
  endLine: Integer,
  visibility: String               // "public", "private"
})

// Fields
(:Field {
  id: UUID,
  repositoryId: UUID,
  name: String,                    // "conversationService"
  type: String,                    // "ConversationService"
  lineNumber: Integer
})

// Annotations
(:Annotation {
  id: UUID,
  fqn: String,                     // "org.springframework.web.bind.annotation.RestController"
  repositoryId: UUID
})
```

### Relationships

```cypher
// Class declares methods
(Type)-[:DECLARES]->(Method)

// Class declares fields
(Type)-[:DECLARES]->(Field)

// Class/Method has annotations
(Type)-[:ANNOTATED_BY]->(Annotation)
(Method)-[:ANNOTATED_BY]->(Annotation)

// Class inheritance
(Type)-[:EXTENDS]->(Type)
(Type)-[:IMPLEMENTS]->(Type)

// Method calls
(Method)-[:CALLS]->(Method)
```

### Vector Indexes

```cypher
// Type embedding index
CREATE VECTOR INDEX type_embedding_index
FOR (t:Type)
ON t.embedding
OPTIONS {
  indexConfig: {
    `vector.dimensions`: 1024,
    `vector.similarity_function`: 'cosine'
  }
}

// Method embedding index
CREATE VECTOR INDEX method_embedding_index
FOR (m:Method)
ON m.embedding
OPTIONS {
  indexConfig: {
    `vector.dimensions`: 1024,
    `vector.similarity_function`: 'cosine'
  }
}
```

### Example Graph

```
(:Repository {
  id: "abc-123",
  url: "github.com/company/app",
  name: "company-app"
})

(:Type {
  name: "ChatController",
  fqn: "com.example.api.ChatController",
  kind: "CLASS",
  embedding: [0.12, 0.45, ...]  // 1024 dimensions
})
  |
  ├─[:DECLARES]→(:Method {
  │   name: "chat",
  │   signature: "chat(ChatRequest)",
  │   embedding: [0.34, 0.78, ...]
  │ })
  │
  ├─[:DECLARES]→(:Method {
  │   name: "getHistory",
  │   embedding: [0.56, 0.91, ...]
  │ })
  │
  ├─[:DECLARES]→(:Field {
  │   name: "conversationService",
  │   type: "ConversationService"
  │ })
  │
  └─[:ANNOTATED_BY]→(:Annotation {
      fqn: "org.springframework.web.bind.annotation.RestController"
    })
```

---

## Embedding Pipeline

### How Embeddings Work

**Purpose**: Convert code into numerical vectors for semantic similarity search.

**Example**:
```java
// Original Code
public ResponseEntity<ChatResponse> chat(ChatRequest request) {
    return conversationService.processMessage(request);
}

// Step 1: Generate Rich Description (DescriptionGenerator)
"Method: chat
Purpose: HTTP POST endpoint for chat messages
Parameters: ChatRequest request
Returns: ResponseEntity<ChatResponse>
Annotations: @PostMapping
Calls: conversationService.processMessage"

// Step 2: Generate Embedding (Ollama mxbai-embed-large)
[0.123, 0.456, 0.789, ..., 0.321]  // 1024 numbers

// Step 3: Store in Neo4j
CREATE (:Method {
  name: "chat",
  embedding: [0.123, 0.456, ...]
})
```

### Similarity Search

```
User Query: "endpoint for sending messages"
   ↓
Generate Query Embedding: [0.145, 0.432, ...]
   ↓
Compare with Method Embeddings (Cosine Similarity)
   ↓
chat method: 0.89 similarity (HIGH MATCH) ✅
getHistory method: 0.34 similarity (LOW MATCH) ❌
   ↓
Return: chat method
```

### Pipeline Detailed Flow

```
┌──────────────────────────────────────────────────────────────┐
│ STEP 1: Repository Cloning                                   │
│ ──────────────────────────────────────────────────────────── │
│ GitOperationsService.cloneRepository()                       │
│ • Input: repositoryUrl, branch                               │
│ • Output: Local directory path                               │
│ • Uses: JGit library                                         │
└───────────────────────┬──────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ STEP 2: Java File Parsing                                    │
│ ──────────────────────────────────────────────────────────── │
│ JavaParserService.parseJavaFiles()                           │
│ • Find all .java files (exclude /test/)                      │
│ • Parse with JavaParser library                              │
│ • Extract:                                                    │
│   - Package name                                              │
│   - Class name, annotations                                  │
│   - Methods (name, params, return type, body, calls)         │
│   - Fields (name, type, annotations)                         │
│   - Inheritance (extends, implements)                        │
│ • Output: List<JavaClass> models                             │
└───────────────────────┬──────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ STEP 3: Description Generation                               │
│ ──────────────────────────────────────────────────────────── │
│ DescriptionGeneratorImpl.generateClassDescription()          │
│                                                               │
│ Template for Class:                                          │
│ "Class: {name}                                               │
│  Package: {packageName}                                      │
│  Kind: {kind}                                                │
│  Purpose: {inferred from annotations}                        │
│  Annotations: {annotations}                                  │
│  Methods: {method names}                                     │
│  Fields: {field names}                                       │
│  Extends: {parent class}                                     │
│  Implements: {interfaces}"                                   │
│                                                               │
│ Template for Method:                                         │
│ "Method: {name}                                              │
│  Signature: {returnType} {name}({parameters})                │
│  Purpose: {inferred from name + annotations}                 │
│  Annotations: {annotations}                                  │
│  Calls: {called methods}                                     │
│  Returns: {returnType}"                                      │
└───────────────────────┬──────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ STEP 4: Embedding Generation                                 │
│ ──────────────────────────────────────────────────────────── │
│ EmbeddingServiceImpl.generateClassEmbedding()                │
│                                                               │
│ • Uses: Ollama mxbai-embed-large model                       │
│ • Input: Rich text description (from step 3)                 │
│ • Process:                                                    │
│   1. HTTP POST to http://localhost:11434/api/embeddings      │
│   2. Request: { model: "mxbai-embed-large", prompt: "..." }  │
│   3. Response: { embedding: [0.12, 0.45, ..., 0.89] }        │
│ • Output: List<Double> (1024 dimensions)                     │
│                                                               │
│ LangChain4j Integration:                                     │
│ • Automatic retry on failure (3 attempts)                    │
│ • Exponential backoff                                        │
│ • Timeout handling (120s)                                    │
└───────────────────────┬──────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ STEP 5: Neo4j Storage                                        │
│ ──────────────────────────────────────────────────────────── │
│ Neo4jGraphStoreImpl.storeJavaClass()                         │
│                                                               │
│ For Each Class:                                              │
│   1. CREATE/MERGE (:Type) node                               │
│   2. Set properties (name, fqn, embedding, sourceCode, ...)  │
│   3. For each method:                                        │
│      a. CREATE (:Method) node with embedding                 │
│      b. CREATE (Type)-[:DECLARES]->(Method)                  │
│   4. For each field:                                         │
│      a. CREATE (:Field) node                                 │
│      b. CREATE (Type)-[:DECLARES]->(Field)                   │
│   5. For each annotation:                                    │
│      a. MERGE (:Annotation) node (de-duplicate)              │
│      b. CREATE (Type)-[:ANNOTATED_BY]->(Annotation)          │
│   6. For each parent class:                                  │
│      a. CREATE (Type)-[:EXTENDS/IMPLEMENTS]->(ParentType)    │
└───────────────────────┬──────────────────────────────────────┘
                        │
                        ▼
┌──────────────────────────────────────────────────────────────┐
│ RESULT: Knowledge Graph Ready for Search                     │
│                                                               │
│ • Type nodes with embeddings                                 │
│ • Method nodes with embeddings                               │
│ • Rich relationships (DECLARES, CALLS, ANNOTATED_BY)         │
│ • Vector indexes for similarity search                       │
└──────────────────────────────────────────────────────────────┘
```

---

## Search Mechanisms

### 1. Hybrid Search (Default)

**Strategy**: Try exact match first (fast), fall back to fuzzy if needed.

```
User Query: "ChatController"
   ↓
STEP 1: Exact Match (Index-backed, <50ms)
   ↓
MATCH (t:Type)
WHERE toLower(t.name) = toLower("chatcontroller")
RETURN t
   ↓
Found? → Return immediately ✅
   │
   │ Not found
   ▼
STEP 2: Fuzzy Match (Full scan, ~500ms)
   ↓
MATCH (t:Type)
WHERE (t.name IS NOT NULL AND toLower(t.name) CONTAINS "chatcontroller")
   OR (t.fqn IS NOT NULL AND toLower(t.fqn) CONTAINS "chatcontroller")
   OR (t.sourceCode IS NOT NULL AND toLower(t.sourceCode) CONTAINS "chatcontroller")
RETURN t
```

**Benefits**:
- 10-100x faster for exact name matches
- Graceful fallback for partial matches
- Uses Neo4j indexes optimally

### 2. Semantic Search (Vector Similarity)

**Strategy**: Understand meaning, not keywords.

```
User Query: "code that handles chat messages"
   ↓
STEP 1: Generate Query Embedding
OllamaClient.embed("code that handles chat messages")
→ [0.234, 0.567, ..., 0.890]  // 1024D vector
   ↓
STEP 2: Vector Search on Type Nodes
CALL db.index.vector.queryNodes(
  'type_embedding_index',
  10,
  [0.234, 0.567, ..., 0.890]
) YIELD node, score
WHERE score > 0.65
RETURN node.name, node.fqn, node.sourceCode, score
ORDER BY score DESC
   ↓
Results:
1. ChatController (score: 0.89) ✅
2. MessageHandler (score: 0.76) ✅
3. UserService (score: 0.42) ❌ (filtered by score < 0.65)
```

**Benefits**:
- Finds relevant code even without exact keywords
- Understands synonyms ("messages" = "chat")
- Filters low-quality matches (score threshold)

### 3. Comparison Matrix

| Query | Exact Match Result | Semantic Search Result |
|-------|-------------------|------------------------|
| "ChatController" | ✅ Finds ChatController.java | ✅ Finds ChatController + similar classes |
| "chat endpoint" | ❌ No exact match | ✅ Finds ChatController (understands "endpoint" = REST controller) |
| "com.example.api.ChatController" | ✅ Finds by FQN | ✅ Finds by package name |
| "message handling" | ❌ No class named this | ✅ Finds ChatController, MessageService |

---

## Tool System

### Available Tools

| Tool Name | Description | Requires Index | Example Query |
|-----------|-------------|----------------|---------------|
| **search_code** | Keyword search in code | ✅ | "Find ChatController" |
| **semantic_search** | AI-powered meaning search | ✅ | "code that processes payments" |
| **discover_project** | Find classes by annotations | ✅ | "all @RestController classes" |
| **graph_query** | Execute Cypher queries | ✅ | "classes that extend BaseService" |
| **dependency** | Analyze dependencies | ✅ | "what calls UserService?" |
| **explain** | Explain code concepts | ✅ | "how does authentication work?" |
| **generate_code** | Generate new code | ✅ | "create ProductService following our patterns" |
| **index** | Trigger indexing | ❌ | "index this repository" |

### Tool Execution Flow

```
LLM Output: "I'll use the search_code tool with query 'ChatController'"
   ↓
Parse Tool Call:
{
  "tool": "search_code",
  "parameters": {
    "query": "ChatController",
    "mode": "hybrid"
  }
}
   ↓
Before Execution → IndexingInterceptor
├─ Check: Is repo indexed?
├─ If NO → Clone + Parse + Embed + Store
└─ Update ToolContext.repositoryIds
   ↓
Execute Tool → SearchCodeTool.execute()
├─ Build Cypher query (hybrid mode)
├─ Execute on Neo4j
└─ Return SearchResult[]
   ↓
Format Tool Result for LLM:
"Found 1 result:
- ChatController.java
  Location: src/main/java/com/example/api/
  Source: [full code here]"
   ↓
LLM uses result to answer user question
```

---

## API Reference

### 1. Chat Endpoint

**POST** `/api/v1/chat`

Start or continue a conversation.

**Request**:
```json
{
  "message": "Explain how authentication works",
  "repositoryUrl": "https://github.com/company/app",
  "branch": "main",
  "conversationId": "optional-existing-id"
}
```

**Response**:
```json
{
  "conversationId": "uuid-here",
  "streamUrl": "/api/v1/chat/uuid-here/stream",
  "status": "processing"
}
```

**SSE Stream Events**:
```
event: CONNECTED
data: {"timestamp": "2026-01-04T10:00:00Z"}

event: THINKING
data: {"message": "Analyzing your request..."}

event: TOOL
data: {"toolName": "search_code", "status": "running"}

event: COMPLETE
data: {"content": "Authentication is handled by..."}
```

### 2. Search Endpoint

**POST** `/api/v1/search`

Direct code search (bypass agent).

**Request**:
```json
{
  "query": "ChatController",
  "repositoryUrl": "https://github.com/company/app",
  "mode": "hybrid",
  "maxResults": 10
}
```

**Response**:
```json
{
  "success": true,
  "results": [
    {
      "entityId": "uuid",
      "fullyQualifiedName": "com.example.api.ChatController",
      "filePath": "src/main/java/com/example/api/ChatController.java",
      "content": "public class ChatController {...}",
      "score": 1.0,
      "searchMode": "HYBRID"
    }
  ]
}
```

### 3. Indexing Endpoint

**POST** `/api/v1/index/repo`

Manually trigger repository indexing.

**Request**:
```json
{
  "repositoryUrl": "https://github.com/company/app",
  "branch": "main"
}
```

**Response**:
```json
{
  "repositoryId": "uuid",
  "status": "IN_PROGRESS",
  "message": "Indexing started"
}
```

**GET** `/api/v1/index/{repositoryId}/status`

Check indexing progress.

**Response**:
```json
{
  "repositoryId": "uuid",
  "status": "COMPLETED",
  "progress": {
    "totalFiles": 150,
    "processedFiles": 150,
    "totalClasses": 89,
    "totalMethods": 456,
    "duration": "45s"
  }
}
```

---

## Configuration

### application.yml

```yaml
app:
  # LLM Provider: 'ollama' (local), 'gemini' (cloud), 'hybrid'
  llm-provider: ollama

  ollama:
    base-url: http://localhost:11434
    chat-model: qwen2.5-coder:7b  # 7B for quality, 1.5B for speed
    embedding-model: mxbai-embed-large  # 1024 dimensions
    num-ctx: 32768  # Large context window
    timeout-seconds: 120
    max-retries: 3

  gemini:
    api-key: ${GEMINI_KEY}
    chat-model: gemini-flash-latest
    retry:
      max-attempts: 6
      initial-backoff-seconds: 10

  code-quality:
    max-complexity: 10
    max-method-lines: 20
    max-parameters: 4
    enforce-interface-pattern: true
```

### Environment Variables

```bash
# Required
export GEMINI_KEY=your-api-key
export NEO4J_URI=bolt://localhost:7687
export NEO4J_USER=neo4j
export NEO4J_PASSWORD=password

# Optional
export LLM_PROVIDER=ollama
export WORKSPACE_DIR=/tmp/autoflow-workspace
```

---

## Deployment

### Prerequisites

1. **Java 17+**
2. **Neo4j 5.x**
   ```bash
   docker run -d \
     -p 7474:7474 -p 7687:7687 \
     -e NEO4J_AUTH=neo4j/password \
     neo4j:5.13
   ```

3. **Ollama** (for local LLM)
   ```bash
   curl -fsSL https://ollama.ai/install.sh | sh
   ollama pull qwen2.5-coder:7b
   ollama pull mxbai-embed-large
   ```

4. **Oracle DB** (for conversations)

### Running Locally

```bash
# 1. Build
mvn clean install -DskipTests

# 2. Run
mvn spring-boot:run

# 3. Test
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello", "repositoryUrl": "https://github.com/..."}'
```

### Docker Deployment

```dockerfile
FROM eclipse-temurin:17-jre
COPY target/ai-rag-orchestrator-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t autoflow-rag .
docker run -p 8080:8080 \
  -e NEO4J_URI=bolt://host.docker.internal:7687 \
  -e GEMINI_KEY=your-key \
  autoflow-rag
```

---

## Performance Metrics

| Operation | Time | Notes |
|-----------|------|-------|
| Exact match search | ~50ms | Index-backed |
| Fuzzy search (CONTAINS) | ~500ms | Full scan |
| Vector search | ~200ms | Depends on result size |
| Repository indexing | ~30s per 100 files | Includes parsing + embeddings |
| Embedding generation | ~100ms per text | Ollama local |
| LLM response | ~2-5s | Depends on model (7B vs 1.5B) |

---

## Troubleshooting

### Issue: Search returns 0 results

**Cause**: Repository not indexed

**Solution**:
```bash
# Check if indexed
curl http://localhost:8080/api/v1/search/repos

# Manually index
curl -X POST http://localhost:8080/api/v1/index/repo \
  -H "Content-Type: application/json" \
  -d '{"repositoryUrl": "https://github.com/..."}'
```

### Issue: 429 Rate Limit Errors

**Cause**: Using Gemini with high request volume

**Solution**: Switch to Ollama
```yaml
app:
  llm-provider: ollama  # Change from 'gemini'
```

### Issue: Embeddings not generated

**Cause**: Ollama not running or model not pulled

**Solution**:
```bash
ollama serve
ollama pull mxbai-embed-large
```

---

## Next Steps

1. **Test the system** with your codebase
2. **Monitor performance** (indexing time, search latency)
3. **Tune LLM model** (7B for quality, 1.5B for speed)
4. **Integrate with CI/CD** (auto-index on commits)
5. **Scale** (multiple Neo4j instances, load balancer)

---

**For questions or issues, refer to:**
- `IMPROVEMENTS_SUMMARY.md` - Recent architectural changes
- `PROMPT_CATALOG.md` - All prompt templates
- `CLEANUP_GUIDE.md` - Obsolete code to delete

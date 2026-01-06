# Codebase Understanding: Architecture Design Document

**Goal:** Make the LLM explain THIS codebase with 100% accuracy. Any question a developer asks, the system should answer correctly.

**Date:** 2026-01-03
**Status:** Design Phase (No code yet)

---

## Table of Contents
1. [How LLMs Actually Work](#1-how-llms-actually-work)
2. [The Context Problem](#2-the-context-problem)
3. [Context Sources: Tradeoffs](#3-context-sources-tradeoffs)
4. [The Right Architecture](#4-the-right-architecture)
5. [JavaParser vs Spoon](#5-javaparser-vs-spoon)
6. [Failure Mode Analysis](#6-failure-mode-analysis)
7. [Implementation Roadmap](#7-implementation-roadmap)

---

## 1. How LLMs Actually Work

### The Fundamental Equation
```
LLM(Prompt + Context) → Response
```

**Key principle:** LLMs don't "know" anything. They only transform **text inputs** into **text outputs** based on patterns learned during training.

### What the LLM Sees (Token Perspective)

When you ask: *"Explain this project to me"*

The LLM receives:
```
System Prompt: You are AutoFlow, an AI assistant for understanding codebases.

Available Tools:
- discover_project: Find main classes, controllers, services using annotations
- search_code: Search for specific code patterns
- get_dependencies: Find what calls/depends on a class

Repository: https://github.com/user/rag-orchestrator
Branch: main

Recent Messages:
user: explain this project to me

Instructions: Either call a tool OR respond directly.
```

The LLM does NOT see:
- ❌ The actual source code (unless you give it)
- ❌ The architecture diagram
- ❌ The dependency graph
- ❌ The file structure

**It only sees TEXT in the prompt.**

### How the LLM Decides What to Do

1. **Pattern matching from training data**
   - "explain project" → Similar to questions about project overview
   - Training data probably had examples of discovering project structure

2. **Tool selection**
   - Sees `discover_project` tool description
   - Decides to call it because it matches the intent

3. **Response generation**
   - Gets tool results back (list of classes)
   - Synthesizes into natural language explanation

### The Quality Equation

```
Response Quality = f(
    Prompt Clarity,           // How well you describe the task
    Context Relevance,        // How relevant the retrieved info is
    Context Completeness,     // How much of the needed info is present
    Context Ordering,         // Most important info first
    LLM Capabilities         // Model's reasoning ability
)
```

**Critical insight:** If context is wrong/incomplete, LLM will hallucinate or give useless answers.

---

## 2. The Context Problem

### Context Window Limits

| Model | Context Window | Can Fit |
|-------|----------------|---------|
| GPT-4 | 128K tokens | ~40 Java files |
| Claude Sonnet | 200K tokens | ~60 Java files |
| Gemini Flash | 1M tokens | ~300 Java files |
| Qwen 2.5 Coder 7B | 32K tokens | ~10 Java files |

**Problem:** Your rag-orchestrator has **~150 Java files**. Even with 1M token window, you can't fit the entire codebase.

### The Three Questions Every Developer Ask

#### Q1: "What is this project?" (BREADTH)
**What LLM needs to know:**
- Main application class
- All controllers (entry points)
- All services (business logic)
- All repositories (data access)
- Key configuration files
- Overall architecture pattern (Spring Boot MVC, microservices, etc.)

**Context required:** ~20-50 classes (structure only, not full code)

#### Q2: "How does feature X work?" (DEPTH)
**Example:** "How does chat streaming work?"

**What LLM needs to know:**
- ChatController.streamChat() method
- ChatStreamService implementation
- SSE (Server-Sent Events) configuration
- How conversation state is managed
- Error handling

**Context required:** 5-10 classes (full implementation)

#### Q3: "Where is X implemented?" (SEARCH)
**Example:** "Where is the code that indexes repositories?"

**What LLM needs to know:**
- Classes/methods matching "indexing"
- Interfaces vs implementations
- Related configuration

**Context required:** 3-5 classes (interface + impl)

### The Context Assembly Problem

**For each question type, you need DIFFERENT context:**

```
"Explain project" → Need: Breadth (all classes, shallow)
"How does X work" → Need: Depth (few classes, deep)
"Where is X"      → Need: Search (find relevant, then depth)
```

**Current problem:** Your system tries to use ONE approach (search) for ALL question types.

---

## 3. Context Sources: Tradeoffs

### Option A: Raw Documents (Naive RAG)

**How it works:**
```
1. Chunk source files into 500-token segments
2. Store chunks in vector DB with embeddings
3. User asks question → embed question
4. Find top-K similar chunks
5. Pass chunks to LLM as context
```

**Example:**
```python
# Chunk 1 (from ChatController.java lines 1-30)
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    private final AutoFlowAgent agent;
    ...
}

# Chunk 2 (from ChatController.java lines 31-60)
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
    ...
}
```

**Pros:**
- ✅ Simple to implement
- ✅ Works for "Where is X" questions

**Cons:**
- ❌ Loses structure (can't answer "What calls this?")
- ❌ Chunks may split related code
- ❌ No understanding of class boundaries
- ❌ Can't answer "Explain project" (too fragmented)

**Verdict:** Not sufficient for codebase understanding.

---

### Option B: Knowledge Graph (Structural)

**How it works:**
```
1. Parse source files → extract entities (Class, Method, Field)
2. Extract relationships (CALLS, DECLARES, IMPLEMENTS)
3. Store in graph database (Neo4j)
4. User asks question → translate to Cypher query
5. Execute query → get structured results
6. Pass results to LLM
```

**Example Neo4j structure:**
```cypher
(ChatController:Class)-[:DECLARES]->(chat:Method)
(chat:Method)-[:CALLS]->(agent.process:Method)
(agent.process:Method)-[:CALLS]->(runAgentLoop:Method)
```

**Pros:**
- ✅ Perfect for "What calls X?" questions
- ✅ Can traverse dependency chains
- ✅ Efficient for structural queries

**Cons:**
- ❌ No semantic understanding ("authentication logic" won't match)
- ❌ Requires exact entity names
- ❌ Doesn't help with "How does X work?" (need actual code)

**Verdict:** Necessary but not sufficient.

---

### Option C: Vector Embeddings (Semantic)

**How it works:**
```
1. For each class/method, create enriched description:
   "Class: ChatController
    Purpose: Handles HTTP requests for chat API
    Annotations: @RestController, @RequestMapping
    Methods: chat(), streamChat(), getHistory()
    Dependencies: AutoFlowAgent, ConversationService"

2. Generate embedding vector (1536 dimensions for OpenAI)

3. Store in Neo4j with vector index:
   CREATE VECTOR INDEX class_embeddings
   FOR (c:Class) ON (c.embedding)

4. User asks "How does chat work?"
   → Embed question
   → Find similar vectors
   → Return top-K classes
```

**Pros:**
- ✅ Finds conceptually related code
- ✅ Works with natural language queries
- ✅ Doesn't require exact names

**Cons:**
- ❌ No structural information (can't traverse call graph)
- ❌ May return irrelevant but similar-sounding code
- ❌ Embedding quality depends on description quality

**Verdict:** Necessary but not sufficient.

---

### Option D: Hybrid Approach (THE ANSWER)

**Combine all three:**

```
┌─────────────────────────────────────────────────────────────┐
│                    USER QUESTION                            │
└────────────┬────────────────────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────────────────────────────────┐
│              QUERY ROUTER (LLM-based)                       │
│  Classifies question type:                                  │
│  - PROJECT_OVERVIEW → Use discover_project                  │
│  - FEATURE_DEEP_DIVE → Use semantic search + graph traverse │
│  - CODE_SEARCH → Use hybrid search                          │
└────────────┬────────────────────────────────────────────────┘
             │
       ┌─────┴─────┬─────────────┬────────────┐
       ▼           ▼             ▼            ▼
   ┌──────┐   ┌─────────┐   ┌────────┐   ┌──────────┐
   │GRAPH │   │ VECTOR  │   │  RAW   │   │ METADATA │
   │QUERY │   │ SEARCH  │   │  CODE  │   │  (ANNOT) │
   └──┬───┘   └────┬────┘   └───┬────┘   └────┬─────┘
      │            │             │             │
      └────────────┴─────────────┴─────────────┘
                   │
                   ▼
         ┌──────────────────────┐
         │  CONTEXT ASSEMBLER   │
         │  Merges results into │
         │  coherent context    │
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │    LLM PROMPT        │
         │  System + Context +  │
         │  User Question       │
         └──────────┬───────────┘
                    │
                    ▼
         ┌──────────────────────┐
         │   LLM RESPONSE       │
         └──────────────────────┘
```

**For "Explain this project":**
1. GRAPH: Find all classes with @SpringBootApplication, @RestController, @Service
2. METADATA: Get class names, annotations, file paths
3. ASSEMBLE: Create summary of architecture
4. LLM: Synthesize into explanation

**For "How does chat streaming work?":**
1. VECTOR: Search for "chat streaming server-sent events"
2. GRAPH: Get ChatController → ChatStreamService call chain
3. RAW CODE: Fetch actual implementation of key methods
4. ASSEMBLE: Full context with structure + code
5. LLM: Explain the flow

**For "Where is repository indexing?":**
1. VECTOR: Search for "repository indexing scan parse"
2. GRAPH: Find related classes (IndexingService, Neo4jIndexingServiceImpl)
3. RAW CODE: Show the actual implementation
4. LLM: Point to specific files and methods

---

## 4. The Right Architecture

### Layer 1: Indexing Pipeline

```
┌──────────────────────────────────────────────────────────────┐
│                    REPOSITORY INPUT                          │
│  User provides: URL + branch                                 │
└────────────┬─────────────────────────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────────────────────┐
│                   FILE DISCOVERY                           │
│  Find all .java files (exclude tests for now)             │
└────────────┬───────────────────────────────────────────────┘
             │
             ▼
┌────────────────────────────────────────────────────────────┐
│                   PARSING LAYER                            │
│  Parser: JavaParser or Spoon (DECISION NEEDED)            │
│                                                            │
│  For each .java file:                                     │
│  1. Parse to AST                                          │
│  2. Extract:                                              │
│     - Classes (name, FQN, annotations, extends, implements) │
│     - Methods (name, signature, annotations, body)         │
│     - Fields (name, type, annotations)                     │
│     - Imports (dependencies)                               │
│  3. Extract relationships:                                 │
│     - Class DECLARES Method                                │
│     - Method CALLS Method (from AST method invocations)    │
│     - Class DEPENDS_ON Class (from imports + usage)        │
└────────────┬───────────────────────────────────────────────┘
             │
       ┌─────┴─────┬────────────┬───────────┐
       ▼           ▼            ▼           ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│  GRAPH   │ │ ENRICHED │ │ EMBEDDING│ │   RAW    │
│  STORE   │ │   TEXT   │ │   GEN    │ │   CODE   │
│ (Neo4j)  │ │ (Prepare)│ │ (Vectors)│ │  STORE   │
└──────────┘ └────┬─────┘ └────┬─────┘ └──────────┘
                  │             │
                  └──────┬──────┘
                         ▼
              ┌──────────────────────┐
              │  Neo4j Vector Index  │
              │  ON (c:Class).embed  │
              │  ON (m:Method).embed │
              └──────────────────────┘
```

### Layer 2: Query Execution

```
User Question: "How does chat streaming work?"
        │
        ▼
┌──────────────────┐
│  Query Router    │  ← LLM classifies question type
│  (LLM-based)     │
└────┬─────────────┘
     │
     ▼
Question Type: FEATURE_DEEP_DIVE
Strategy: Semantic Search → Graph Traverse → Code Fetch
     │
     ├─→ Step 1: Semantic Search
     │   Input: "chat streaming server-sent events"
     │   Query: db.index.vector.queryNodes('class_embeddings', 5, $embedding)
     │   Result: [ChatController, ChatStreamService, ...]
     │
     ├─→ Step 2: Graph Traverse
     │   Input: ChatController
     │   Query: MATCH (c:Class {name: 'ChatController'})-[:DECLARES]->(m:Method)
     │          MATCH (m)-[:CALLS*1..3]->(called)
     │          RETURN m, called
     │   Result: [streamChat() → sendEvent() → ...]
     │
     └─→ Step 3: Code Fetch
         Input: [ChatController, ChatStreamService, streamChat, sendEvent]
         Query: MATCH (e) WHERE e.id IN $ids RETURN e.sourceCode
         Result: Full code of relevant classes/methods

         ▼
┌──────────────────────────────────────────────────────┐
│               CONTEXT ASSEMBLER                      │
│                                                      │
│  Assembles into structured prompt:                  │
│                                                      │
│  ## Architecture Overview                           │
│  ChatController handles HTTP requests...            │
│                                                      │
│  ## Key Classes                                     │
│  1. ChatController - REST endpoint                  │
│     - streamChat() method                           │
│                                                      │
│  2. ChatStreamService - SSE implementation          │
│     - sendEvent() method                            │
│                                                      │
│  ## Implementation Details                          │
│  [Full code of streamChat() method]                 │
│  [Full code of ChatStreamService]                   │
│                                                      │
│  ## Call Flow                                       │
│  ChatController.streamChat() →                      │
│    ChatStreamService.sendEvent() →                  │
│    SseEmitter.send()                                │
└────────────────┬─────────────────────────────────────┘
                 │
                 ▼
         ┌───────────────┐
         │  LLM PROMPT   │
         └───────┬───────┘
                 │
                 ▼
         ┌───────────────┐
         │  LLM RESPONSE │
         └───────────────┘
```

### Layer 3: Storage Schema

**Neo4j Graph:**
```cypher
// Node Types
(:Class {
  id: UUID,
  repositoryId: String,
  name: String,
  fqn: String,
  kind: "CLASS|INTERFACE|ENUM",
  annotations: [String],
  filePath: String,
  sourceCode: String,
  embedding: [Float]  ← Vector for semantic search
})

(:Method {
  id: UUID,
  repositoryId: String,
  name: String,
  signature: String,
  annotations: [String],
  sourceCode: String,
  embedding: [Float]
})

(:Field {
  id: UUID,
  repositoryId: String,
  name: String,
  type: String,
  annotations: [String]
})

(:Annotation {
  id: UUID,
  repositoryId: String,
  fqn: String  // "org.springframework.web.bind.annotation.RestController"
})

// Relationships
(Class)-[:DECLARES]->(Method)
(Class)-[:DECLARES]->(Field)
(Class)-[:ANNOTATED_BY]->(Annotation)
(Method)-[:ANNOTATED_BY]->(Annotation)
(Method)-[:CALLS]->(Method)
(Class)-[:DEPENDS_ON]->(Class)
(Class)-[:EXTENDS]->(Class)
(Class)-[:IMPLEMENTS]->(Class)
```

**Vector Indexes:**
```cypher
CREATE VECTOR INDEX class_embedding_index
FOR (c:Class) ON (c.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 1536,
  `vector.similarity_function`: 'cosine'
}}

CREATE VECTOR INDEX method_embedding_index
FOR (m:Method) ON (m.embedding)
OPTIONS {indexConfig: {
  `vector.dimensions`: 1536,
  `vector.similarity_function`: 'cosine'
}}
```

---

## 5. JavaParser vs Spoon

### What We Need From a Parser

| Requirement | Why |
|-------------|-----|
| Extract class metadata | Name, package, annotations, extends, implements |
| Extract method signatures | Name, parameters, return type, annotations |
| Extract method body | For call graph analysis (method invocations) |
| Extract field declarations | Name, type, annotations |
| Extract imports | For dependency analysis |
| Handle Java 17+ syntax | Your project uses Java 17 |
| Extract comments/Javadoc | For better embeddings |
| Performance | Need to parse 150+ files quickly |

### JavaParser Analysis

**Pros:**
- ✅ You already have it as a dependency
- ✅ Mature, well-documented (10K+ stars on GitHub)
- ✅ Supports Java 1-21
- ✅ Easy to use API
- ✅ Symbol resolution (can resolve types across files)
- ✅ Active community

**Cons:**
- ⚠️ Requires writing traversal logic yourself
- ⚠️ Need to manually extract call graphs

**Example code:**
```java
CompilationUnit cu = StaticJavaParser.parse(javaFile);

// Extract class
cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
    String className = cls.getNameAsString();
    List<String> annotations = cls.getAnnotations().stream()
        .map(a -> a.getNameAsString())
        .toList();

    // Extract methods
    cls.getMethods().forEach(method -> {
        String methodName = method.getNameAsString();

        // Extract method calls
        method.findAll(MethodCallExpr.class).forEach(call -> {
            String calledMethod = call.getNameAsString();
            // Store CALLS relationship
        });
    });
});
```

### Spoon Analysis

**Pros:**
- ✅ More powerful transformation capabilities
- ✅ Better AST querying (can use filters)
- ✅ Built-in visitors for common patterns
- ✅ Good for complex analysis

**Cons:**
- ⚠️ Steeper learning curve
- ⚠️ Heavier dependency
- ⚠️ Less popular than JavaParser (2K stars)

**Example code:**
```java
Launcher launcher = new Launcher();
launcher.addInputResource(javaFile.getPath());
CtModel model = launcher.buildModel();

// Extract classes
model.getElements(new TypeFilter<>(CtClass.class)).forEach(ctClass -> {
    String className = ctClass.getSimpleName();

    // Extract methods with calls
    ctClass.getMethods().forEach(method -> {
        method.getElements(new TypeFilter<>(CtInvocation.class)).forEach(invocation -> {
            String calledMethod = invocation.getExecutable().getSimpleName();
            // Store CALLS relationship
        });
    });
});
```

### Decision Matrix

| Criterion | JavaParser | Spoon | Winner |
|-----------|------------|-------|--------|
| Already in project | ✅ Yes | ❌ No | JavaParser |
| Ease of use | ✅ Simple | ⚠️ Complex | JavaParser |
| Documentation | ✅ Excellent | ⚠️ Good | JavaParser |
| Call graph extraction | ⚠️ Manual | ✅ Built-in | Spoon |
| Symbol resolution | ✅ Yes | ✅ Yes | Tie |
| Performance | ✅ Fast | ✅ Fast | Tie |
| Community size | ✅ Large | ⚠️ Smaller | JavaParser |

**RECOMMENDATION: JavaParser**

Reasons:
1. Already in your dependencies (zero setup)
2. Simpler API (faster to implement)
3. Larger community (more examples, better support)
4. You can always add Spoon later if you need advanced features

---

## 6. Failure Mode Analysis

### What Can Go Wrong?

#### Failure Mode 1: Search Returns Wrong Results

**Scenario:** User asks "find all REST controllers"

**Current behavior:**
- Search for "@RestController"
- Returns methods/fields that contain "controller" in code
- Wrong results → LLM gets confused → Bad answer

**Root cause:**
- No entity type filtering (searches all nodes)
- Annotation not stored as searchable property

**Fix:**
```cypher
// WRONG (current)
MATCH (e:Entity)
WHERE toLower(e.sourceCode) CONTAINS 'restcontroller'
RETURN e

// RIGHT (with new schema)
MATCH (c:Class)-[:ANNOTATED_BY]->(a:Annotation)
WHERE a.fqn CONTAINS 'RestController'
RETURN c
```

#### Failure Mode 2: Embeddings Are Garbage

**Scenario:** Generate embedding for ChatController

**Bad approach (what most people do):**
```java
String sourceCode = readFile("ChatController.java");
List<Double> embedding = llm.embed(sourceCode);
```

**Why it fails:**
- Raw code has lots of noise (imports, braces, comments)
- Embedding captures syntax, not semantics
- Can't distinguish ChatController from OrderController

**Good approach:**
```java
String enrichedDescription =
    "Class: ChatController\n" +
    "Purpose: Handles HTTP requests for chat conversations\n" +
    "Package: com.purchasingpower.autoflow.api\n" +
    "Annotations: @RestController, @RequestMapping('/api/v1/chat')\n" +
    "Dependencies: AutoFlowAgent, ConversationService, ChatStreamService\n" +
    "Key Methods:\n" +
    "  - chat(ChatRequest): Process chat message, returns response\n" +
    "  - streamChat(String): Stream chat updates via SSE\n" +
    "  - getHistory(String): Retrieve conversation history\n" +
    "Domain: Chat API, REST endpoint, conversation management";

List<Double> embedding = llm.embed(enrichedDescription);
```

**Why this works:**
- Captures semantic meaning (what the class does)
- Includes domain context
- Searchable by intent ("I need a chat endpoint")

#### Failure Mode 3: Call Graph Incomplete

**Scenario:** User asks "What calls IndexingService.indexRepository()?"

**Problem:** Your current AST parser doesn't track method calls

**Example:**
```java
// In ChatController
AutoFlowAgent.process() // ← Not tracked
```

**Fix:** Extract MethodCallExpr from AST
```java
method.findAll(MethodCallExpr.class).forEach(call -> {
    String calledMethod = call.getNameAsString();
    // Try to resolve which class it belongs to
    Optional<ResolvedMethodDeclaration> resolved = call.resolve();
    if (resolved.isPresent()) {
        String targetClass = resolved.get().getClassName();
        // Store: (currentMethod)-[:CALLS]->(targetMethod)
    }
});
```

#### Failure Mode 4: Context Too Large

**Scenario:** User asks "How does the entire indexing pipeline work?"

**Problem:** Pipeline touches 10+ classes, 50+ methods
- If you include all code → Exceeds context window
- If you truncate → Miss critical parts
- LLM gets incomplete picture → Wrong answer

**Fix: Hierarchical Context**
```
Level 1: Architecture overview (class names + 1-line description)
Level 2: Key method signatures (no implementation)
Level 3: Critical implementation details (only for 2-3 key methods)

LLM first gets Level 1, decides what's important
Then you fetch Level 2 for those classes
Then fetch Level 3 for the critical path
```

#### Failure Mode 5: Hallucination

**Scenario:** User asks "Does this project support OAuth2?"

**Current behavior:**
- Search finds nothing about OAuth2
- LLM still answers "Yes" (hallucination)

**Fix: Teach LLM to say "I don't know"**
```
Prompt instruction:
"If the search returns 0 results, say 'I don't see any evidence of [feature] in this codebase.'
DO NOT make assumptions. DO NOT hallucinate features."
```

---

## 7. Implementation Roadmap

### Phase 0: Foundation (Critical Path)

**Goal:** Get indexing working correctly

**Tasks:**
1. ✅ Choose parser: **JavaParser** (already in deps)
2. ⬜ Test JavaParser on 5 sample files from this repo
3. ⬜ Verify it can extract:
   - Class metadata (name, annotations, FQN)
   - Method signatures
   - Method calls (MethodCallExpr)
   - Imports and dependencies
4. ⬜ Create sample Neo4j queries to verify schema works

**Success criteria:**
- Can parse ChatController.java
- Extract: @RestController annotation, chat() method, calls to agent.process()
- Store in Neo4j with proper relationships
- Query works: "Find all classes with @RestController"

**Time estimate:** 1 day

---

### Phase 1: Indexing Pipeline

**Goal:** Index this entire repository correctly

**Tasks:**

#### 1.1 Core Parsing (Priority 1)
```
⬜ Implement JavaParserService
  - Method: parseJavaFile(File) → ParsedClass
  - Extract: classes, methods, fields, annotations
  - Extract: method calls (CALLS relationships)
  - Extract: imports (DEPENDS_ON relationships)

⬜ Handle edge cases:
  - Inner classes
  - Anonymous classes
  - Lambda expressions (don't index these separately)
  - Generics (store type parameters)
```

#### 1.2 Graph Storage (Priority 1)
```
⬜ Implement Neo4jIndexingService
  - Store Class nodes with all metadata
  - Store Method nodes with all metadata
  - Store Field nodes
  - Store Annotation nodes (de-duplicated)
  - Create relationships: DECLARES, ANNOTATED_BY, CALLS, DEPENDS_ON

⬜ Add indexes:
  - ON (c:Class).fqn
  - ON (m:Method).signature
  - ON (a:Annotation).fqn
```

#### 1.3 Enriched Text Generation (Priority 2)
```
⬜ For each Class, generate enriched description:
  - Class name and purpose (infer from class name + context)
  - Package and domain
  - Annotations
  - Extends/implements
  - Dependencies (other classes it uses)
  - Key methods (names only)

⬜ For each Method, generate enriched description:
  - Method signature and purpose
  - Annotations
  - What it calls
  - Parameters and return type
```

#### 1.4 Embedding Generation (Priority 3)
```
⬜ Integrate with LLM provider (Ollama or Gemini)
  - Use mxbai-embed-large for Ollama (1024 dimensions)
  - Use text-embedding-004 for Gemini (768 dimensions)

⬜ Generate embeddings for enriched text
⬜ Store embeddings in Neo4j

⬜ Create vector indexes:
  CREATE VECTOR INDEX class_embeddings
  FOR (c:Class) ON (c.embedding)
  OPTIONS {indexConfig: {
    `vector.dimensions`: 1024,
    `vector.similarity_function`: 'cosine'
  }}
```

**Success criteria:**
- Index all 150 Java files in this repo
- Neo4j contains ~200 Class nodes, ~1000 Method nodes
- Each node has embedding vector
- Can query: "Find classes similar to 'chat handling'"

**Time estimate:** 3-4 days

---

### Phase 2: Query & Retrieval

**Goal:** Make "explain this project" work perfectly

**Tasks:**

#### 2.1 Discover Project Tool (Already exists, but verify)
```
⬜ Test current DiscoverProjectTool
  - Does it find all @SpringBootApplication classes?
  - Does it find all @RestController classes?
  - Does it find all @Service classes?

⬜ If it works → great!
⬜ If not → fix the Cypher queries
```

#### 2.2 Semantic Search Implementation
```
⬜ Implement vector search:
  CALL db.index.vector.queryNodes(
    'class_embeddings',
    10,
    $queryEmbedding
  ) YIELD node, score

⬜ Implement hybrid search (graph + vector):
  - Step 1: Vector search for candidates
  - Step 2: Graph traverse for related classes
  - Step 3: Rank by combined score

⬜ Test queries:
  - "Find code that handles authentication"
  - "Find REST endpoints"
  - "Find database access code"
```

#### 2.3 Context Assembler
```
⬜ Build context assembly logic:
  - Input: List of classes/methods (search results)
  - Output: Formatted text for LLM prompt

⬜ Implement different templates:
  - PROJECT_OVERVIEW: Class names + annotations only
  - FEATURE_DEEP_DIVE: Full code of 3-5 key classes
  - CODE_SEARCH: Specific method implementations

⬜ Add context budget management:
  - Track token count
  - Prioritize most relevant items
  - Truncate if exceeds limit
```

**Success criteria:**
- User asks: "explain this project to me"
- System calls discover_project
- Returns: "This is a Spring Boot RAG orchestrator with..."
- Lists all controllers, services, repositories correctly
- Response is comprehensive and accurate

**Time estimate:** 2-3 days

---

### Phase 3: Verification & Testing

**Goal:** Validate the system works for all question types

**Test Cases:**

#### Test 1: Project Overview
```
Question: "Explain this project to me"

Expected:
- Identifies it's a Spring Boot application
- Lists main class: AiRagOrchestratorApplication
- Lists controllers: ChatController, KnowledgeController, SearchController
- Lists services: AutoFlowAgent, ChatStreamService, etc.
- Explains it's a RAG orchestrator for code understanding

Pass/Fail: _____
```

#### Test 2: Feature Deep Dive
```
Question: "How does chat streaming work?"

Expected:
- Explains ChatController.streamChat() endpoint
- Explains ChatStreamService sends SSE events
- Shows the SseEmitter pattern
- Explains conversation state management

Pass/Fail: _____
```

#### Test 3: Code Search
```
Question: "Where is the code that indexes repositories?"

Expected:
- Points to IndexingService interface
- Points to JavaParserIndexingService implementation
- Shows the indexRepository() method
- Explains the flow: clone → parse → store

Pass/Fail: _____
```

#### Test 4: Architecture Question
```
Question: "What are all the REST endpoints in this project?"

Expected:
- Lists ChatController endpoints
- Lists KnowledgeController endpoints
- Lists SearchController endpoints
- Shows HTTP methods and paths

Pass/Fail: _____
```

#### Test 5: Dependency Question
```
Question: "What calls the AutoFlowAgent?"

Expected:
- Shows ChatController.chat() calls agent.process()
- Shows ChatStreamService might call it
- Shows the call chain

Pass/Fail: _____
```

#### Test 6: Non-Existent Feature
```
Question: "Does this project support OAuth2?"

Expected:
- "I don't see any evidence of OAuth2 in this codebase"
- Does NOT hallucinate that it exists

Pass/Fail: _____
```

**Success criteria:** All 6 tests pass

**Time estimate:** 1 day testing + fixes

---

## Total Timeline

| Phase | Duration | Risk |
|-------|----------|------|
| Phase 0: Foundation | 1 day | Low |
| Phase 1: Indexing | 3-4 days | Medium |
| Phase 2: Query & Retrieval | 2-3 days | Medium |
| Phase 3: Testing | 1 day | Low |
| **TOTAL** | **7-9 days** | |

---

## Critical Decisions Needed

### Decision 1: Embedding Provider
**Options:**
- Ollama (mxbai-embed-large) - Local, free, 1024 dims
- Gemini (text-embedding-004) - Cloud, paid, 768 dims

**Recommendation:** Start with Ollama (you already have it)

### Decision 2: Vector Dimensions
**Options:**
- 768 (Gemini)
- 1024 (mxbai-embed-large)
- 1536 (OpenAI)

**Recommendation:** Use 1024 (matches mxbai-embed-large)

### Decision 3: Enrichment Strategy
**Options:**
- Minimal: Just class name + annotations
- Moderate: Name + annotations + methods + dependencies
- Full: Everything including javadoc and code snippets

**Recommendation:** Start with Moderate, test, then decide

---

## Open Questions

1. **Should we index test files?**
   - Pro: Helps answer "what tests cover this?"
   - Con: Doubles indexing time
   - **Recommendation:** No for Phase 1, add in Phase 2

2. **Should we parse method bodies for logic understanding?**
   - Pro: Can answer "what does this method do?" without full code
   - Con: Complex, error-prone
   - **Recommendation:** No, just use embeddings on full method

3. **Should we store source code in Neo4j or separate?**
   - Pro (in Neo4j): Single source of truth
   - Con (in Neo4j): Large graph size
   - **Recommendation:** Store in Neo4j for simplicity

---

## Next Steps

1. **Review this document** - Make sure we agree on the approach
2. **Make decisions** on open questions
3. **Start Phase 0** - Test JavaParser on sample files
4. **Build incrementally** - One phase at a time
5. **Test constantly** - Don't wait until the end

---

## Appendix: Example Queries

### Query 1: Find All Controllers
```cypher
MATCH (c:Class)-[:ANNOTATED_BY]->(a:Annotation)
WHERE a.fqn CONTAINS 'RestController'
  AND c.repositoryId = $repoId
RETURN c.name, c.fqn, c.filePath
```

### Query 2: Find What Calls a Method
```cypher
MATCH (caller:Method)-[:CALLS]->(target:Method)
WHERE target.signature CONTAINS 'indexRepository'
  AND caller.repositoryId = $repoId
RETURN caller.name, caller.signature
```

### Query 3: Get Class Dependencies
```cypher
MATCH (c:Class {fqn: $classFqn})-[:DEPENDS_ON]->(dep:Class)
WHERE c.repositoryId = $repoId
RETURN dep.name, dep.fqn
```

### Query 4: Semantic Search
```cypher
CALL db.index.vector.queryNodes(
  'class_embeddings',
  10,
  $queryEmbedding
) YIELD node, score
WHERE node.repositoryId = $repoId
RETURN node.name, node.fqn, score
ORDER BY score DESC
```

### Query 5: Find Methods by Annotation
```cypher
MATCH (m:Method)-[:ANNOTATED_BY]->(a:Annotation)
WHERE a.fqn CONTAINS 'GetMapping'
  AND m.repositoryId = $repoId
RETURN m.name, m.signature
```

---

**END OF DESIGN DOCUMENT**

---

## 8. Critical Decisions Made

| # | Question | Decision | Rationale |
|---|----------|----------|-----------|
| 1 | Vector dimensions | **1024** (mxbai-embed-large) | Ollama local, free, good quality |
| 2 | LLM correction loop | **YES** - 4 layers | Conversation memory + confidence scores + validation + citations |
| 3 | PROJECT_SUMMARY.md | **YES** - Auto-generate | Use if exists, create if missing, reduces tokens |
| 4 | Context exhaustion | **Progressive disclosure** | Metadata first, fetch details on-demand |
| 5 | Parser choice | **JavaParser** | Already in deps, best balance, active community |
| 6 | "I don't know" | **4-layer validation** | Tool confidence + prompts + self-check + sources |
| 7 | Class descriptions | **YES** - All classes | Auto-generate enriched text for embeddings |
| 8 | Document parsing | **YES** - Phase 1 | MD, YAML priority; XML/ImpEx for Hybris |
| 9 | Logging | **Comprehensive** | Emojis for visual grep, structured format |

---

## 9. Document Parsing Strategy

### Priority Order
1. **README.md** - Always check first for project overview
2. **PROJECT_SUMMARY.md** - Auto-generated summary (create if missing)
3. **application.yml** - Configuration understanding
4. **Other .md in docs/** - Architecture, API docs
5. **XML files** - For Hybris (items.xml, *-spring.xml)

### Neo4j Schema Extension
```cypher
(:Document {
  id: UUID,
  repositoryId: String,
  filePath: String,
  type: "README|SUMMARY|CONFIG|API",
  title: String,
  content: String,
  embedding: [Float]
})

(:Section {
  id: UUID,
  documentId: UUID,
  heading: String,
  content: String,
  level: Int,
  embedding: [Float]
})

(:ConfigKey {
  id: UUID,
  repositoryId: String,
  key: String,
  value: String,
  filePath: String
})

(Document)-[:HAS_SECTION]->(Section)
(Document)-[:BELONGS_TO]->(Repository)
(Class)-[:DOCUMENTED_IN]->(Document)
```

---

## 10. Logging Strategy

### Format
```
🔍 - Search/Discovery
📂 - File operations
🔗 - Graph operations
💾 - Database
🔵 - LLM request
🟢 - LLM response
📊 - Metrics
⚠️ - Warnings
❌ - Errors
✅ - Success
```

### Example
```java
log.info("🔍 [INDEXING] Starting: repoId={}, url={}", repoId, url);
log.debug("📂 [PARSER] Parsing: {}", file);
log.info("✅ [INDEXING] Completed: {} types, {} methods in {}ms", types, methods, ms);
```

---

**Next Action:** Review TODO.md, approve design, start Phase 0.

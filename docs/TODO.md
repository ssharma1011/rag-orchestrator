# Implementation TODO - Codebase Understanding

**Goal:** Make LLM explain this codebase with 100% accuracy

**Reference:** See `CODEBASE-UNDERSTANDING-DESIGN.md` for full architecture

---

## Phase 0: Foundation & Testing ⬜

### P0.1: Verify JavaParser Works
- [ ] Test JavaParser on 5 sample files from this repo
  - [ ] ChatController.java
  - [ ] AutoFlowAgent.java
  - [ ] Neo4jGraphStoreImpl.java
  - [ ] IndexingService.java (interface)
  - [ ] JavaParserIndexingService.java
- [ ] Verify extraction works:
  - [ ] Class metadata (name, FQN, annotations)
  - [ ] Method signatures
  - [ ] Method calls (MethodCallExpr)
  - [ ] Imports and dependencies
- [ ] Create sample Cypher queries to test schema
  - [ ] Find all @RestController classes
  - [ ] Find methods that call AutoFlowAgent.process()
  - [ ] Get dependencies of ChatController

**Success Criteria:** Can parse 5 files, extract all metadata, query works

**Time Estimate:** 1 day

---

## Phase 1: Indexing Pipeline ⬜

### P1.1: Core Java Parsing (Priority 1)
- [ ] Implement JavaParserService.parseJavaFile()
  - [ ] Extract class: name, package, FQN, annotations, extends, implements
  - [ ] Extract methods: name, signature, parameters, return type, annotations
  - [ ] Extract fields: name, type, annotations
  - [ ] Extract method calls: MethodCallExpr → CALLS relationships
  - [ ] Extract imports: dependency analysis
- [ ] Handle edge cases:
  - [ ] Inner classes
  - [ ] Anonymous classes
  - [ ] Lambda expressions (ignore for now)
  - [ ] Generics (store type parameters)
- [ ] Add comprehensive logging (see §9 in design)

### P1.2: Graph Storage (Priority 1)
- [ ] Update Neo4jGraphStoreImpl.createIndexes()
  - [ ] Add indexes for Type, Method, Field, Annotation nodes
  - [ ] Keep old Entity indexes for backward compatibility
- [ ] Implement storage methods:
  - [ ] storeClass() → Create (:Type) node
  - [ ] storeMethod() → Create (:Method) node
  - [ ] storeField() → Create (:Field) node
  - [ ] storeAnnotation() → Create (:Annotation) node (de-duplicate)
  - [ ] storeRelationships() → DECLARES, ANNOTATED_BY, CALLS, DEPENDS_ON
- [ ] Test storage:
  - [ ] Store ChatController
  - [ ] Query: Find all @RestController classes
  - [ ] Query: Find methods in ChatController
  - [ ] Query: Find what calls agent.process()

### P1.3: Enriched Text Generation (Priority 2)
- [ ] Implement ClassDescriptionGenerator
  - [ ] Auto-infer purpose from class name + annotations
  - [ ] Extract domain from package name
  - [ ] List annotations
  - [ ] List key public methods (names only)
  - [ ] List dependencies (classes it uses)
- [ ] Implement MethodDescriptionGenerator
  - [ ] Extract method signature + purpose
  - [ ] List annotations
  - [ ] List what it calls
  - [ ] Describe parameters and return type
- [ ] Store descriptions in Neo4j nodes
  - [ ] Add `description` property to Type nodes
  - [ ] Add `description` property to Method nodes

### P1.4: Embedding Generation (Priority 3)
- [ ] Integrate with Ollama mxbai-embed-large
  - [ ] Add embedding endpoint call
  - [ ] Handle batching (100 items at a time)
  - [ ] Add retry logic for failures
- [ ] Generate embeddings for enriched descriptions
  - [ ] Embed class descriptions → store in Type.embedding
  - [ ] Embed method descriptions → store in Method.embedding
- [ ] Create vector indexes in Neo4j:
  ```cypher
  CREATE VECTOR INDEX class_embeddings
  FOR (c:Type) ON (c.embedding)
  OPTIONS {indexConfig: {
    `vector.dimensions`: 1024,
    `vector.similarity_function`: 'cosine'
  }}

  CREATE VECTOR INDEX method_embeddings
  FOR (m:Method) ON (m.embedding)
  OPTIONS {indexConfig: {
    `vector.dimensions`: 1024,
    `vector.similarity_function`: 'cosine'
  }}
  ```
- [ ] Test vector search:
  ```cypher
  CALL db.index.vector.queryNodes('class_embeddings', 5, $embedding)
  YIELD node, score
  ```

### P1.5: Document Indexing (Priority 2)
- [ ] Implement MarkdownParser
  - [ ] Parse README.md, ARCHITECTURE.md, etc.
  - [ ] Extract sections (H1, H2, H3)
  - [ ] Store as (:Document) and (:Section) nodes
  - [ ] Generate embeddings for sections
- [ ] Implement YamlParser
  - [ ] Parse application.yml
  - [ ] Extract key-value pairs
  - [ ] Store as (:ConfigKey) nodes
  - [ ] Link to classes that use them
- [ ] Create PROJECT_SUMMARY.md auto-generator
  - [ ] Check if PROJECT_SUMMARY.md exists
  - [ ] If not, generate from discovery + analysis
  - [ ] Use template format (see design §3)
  - [ ] Store as (:Document) node

**Success Criteria:**
- Index all 150+ Java files in this repo
- Neo4j contains ~200 Type nodes, ~1000 Method nodes
- Each node has embedding vector
- Can query: "Find classes similar to 'chat handling'"
- PROJECT_SUMMARY.md exists (auto-generated or manual)

**Time Estimate:** 3-4 days

---

## Phase 2: Query & Retrieval ⬜

### P2.1: Discover Project Tool (Verify Existing)
- [ ] Test current DiscoverProjectTool with new schema
  - [ ] Does it find @SpringBootApplication?
  - [ ] Does it find all @RestController classes?
  - [ ] Does it find all @Service classes?
  - [ ] Does it find all @Repository classes?
- [ ] Fix if needed (should use ANNOTATED_BY relationships)
- [ ] Add confidence scoring
  - [ ] confidence = 1.0 if results > 5
  - [ ] confidence = 0.5 if results < 3
  - [ ] confidence = 0.0 if results = 0

### P2.2: Semantic Search Implementation
- [ ] Implement VectorSearchService
  - [ ] embedQuery() → Get embedding for user question
  - [ ] searchClasses() → Query class_embeddings index
  - [ ] searchMethods() → Query method_embeddings index
  - [ ] rankResults() → Combine scores, deduplicate
- [ ] Implement HybridSearchService
  - [ ] Step 1: Vector search for candidates
  - [ ] Step 2: Graph traverse for related entities
  - [ ] Step 3: Rank by combined score
- [ ] Test queries:
  - [ ] "Find code that handles authentication"
  - [ ] "Find REST endpoints"
  - [ ] "Find database access code"
  - [ ] "Find chat streaming logic"

### P2.3: Context Assembler
- [ ] Implement ContextAssembler.assembleContext()
  - [ ] PROJECT_OVERVIEW template (metadata only)
  - [ ] FEATURE_DEEP_DIVE template (signatures + key code)
  - [ ] CODE_DETAIL template (full implementations)
- [ ] Implement progressive disclosure
  - [ ] Level 1: Read PROJECT_SUMMARY.md if exists
  - [ ] Level 2: Fetch class names + annotations
  - [ ] Level 3: Fetch specific implementations on-demand
- [ ] Add token budget tracking
  - [ ] Count tokens in context
  - [ ] Prioritize most relevant items
  - [ ] Truncate if exceeds limit (32K for Qwen)

### P2.4: Tool Confidence & Validation (Decision #2, #6)
- [ ] Add confidence scores to all tools
  - [ ] SearchTool: Based on result count + scores
  - [ ] DiscoverProjectTool: Based on completeness
  - [ ] GraphQueryTool: Based on result quality
- [ ] Update prompts with validation rules
  - [ ] If results = 0, say "I couldn't find..."
  - [ ] If confidence < 0.5, say "I found limited info..."
  - [ ] Never hallucinate features
- [ ] Add source citations
  - [ ] Append file paths and line numbers
  - [ ] Format: "Source: ChatController.java:42"

### P2.5: LLM Correction Loop (Decision #2)
- [ ] Implement conversation memory (already exists)
- [ ] Add self-verification prompts
  - [ ] After generating answer, check completeness
  - [ ] Validate against checklist for common queries
- [ ] Support multi-turn refinement
  - [ ] User: "That's wrong, check again"
  - [ ] Agent: Re-searches with new context
  - [ ] Agent: Updates previous answer

**Success Criteria:**
- User asks: "explain this project"
- Returns comprehensive, accurate overview
- Lists all controllers, services, repositories
- Uses PROJECT_SUMMARY.md if exists
- Includes confidence scores
- Cites sources

**Time Estimate:** 2-3 days

---

## Phase 3: Testing & Validation ⬜

### P3.1: Core Test Cases
- [ ] Test 1: Project Overview
  - [ ] Question: "Explain this project to me"
  - [ ] Expected: Identifies Spring Boot, lists all components
  - [ ] Pass/Fail: ____

- [ ] Test 2: Feature Deep Dive
  - [ ] Question: "How does chat streaming work?"
  - [ ] Expected: Explains ChatController → ChatStreamService → SSE
  - [ ] Pass/Fail: ____

- [ ] Test 3: Code Search
  - [ ] Question: "Where is the code that indexes repositories?"
  - [ ] Expected: Points to IndexingService + implementation
  - [ ] Pass/Fail: ____

- [ ] Test 4: Architecture Question
  - [ ] Question: "What are all the REST endpoints?"
  - [ ] Expected: Lists all controllers with endpoints
  - [ ] Pass/Fail: ____

- [ ] Test 5: Dependency Question
  - [ ] Question: "What calls AutoFlowAgent?"
  - [ ] Expected: Shows call chain from ChatController
  - [ ] Pass/Fail: ____

- [ ] Test 6: Non-Existent Feature
  - [ ] Question: "Does this project support OAuth2?"
  - [ ] Expected: "I don't see evidence of OAuth2"
  - [ ] Pass/Fail: ____

### P3.2: Edge Cases
- [ ] Empty search results handling
- [ ] Very long class (exceeds token limit)
- [ ] Circular dependencies
- [ ] Ambiguous queries
- [ ] Multi-turn conversation memory

### P3.3: Performance Testing
- [ ] Indexing time for 150 files: < 5 minutes
- [ ] Search response time: < 2 seconds
- [ ] Embedding generation: < 10 seconds for 200 classes
- [ ] Context assembly: < 1 second

**Success Criteria:** All 6 core tests pass, edge cases handled, performance acceptable

**Time Estimate:** 1 day

---

## Critical Decisions Implemented

| # | Decision | Status | Notes |
|---|----------|--------|-------|
| 1 | Vector dimensions: 1024 | ⬜ | Use mxbai-embed-large |
| 2 | LLM correction loop | ⬜ | Conv memory + confidence + validation |
| 3 | PROJECT_SUMMARY.md | ⬜ | Auto-generate if missing |
| 4 | Progressive disclosure | ⬜ | Metadata → details on-demand |
| 5 | JavaParser (not Spoon) | ⬜ | Already in deps, best option |
| 6 | "I don't know" handling | ⬜ | 4 layers of validation |
| 7 | Class descriptions | ⬜ | Auto-generate for all |
| 8 | Document parsing | ⬜ | MD, YAML in Phase 1 |
| 9 | Comprehensive logging | ⬜ | Emojis + structured |

---

## Next Steps

1. **Review this TODO** - Confirm priorities
2. **Start Phase 0** - Test JavaParser on 5 files
3. **Build Phase 1** - Complete indexing pipeline
4. **Test Phase 2** - Query & retrieval
5. **Validate Phase 3** - All 6 tests pass

**Total Estimated Time:** 7-9 days

---

## Progress Tracking

Update this section as tasks complete:

**Phase 0:** ✅ COMPLETE (JavaParser verified - 4 classes, 68 methods, 291 calls extracted)
**Phase 1:** 🔄 IN PROGRESS
**Phase 2:** ⬜ Not started
**Phase 3:** ⬜ Not started

**Last Updated:** 2026-01-03

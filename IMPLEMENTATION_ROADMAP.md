# AutoFlow Implementation Roadmap

**Date:** 2025-12-26
**Status:** Active Development
**Related:** See [ARCHITECTURAL_ANALYSIS.md](./ARCHITECTURAL_ANALYSIS.md) for architectural problems

---

## 🚨 **Critical Issues Discovered**

### Issue #1: Empty Oracle Tables - Data Not Being Persisted ❌

**Tables with NO data:**
1. `CONVERSATION_MESSAGES` - Exists as entity, but never saved
2. `CONVERSATION_CONTEXT` - Exists as entity, but never saved
3. `AGENT_EXECUTIONS` - Table exists, but NO entity/repository to use it
4. `CODE_NODES.domain`, `CODE_NODES.business_capability`, etc. - Fields exist but not populated
5. Pinecone vector metadata - Not being set properly

**Root Cause:**

```java
// WorkflowExecutionServiceImpl.java - Line 195-224
private void saveWorkflowState(WorkflowState state) {
    // ❌ PROBLEM: Only saves to WORKFLOW_STATES table as JSON blob
    String stateJson = objectMapper.writeValueAsString(state);
    entity.setStateJson(stateJson);  // Everything serialized to JSON
    stateRepository.save(entity);     // Only one table updated!

    // ❌ MISSING: Should ALSO save to:
    // - CONVERSATION_CONTEXT (normalized conversation data)
    // - CONVERSATION_MESSAGES (individual messages)
    // - AGENT_EXECUTIONS (agent tracking)
}
```

**Why This is Bad:**
- 📊 **No queryable conversation history** - Can't search messages, can't build analytics
- 🔍 **Can't filter by conversation state** - All data is in JSON blob
- 📈 **No agent performance metrics** - Can't track which agents are slow/failing
- 🗂️ **Denormalized mess** - Violates database normalization principles
- 💾 **Duplicate storage** - Same data in JSON AND (should be in) normalized tables

---

### Issue #2: Workflow Completion & Conversation History Navigation

**User's Question:**
> "how do you want to handle a workflow state and move it to complete? because the dev can always go back to the conversation history and may be try to correct the LLM based on previous prompts/repo history"

**Current Behavior:**
```
Workflow starts → Agents execute → Status = "COMPLETED" → **DEAD END**

User can't:
- ❌ Resume from middle of workflow
- ❌ Retry specific agents
- ❌ Branch from previous point
- ❌ Correct LLM's understanding mid-flow
```

**Problem:**
- `workflowStatus` is a **terminal state** (COMPLETED = done forever)
- No concept of "conversation continues after workflow completes"
- Can't distinguish between "workflow complete" vs "conversation complete"
- No way to resume or branch from history

**What We Need:**
```
Conversation (long-lived, many workflows)
  └─ Workflow 1: "Add retry logic" → COMPLETED
  └─ Workflow 2: "Actually, change the timeout" → COMPLETED
  └─ Workflow 3: "Now add tests" → RUNNING
```

**Proposed Solution:** See "Workflow Completion Strategy" section below.

---

## 📊 **Current State Analysis**

### What Works ✅
1. ✅ Workflow execution (LangGraph4j)
2. ✅ Agent orchestration (linear paths)
3. ✅ LLM integration (Gemini)
4. ✅ Pinecone vector search
5. ✅ Neo4j code graph storage
6. ✅ State persistence to `WORKFLOW_STATES` (as JSON)
7. ✅ Async execution
8. ✅ Conversation history in memory (WorkflowState)

### What's Broken ❌
1. ❌ Conversation persistence to normalized Oracle tables
2. ❌ Agent execution tracking
3. ❌ Neo4j metadata fields (domain, business_capability)
4. ❌ Pinecone metadata enrichment
5. ❌ Workflow completion/resumption strategy
6. ❌ Mode switching (Q&A → Implementation)
7. ❌ Duplicate code (`extractRepoName()` × 7)
8. ❌ Hardcoded prompts (CRITICAL rules everywhere)

---

## 🎯 **Implementation Plan**

### **Phase 1: Data Persistence Fixes (Week 1)**

#### 1.1 Fix Conversation Persistence

**Files to Modify:**
- `WorkflowExecutionServiceImpl.java`
- `ConversationService.java` (new)

**Changes:**
```java
@Service
public class ConversationService {
    private final ConversationContextRepository conversationRepo;

    public void saveConversation(WorkflowState state) {
        // Create or update ConversationContext
        ConversationContext context = conversationRepo
            .findById(state.getConversationId())
            .orElse(new ConversationContext());

        context.setIssueKey(state.getConversationId());
        context.setRepoName(extractRepoName(state.getRepoUrl()));
        context.setRequirements(state.getRequirement());
        context.setState(determineConversationState(state));

        // Add messages from WorkflowState to ConversationContext
        for (ChatMessage msg : state.getConversationHistory()) {
            ConversationMessage dbMsg = new ConversationMessage(
                msg.getRole(),
                msg.getContent()
            );
            context.addMessage(dbMsg);
        }

        conversationRepo.save(context);
    }
}
```

**Impact:** ✅ Conversations persisted to Oracle, queryable, analytics-ready

---

#### 1.2 Add Agent Execution Tracking

**New Files:**
- `src/main/java/com/purchasingpower/autoflow/model/metrics/AgentExecution.java` (entity)
- `src/main/java/com/purchasingpower/autoflow/repository/AgentExecutionRepository.java`
- `src/main/java/com/purchasingpower/autoflow/service/AgentMetricsService.java`

**Entity:**
```java
@Entity
@Table(name = "AGENT_EXECUTIONS")
public class AgentExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String executionId;         // UUID for this execution
    private String conversationId;      // Which conversation
    private String agentName;           // Which agent ran
    private String decision;            // PROCEED / ASK_DEV / ERROR
    private Double confidence;          // 0.0 - 1.0
    private Integer tokenUsageInput;    // LLM tokens
    private Integer tokenUsageOutput;   // LLM tokens
    private Long latencyMs;             // How long agent took
    private String status;              // SUCCESS / FAILED
    private String errorMessage;        // If failed

    @Lob
    private String inputState;          // JSON snapshot of input

    @Lob
    private String outputState;         // JSON snapshot of output

    private LocalDateTime createdAt;
}
```

**Integration Point:**
```java
// Base class for all agents
public abstract class BaseAgent {
    protected AgentMetricsService metricsService;

    public Map<String, Object> execute(WorkflowState state) {
        String executionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();

        try {
            Map<String, Object> result = doExecute(state);

            // Track successful execution
            metricsService.recordExecution(AgentExecution.builder()
                .executionId(executionId)
                .conversationId(state.getConversationId())
                .agentName(this.getClass().getSimpleName())
                .latencyMs(System.currentTimeMillis() - startTime)
                .status("SUCCESS")
                .inputState(serialize(state))
                .outputState(serialize(result))
                .build());

            return result;

        } catch (Exception e) {
            // Track failed execution
            metricsService.recordExecution(AgentExecution.builder()
                .executionId(executionId)
                .conversationId(state.getConversationId())
                .agentName(this.getClass().getSimpleName())
                .latencyMs(System.currentTimeMillis() - startTime)
                .status("FAILED")
                .errorMessage(e.getMessage())
                .build());
            throw e;
        }
    }

    protected abstract Map<String, Object> doExecute(WorkflowState state);
}
```

**Impact:** ✅ Agent performance tracking, debugging, observability

---

#### 1.3 Fix Neo4j Metadata Population

**Problem:**
```java
// CodeIndexerAgent.java - Line 350-380
// Creates ClassNode but doesn't set domain/business_capability fields
ClassNode classNode = new ClassNode();
classNode.setNodeId(nodeId);
classNode.setSimpleName(metadata.getSimpleName());
// ❌ MISSING: classNode.setDomain(...)
// ❌ MISSING: classNode.setBusinessCapability(...)
// ❌ MISSING: classNode.setFeatures(...)
```

**Fix:**
```java
// After creating ClassNode, enrich with LLM
private void enrichClassMetadata(ClassNode node, ClassMetadata metadata, String sourceCode) {
    String prompt = promptLibrary.render("class-metadata-enrichment", Map.of(
        "className", metadata.getSimpleName(),
        "classCode", sourceCode
    ));

    String llmResponse = geminiClient.generateText(prompt);
    ClassMetadataEnrichment enrichment = parseEnrichment(llmResponse);

    node.setDomain(enrichment.getDomain());
    node.setBusinessCapability(enrichment.getCapability());
    node.setFeatures(String.join(",", enrichment.getFeatures()));
    node.setConcepts(String.join(",", enrichment.getConcepts()));
}
```

**New Prompt:** `prompts/class-metadata-enrichment.yaml`
```yaml
systemPrompt: |
  Analyze this Java class and extract business metadata.

  Class: {{className}}

  Code:
  {{classCode}}

  Return JSON:
  {
    "domain": "payment|user|order|...",
    "capability": "payment-processing|authentication|...",
    "features": ["retry", "circuit-breaker", ...],
    "concepts": ["transaction", "refund", ...]
  }
```

**Impact:** ✅ Semantic code search, domain-based filtering, better scope discovery

---

#### 1.4 Fix Pinecone Metadata

**Problem:**
```java
// PineconeService.java - Metadata is minimal
Map<String, Object> metadata = new HashMap<>();
metadata.put("className", chunk.getClassName());
metadata.put("filePath", chunk.getFilePath());
// ❌ MISSING: domain, dependencies, complexity, etc.
```

**Fix:**
```java
Map<String, Object> metadata = new HashMap<>();
metadata.put("className", chunk.getClassName());
metadata.put("filePath", chunk.getFilePath());
metadata.put("methodName", chunk.getMethodName());
metadata.put("chunkType", chunk.getType().name());
metadata.put("repoName", repoName);
metadata.put("commitHash", currentCommit);

// Add enriched metadata from Neo4j
ClassNode classNode = neo4jService.findClassByName(chunk.getClassName());
if (classNode != null) {
    metadata.put("domain", classNode.getDomain());
    metadata.put("businessCapability", classNode.getBusinessCapability());
    metadata.put("features", classNode.getFeatures());
}

// Add code metrics
metadata.put("linesOfCode", chunk.getContent().split("\n").length);
metadata.put("complexity", calculateComplexity(chunk.getContent()));
metadata.put("hasTests", hasTestCoverage(chunk.getClassName()));
```

**Impact:** ✅ Better vector search filtering, domain-aware retrieval

---

### **Phase 2: Workflow Completion Strategy**

#### 2.1 Conversation vs Workflow Separation

**Current Problem:**
- One WorkflowState = One Conversation
- When workflow completes, conversation is "done"
- No way to continue conversation after completion

**Solution: Separate Concepts**

```java
// NEW MODEL
@Entity
@Table(name = "CONVERSATIONS")
public class Conversation {
    @Id
    private String conversationId;      // Long-lived conversation

    private String userId;
    private String repoUrl;

    @Enumerated(EnumType.STRING)
    private ConversationMode mode;      // EXPLORE / DEBUG / IMPLEMENT / REVIEW

    @OneToMany(mappedBy = "conversation")
    private List<Workflow> workflows;   // Multiple workflows per conversation

    @OneToMany
    private List<ConversationMessage> messages;  // All messages across all workflows

    private boolean isActive;           // Conversation can be closed
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;
}

@Entity
@Table(name = "WORKFLOWS")
public class Workflow {
    @Id
    private String workflowId;          // One specific task execution

    @ManyToOne
    private Conversation conversation;  // Belongs to conversation

    private String goal;                // "Add retry logic", "Fix NPE"
    private String status;              // RUNNING / COMPLETED / FAILED
    private String currentAgent;

    @Lob
    private String stateJson;           // WorkflowState snapshot

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}
```

**Flow:**
```
1. User: "Add retry logic to Pinecone"
   → Creates Conversation (conv_123)
   → Creates Workflow (wf_001) with goal="Add retry logic"
   → Workflow executes → Completes
   → Conversation stays ACTIVE

2. User: "Actually, change timeout to 10s"
   → Same Conversation (conv_123)
   → Creates NEW Workflow (wf_002) with goal="Change timeout"
   → Workflow executes → Completes
   → Conversation stays ACTIVE

3. User: "Close this conversation"
   → Conversation.isActive = false
```

**Benefits:**
- ✅ Conversation history preserved across multiple workflows
- ✅ Can resume/branch from any point
- ✅ Can view "what we've accomplished in this conversation"
- ✅ Can track multiple goals in same conversation

---

#### 2.2 Workflow Resumption Strategy

**Scenarios:**

**Scenario A: Retry Failed Workflow**
```java
// User wants to retry after fixing issue
POST /api/v1/workflows/{workflowId}/retry

// Backend:
1. Load Workflow (wf_001) - status = FAILED
2. Load last WorkflowState from stateJson
3. Create NEW Workflow (wf_001_retry1) - status = RUNNING
4. Execute from last successful agent
5. Link to same Conversation
```

**Scenario B: Branch from History**
```java
// User wants to try different approach from earlier point
POST /api/v1/conversations/{conversationId}/branch
Body: {
  "fromWorkflowId": "wf_001",
  "fromAgentName": "scope_discovery",
  "newGoal": "Try different approach - use facade pattern"
}

// Backend:
1. Load Workflow (wf_001)
2. Extract WorkflowState at "scope_discovery" stage
3. Create NEW Workflow (wf_003) with that state as starting point
4. User can provide different input
5. Workflow continues from there
```

**Scenario C: Correct LLM Understanding**
```java
// User sees LLM misunderstood requirement
POST /api/v1/workflows/{workflowId}/correct
Body: {
  "agentName": "requirement_analyzer",
  "correction": "No, I want to add retry to GEMINI calls, not Pinecone"
}

// Backend:
1. Load current Workflow
2. Rollback to requirement_analyzer output
3. Update RequirementAnalysis with correction
4. Create NEW Workflow with corrected understanding
5. Continue from next agent (skip requirement_analyzer)
```

**Implementation:**
```java
@RestController
@RequestMapping("/api/v1/conversations")
public class ConversationController {

    @PostMapping("/{conversationId}/message")
    public ResponseEntity<WorkflowResponse> sendMessage(
        @PathVariable String conversationId,
        @RequestBody UserMessage message) {

        // Load conversation
        Conversation conv = conversationService.findById(conversationId);

        // Determine if this is continuation or new workflow
        Workflow lastWorkflow = conv.getWorkflows().getLast();

        if (lastWorkflow.getStatus().equals("RUNNING")) {
            // Resume existing workflow
            return workflowService.resumeWorkflow(lastWorkflow.getWorkflowId(), message);
        } else {
            // Create new workflow in same conversation
            Workflow newWorkflow = Workflow.builder()
                .workflowId(UUID.randomUUID().toString())
                .conversation(conv)
                .goal(message.getContent())
                .status("RUNNING")
                .build();

            return workflowService.startWorkflow(newWorkflow);
        }
    }

    @GetMapping("/{conversationId}/history")
    public ConversationHistory getHistory(@PathVariable String conversationId) {
        Conversation conv = conversationService.findById(conversationId);

        return ConversationHistory.builder()
            .conversationId(conversationId)
            .mode(conv.getMode())
            .workflows(conv.getWorkflows().stream()
                .map(w -> WorkflowSummary.builder()
                    .workflowId(w.getWorkflowId())
                    .goal(w.getGoal())
                    .status(w.getStatus())
                    .startedAt(w.getStartedAt())
                    .completedAt(w.getCompletedAt())
                    .build())
                .collect(Collectors.toList()))
            .messages(conv.getMessages())
            .build();
    }
}
```

---

### **Phase 3: Quick Wins (Week 2)**

#### 3.1 Remove Duplicate extractRepoName()

**Found in 7+ files:**
- CodeIndexerAgent.java (line 446)
- ScopeDiscoveryAgent.java (line 559)
- ContextBuilderAgent.java (line 152)
- DocumentationAgent.java (line 134)
- PRCreatorAgent.java (line 65)
- +2 more

**Fix:**
```bash
# Delete all duplicate implementations
# Use GitOperationsService.extractRepoName() everywhere

# Example:
- private String extractRepoName(String repoUrl) { ... }
+ String repoName = gitOperationsService.extractRepoName(state.getRepoUrl());
```

**Files to modify:** 7 agent files
**Lines saved:** ~70 lines of duplicate code
**Risk:** Low (just refactoring)

---

#### 3.2 Delete Contradictory Prompt Patterns

**File:** `src/main/resources/prompts/requirement-analyzer.yaml`

**Delete these sections:**
```yaml
# Lines 52-55: Greeting patterns
CRITICAL: If the requirement is just a greeting (hi, hello, hey) with NO actual task:
  - Set confidence to 0.0
  - Set taskType to "unknown"
  - Ask: "What would you like me to help you build or fix?"

# Lines 57-61: Conversational patterns (CONTRADICTS CONVERSATION HISTORY!)
CRITICAL: If the requirement is conversational/contextual with NO actual task:
  - Examples: "our conversation was cut off", "what were we discussing?", "I see", "ok", "thanks", "continue"
  - Set confidence to 0.0
  - Set taskType to "unknown"
  - Respond: "I don't have context from previous conversations."  # ❌ LIE! We DO have context!
```

**Replace with:**
```yaml
# Trust the LLM - no hardcoded patterns
# If user message is conversational, LLM will naturally respond using conversation history

conversationHistory: |
  {{conversationHistory}}

instructions: |
  Analyze the user's message in context of the conversation history above.

  If this is just a greeting or continuation, respond naturally using the conversation context.
  If this is a new task request, analyze it and determine the goal.
```

**Impact:** ✅ No more contradictions, LLM uses conversation history naturally

---

### **Phase 4: Architectural Improvements (Week 3-4)**

See [ARCHITECTURAL_ANALYSIS.md](./ARCHITECTURAL_ANALYSIS.md) for full details:

1. **MetaAgent Orchestration** - Dynamic routing instead of hardcoded graph
2. **ConversationState Model** - Track mode, goals, confidence
3. **Mode Switching** - EXPLORE → DEBUG → IMPLEMENT seamlessly
4. **Simplified Prompts** - Remove all CRITICAL rules, trust LLM
5. **Dynamic Progress** - Based on goals, not hardcoded percentages

---

## 📋 **Implementation Checklist**

### **Critical (This Week)**
- [ ] Fix ConversationContext persistence (ConversationService)
- [ ] Fix ConversationMessage persistence
- [ ] Create AgentExecution entity + repository
- [ ] Add agent execution tracking to all agents
- [ ] Implement Conversation/Workflow separation
- [ ] Add conversation history navigation API
- [ ] Fix Neo4j metadata enrichment (domain, capability)
- [ ] Fix Pinecone metadata enhancement

### **Quick Wins (This Week)**
- [ ] Remove duplicate extractRepoName() (7+ files)
- [ ] Delete contradictory prompt patterns
- [ ] Simplify requirement-analyzer.yaml prompt
- [ ] Add workflow retry endpoint
- [ ] Add workflow branch endpoint

### **Architectural (Next 2 Weeks)**
- [ ] Design MetaAgent orchestrator
- [ ] Create ConversationState model
- [ ] Implement mode switching
- [ ] Refactor AutoFlowWorkflow for dynamic routing
- [ ] Remove hardcoded task types
- [ ] Add confidence-based decisions

---

## 🎯 **Success Metrics**

**Week 1:**
- ✅ All Oracle tables have data
- ✅ Can query conversation history via API
- ✅ Agent execution metrics visible
- ✅ Neo4j domain metadata populated
- ✅ Duplicate code reduced by 70+ lines

**Week 2:**
- ✅ Users can resume workflows
- ✅ Users can branch from conversation history
- ✅ Users can correct LLM mid-flow
- ✅ Conversations survive workflow completion

**Week 3-4:**
- ✅ Mode switching works (Q&A → Debug → Implement)
- ✅ No more hardcoded CRITICAL rules
- ✅ Dynamic agent orchestration
- ✅ Confidence-based routing

---

## 🔧 **Database Migration Plan**

### **New Tables Needed:**

```sql
-- Conversations table
CREATE TABLE CONVERSATIONS (
    conversation_id VARCHAR2(100) PRIMARY KEY,
    user_id VARCHAR2(100) NOT NULL,
    repo_url VARCHAR2(500),
    mode VARCHAR2(20),  -- EXPLORE / DEBUG / IMPLEMENT / REVIEW
    is_active NUMBER(1) DEFAULT 1,
    created_at TIMESTAMP NOT NULL,
    last_activity TIMESTAMP NOT NULL
);

-- Workflows table (multiple per conversation)
CREATE TABLE WORKFLOWS (
    workflow_id VARCHAR2(100) PRIMARY KEY,
    conversation_id VARCHAR2(100) REFERENCES CONVERSATIONS(conversation_id),
    goal CLOB,
    status VARCHAR2(20) NOT NULL,  -- RUNNING / COMPLETED / FAILED / CANCELLED
    current_agent VARCHAR2(50),
    state_json CLOB,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP
);

-- Workflow branching (track lineage)
CREATE TABLE WORKFLOW_BRANCHES (
    branch_id VARCHAR2(100) PRIMARY KEY,
    parent_workflow_id VARCHAR2(100) REFERENCES WORKFLOWS(workflow_id),
    child_workflow_id VARCHAR2(100) REFERENCES WORKFLOWS(workflow_id),
    branch_point_agent VARCHAR2(50),  -- Which agent we branched from
    reason CLOB,  -- Why we branched
    created_at TIMESTAMP NOT NULL
);
```

### **Migration Script:**

File: `src/main/resources/db/conversation-persistence-migration.sql`

```sql
-- Add conversation_id column to existing tables
ALTER TABLE CONVERSATION_CONTEXT ADD conversation_id VARCHAR2(100);
ALTER TABLE CONVERSATION_MESSAGES ADD conversation_id VARCHAR2(100);

-- Migrate existing data from WORKFLOW_STATES
INSERT INTO CONVERSATIONS (conversation_id, user_id, repo_url, is_active, created_at, last_activity)
SELECT
    conversation_id,
    user_id,
    JSON_VALUE(state_json, '$.repoUrl'),
    1,
    created_at,
    updated_at
FROM WORKFLOW_STATES;

-- Create initial workflows from existing workflow states
INSERT INTO WORKFLOWS (workflow_id, conversation_id, goal, status, state_json, started_at, completed_at)
SELECT
    conversation_id || '_initial',
    conversation_id,
    JSON_VALUE(state_json, '$.requirement'),
    status,
    state_json,
    created_at,
    CASE WHEN status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN updated_at ELSE NULL END
FROM WORKFLOW_STATES;

COMMIT;
```

---

## 📝 **API Changes**

### **New Endpoints:**

```
# Conversation Management
POST   /api/v1/conversations                    # Create conversation
GET    /api/v1/conversations/{id}               # Get conversation details
GET    /api/v1/conversations/{id}/history       # Get full history
POST   /api/v1/conversations/{id}/message       # Send message (auto creates/resumes workflow)
DELETE /api/v1/conversations/{id}               # Close conversation

# Workflow Management
GET    /api/v1/workflows/{id}                   # Get workflow details
POST   /api/v1/workflows/{id}/retry             # Retry failed workflow
POST   /api/v1/workflows/{id}/cancel            # Cancel running workflow
POST   /api/v1/workflows/{id}/branch            # Branch from specific point

# Agent Metrics
GET    /api/v1/metrics/agents                   # Get agent performance stats
GET    /api/v1/metrics/agents/{name}            # Get specific agent metrics
GET    /api/v1/metrics/executions/{id}          # Get execution details
```

### **Updated Endpoints:**

```
# Old: POST /api/v1/workflows/start
# New: POST /api/v1/conversations/{id}/message
# Change: Now conversation-centric, not workflow-centric
```

---

## 💡 **Key Design Decisions**

### **1. Why Separate Conversation and Workflow?**
- **Conversation** = Long-lived user session (minutes to hours)
- **Workflow** = One specific task execution (30s to 5min)
- Users need to continue conversation after workflow completes
- Enables branching, retrying, and correction

### **2. Why Normalized Tables + JSON?**
- **JSON in WORKFLOW_STATES** = Fast recovery, complete snapshot
- **Normalized tables** = Queryable, analytics, reporting
- Both serve different purposes, both needed

### **3. Why Track Agent Executions?**
- **Observability** - Which agents are slow/failing?
- **Debugging** - What happened in this workflow?
- **Analytics** - Cost tracking (token usage), performance metrics
- **Optimization** - Identify bottlenecks

### **4. Why Conversation Modes?**
- Different modes need different agent sequences
- EXPLORE mode ≠ IMPLEMENT mode
- Enables mode switching mid-conversation
- Foundation for MetaAgent orchestration

---

## 🚀 **Getting Started**

### **Step 1: Run Database Migrations**
```bash
# Connect to Oracle
sqlplus autoflow/password@localhost:1521/XEPDB1

# Run migrations
@src/main/resources/db/conversation-persistence-migration.sql
@src/main/resources/db/agent-execution-migration.sql
```

### **Step 2: Create New Entities**
```bash
# 1. Create AgentExecution entity
# 2. Create Conversation entity
# 3. Create Workflow entity
# 4. Create repositories
```

### **Step 3: Update Services**
```bash
# 1. Create ConversationService
# 2. Update WorkflowExecutionServiceImpl
# 3. Create AgentMetricsService
# 4. Update all agents to extend BaseAgent
```

### **Step 4: Add API Endpoints**
```bash
# 1. Create ConversationController
# 2. Update WorkflowController
# 3. Create MetricsController
```

### **Step 5: Quick Wins**
```bash
# 1. Remove duplicate extractRepoName()
# 2. Delete contradictory prompts
# 3. Simplify requirement-analyzer.yaml
```

---

## 📊 **Testing Strategy**

### **Unit Tests:**
```java
// ConversationServiceTest
- testCreateConversation()
- testAddMessageToConversation()
- testMultipleWorkflowsInConversation()

// WorkflowServiceTest
- testRetryFailedWorkflow()
- testBranchFromWorkflow()
- testWorkflowCompletion()

// AgentMetricsServiceTest
- testRecordExecution()
- testGetAgentStats()
```

### **Integration Tests:**
```java
// End-to-end conversation flow
@Test
public void testConversationLifecycle() {
    // 1. Create conversation
    // 2. Send message "Add retry logic"
    // 3. Verify workflow created + executed
    // 4. Send message "Change timeout"
    // 5. Verify NEW workflow created
    // 6. Verify both workflows linked to same conversation
    // 7. Get conversation history
    // 8. Verify all messages present
}
```

---

## 🎬 **Next Actions**

1. ✅ Review this roadmap
2. ⏳ Run database migrations
3. ⏳ Create AgentExecution entity + repository
4. ⏳ Create ConversationService
5. ⏳ Update WorkflowExecutionServiceImpl
6. ⏳ Test conversation persistence
7. ⏳ Quick wins (duplicate code removal)
8. ⏳ API endpoint updates

**Let's start with Phase 1.1 (Conversation Persistence) and Phase 3 (Quick Wins) in parallel!**

---

**Questions? Issues? Concerns?** Add comments inline and we'll discuss!

# AutoFlow Architecture: Issues & Proposed Solutions

**Date:** 2025-12-26
**Status:** Pre-Implementation Discussion - NO CODE CHANGES YET

---

## 🎯 Vision: What We Want to Build

A **flexible, conversational AI system** that:
1. **Converses naturally** with developers to answer questions about projects
2. **Understands intent dynamically** without rigid task classification
3. **Switches modes seamlessly** (Q&A → Debug → Fix → Build)
4. **Leverages our tech stack** (Neo4j, Pinecone, Oracle, LLM) instead of if/else logic
5. **Works like ChatGPT** but with deep codebase understanding

---

## 🚨 Critical Problems in Current Architecture

### Problem 1: **Hardcoded Linear Workflow** (AutoFlowWorkflow.java)

**Location:** `/src/main/java/com/purchasingpower/autoflow/workflow/AutoFlowWorkflow.java`

**What's Wrong:**
```java
// Lines 72-102: HARDCODED LINEAR PATH
graph.addEdge(START, "requirement_analyzer");

graph.addConditionalEdges("requirement_analyzer",
    edge_async(s -> {
        // HARDCODED: Only checks "DOCUMENTATION" taskType
        if (analysis != null && "DOCUMENTATION".equalsIgnoreCase(analysis.getTaskType())) {
            return "code_indexer";
        }
        if (shouldPause(s)) {
            return "ask_developer";
        }
        // HARDCODED: Always goes to log_analyzer OR code_indexer
        return s.hasLogs() ? "log_analyzer" : "code_indexer";
    }),
    Map.of(
        "ask_developer", "ask_developer",
        "log_analyzer", "log_analyzer",
        "code_indexer", "code_indexer"
    )
);
```

**Why It's Wrong:**
- **Fixed route**: START → RequirementAnalyzer → CodeIndexer → ScopeDiscovery → ... → PR Creator
- **One taskType special case**: Only "DOCUMENTATION" gets different routing
- **Cannot handle mixed workflows**: User can't ask a question mid-implementation
- **Cannot skip agents**: Even for simple tasks, all agents run
- **State machine, not orchestration**: Routes are predetermined, not dynamic

**Example Failures:**
- User says "hi" → Full workflow runs (CodeIndexer, ScopeDiscovery, etc.)
- User says "our conversation was cut off" → System tries to build features
- User asks "what does this code do?" mid-fix → Can't switch to Q&A mode

---

### Problem 2: **Rigid Task Classification** (RequirementAnalyzerAgent + Prompts)

**Locations:**
- `/src/main/java/com/purchasingpower/autoflow/workflow/agents/RequirementAnalyzerAgent.java`
- `/src/main/resources/prompts/requirement-analyzer.yaml`
- `/src/main/java/com/purchasingpower/autoflow/workflow/state/RequirementAnalysis.java`

**What's Wrong:**

```yaml
# requirement-analyzer.yaml lines 64-71
Determine:
  1. Task type:
     - "documentation" = User wants explanation/understanding/learning (read-only)
     - "bug_fix" = Fix a specific bug/error/issue
     - "feature" = Add new functionality/capability
     - "refactor" = Improve code structure/organization
     - "test" = Add/modify tests
```

**Why It's Wrong:**
- **Forced classification**: Every input MUST be one of 5 types
- **Binary buckets**: Real conversations are fluid, not categorical
- **No mixed intent**: "Explain this bug then fix it" doesn't fit
- **No exploratory mode**: "Help me understand why this fails" → forced into "bug_fix"
- **LLM must choose**: AI has to guess which bucket, causing misclassifications

**Example Failures:**
- "I'm seeing 500 errors" → Classified as "bug_fix" → Tries to generate code immediately
- "What agents do we have?" → Classified as "documentation" OR "feature" (ambiguous)
- "seems like our conversation was cut off" → Classified as "feature" (0.05 confidence) → Still proceeds!

---

### Problem 3: **Prompt Engineering Anti-Pattern** (requirement-analyzer.yaml)

**Location:** `/src/main/resources/prompts/requirement-analyzer.yaml`

**What's Wrong:**
```yaml
# Lines 15-26: EVER-GROWING RULES
CRITICAL RULES FOR QUESTIONS:
1. If you have questions, ask ALL of them in a SINGLE response
2. NEVER ask follow-up questions after receiving answers - move forward
3. ONLY ask questions that would prevent generating correct code
4. DO NOT ask about: [12+ hardcoded exclusions]
...

# Lines 52-61: BAND-AID FOR GREETINGS
CRITICAL: If the requirement is just a greeting (hi, hello, hey) with NO actual task:
  - Set confidence to 0.0
  - Set taskType to "unknown"
  - Ask: "What would you like me to help you build or fix?"

# NEW BAND-AID (just added)
CRITICAL: If the requirement is conversational/contextual with NO actual task:
  - Examples: "our conversation was cut off", "what were we discussing?", "I see", "ok", "thanks", "continue"
  - Set confidence to 0.0
  - Set taskType to "unknown"
  - Respond: "I don't have context from previous conversations. Please describe what you'd like me to help you with."
```

**Why It's Wrong:**
- **Each edge case = new CRITICAL rule**: Unmaintainable
- **Contradicts conversation history**: We STORE history but say "I don't have context"?!
- **Doesn't trust LLM**: Over-constraining with explicit patterns
- **Pattern matching instead of understanding**: "hi", "hello", "hey" hardcoded
- **Will grow infinitely**: Every new issue = another CRITICAL block

**User's Feedback:**
> "can't simply keep changing prompts also as in when we're seeing issues... why the prompts can be more generic or why can't we use llm to understand what the dev is trying to say?"

---

### Problem 4: **Code Duplication** (extractRepoName everywhere)

**Found in 7+ files:**
- CodeIndexerAgent.java (line 446)
- ScopeDiscoveryAgent.java (line 559)
- ContextBuilderAgent.java (line 152)
- DocumentationAgent.java (line 134)
- PRCreatorAgent.java (line 65)
- GitOperationsServiceImpl.java (line 225) ← Only this one is a service!

**What's Wrong:**
```java
// DUPLICATED 6+ times across agents
private String extractRepoName(String repoUrl) {
    if (repoUrl == null || repoUrl.trim().isEmpty()) {
        throw new IllegalArgumentException("Repository URL is required...");
    }
    String[] parts = repoUrl.replace(".git", "").split("/");
    return parts[parts.length - 1];
}
```

**Why It's Wrong:**
- **DRY violation**: Same logic in 7 places
- **GitOperationsService.extractRepoName() exists** but agents don't use it
- **Maintenance nightmare**: Bug fix needs 7+ changes
- **Inconsistent implementations**: Some throw exceptions, some don't

**Should Be:** All agents use `GitOperationsService.extractRepoName()`

---

### Problem 5: **Hardcoded Progress Calculation** (WorkflowResponse.java)

**Location:** `/src/main/java/com/purchasingpower/autoflow/model/dto/WorkflowResponse.java`

**What's Wrong:**
```java
// Lines 105-123: HARDCODED AGENT NAMES & PERCENTAGES
private static int calculateProgress(WorkflowState state) {
    String agent = state.getCurrentAgent();
    if (agent == null) return 0;

    return switch (agent) {
        case "requirement_analyzer" -> 5;
        case "log_analyzer" -> 10;
        case "code_indexer" -> 20;
        case "scope_discovery" -> 35;
        case "context_builder" -> 50;
        case "code_generator" -> 65;
        case "build_validator" -> 75;
        case "test_runner" -> 85;
        case "pr_reviewer" -> 90;
        case "readme_generator" -> 95;
        case "pr_creator" -> 100;
        default -> 0;
    };
}
```

**Why It's Wrong:**
- **Assumes linear workflow**: What if user only wants documentation? (Should be 100% after DocumentationAgent)
- **Hardcoded percentages**: No flexibility for dynamic workflows
- **Doesn't reflect actual time**: CodeGenerator might take 80% of total time
- **Breaks with new agents**: Add new agent = update switch statement

**Should Be:** Progress based on conversation state/goals, not hardcoded agent sequence

---

### Problem 6: **Not Leveraging Tech Stack**

**We Have:**
- ✅ **Neo4j**: Stores class relationships, method calls, inheritance graphs
- ✅ **Pinecone**: Semantic search over code embeddings
- ✅ **Oracle**: Conversation history with full context
- ✅ **LLM (Gemini)**: Can understand complex, nuanced intent

**We Use:**
- ❌ if/else routing based on strings
- ❌ Hardcoded task types
- ❌ Pattern matching ("hi", "hello", "hey")
- ❌ Binary decisions (proceed vs ask_dev)

**Example of Underutilization:**
```java
// AutoFlowWorkflow.java line 88
if (analysis != null && "DOCUMENTATION".equalsIgnoreCase(analysis.getTaskType())) {
    log.info("📚 Routing to documentation agent");
    return "code_indexer";
}
```

**Should Be:**
- Ask LLM: "Given conversation history, what should happen next?"
- Use Neo4j: "What classes relate to user's question?"
- Use confidence scores: "Is this 90% Q&A or 60% Q&A / 40% Fix?"

---

### Problem 7: **No Conversation State Model**

**Current State Tracking:**
```java
// WorkflowState.java - Only tracks:
private String requirement;              // Latest message only
private String workflowStatus;           // RUNNING / PAUSED / COMPLETED
private String currentAgent;             // Which agent is active
private RequirementAnalysis analysis;    // Rigid classification
```

**Missing:**
- **Conversation mode**: Are we in Q&A? Debugging? Implementation?
- **User goals**: What does user ultimately want? (understand vs fix vs build)
- **Confidence tracking**: How certain are we about current direction?
- **Intent history**: User started with question, now wants fix → mode shift
- **Context window**: What parts of codebase are we focused on?

**Example Failure:**
```
User: "Explain how authentication works"
System: [Routes to DocumentationAgent, indexes code, generates explanation]
User: "Ok, now add OAuth support"
System: ❌ STUCK - Can't switch from DOCUMENTATION mode to FEATURE mode
```

---

## 📋 Complete List of Hardcodings to Remove

### 1. **Task Type Enum** (5 values)
- ❌ "documentation", "bug_fix", "feature", "refactor", "test"
- ✅ Replace with: Intent understanding + confidence scores

### 2. **Greeting Patterns** (requirement-analyzer.yaml)
- ❌ "hi", "hello", "hey"
- ✅ Replace with: LLM determines if input is conversational

### 3. **Conversational Statement Patterns** (requirement-analyzer.yaml)
- ❌ "our conversation was cut off", "what were we discussing?", "I see", "ok", "thanks", "continue"
- ✅ Replace with: LLM uses conversation history to respond appropriately

### 4. **Agent Routing Logic** (AutoFlowWorkflow.java)
- ❌ if taskType == "DOCUMENTATION" → route to DocumentationAgent
- ❌ if hasLogs() → route to LogAnalyzer
- ❌ if scope exists → route to ScopeApproval
- ✅ Replace with: Dynamic orchestration based on conversation state

### 5. **Progress Percentages** (WorkflowResponse.java)
- ❌ Switch statement with 11 hardcoded agent names
- ✅ Replace with: Dynamic progress based on conversation goals

### 6. **extractRepoName() Duplication**
- ❌ 7+ implementations across agents
- ✅ Replace with: Single service method used everywhere

### 7. **Default Retry Parameters** (Prompt)
- ❌ "use: network errors, 429, 5xx"
- ❌ "use: 1s, 2s, 4s exponential"
- ✅ Keep as defaults but allow LLM to reason about them

### 8. **Workflow Graph Structure** (AutoFlowWorkflow.java)
- ❌ Fixed path: RequirementAnalyzer → CodeIndexer → ScopeDiscovery → ... → PRCreator
- ✅ Replace with: Dynamic graph built per conversation

---

## 🏗️ Proposed Flexible Architecture

### Core Principle
**Trust the LLM with full context, use our tech stack for knowledge, enable dynamic orchestration**

---

### Architecture 1: **Intent-Driven Orchestration**

```
┌─────────────────────────────────────────────────────────────┐
│                     CONVERSATION LOOP                        │
│                                                              │
│  1. User sends message                                       │
│  2. MetaAgent analyzes:                                      │
│     - Conversation history (Oracle)                          │
│     - Current goals/mode                                     │
│     - Required context (Neo4j relationships)                 │
│     - User intent (LLM understanding)                        │
│                                                              │
│  3. MetaAgent decides:                                       │
│     - What mode are we in? (explore / debug / implement)     │
│     - What agents to invoke? (1 or many, in sequence/parallel)│
│     - What context to load? (Pinecone semantic search)       │
│     - How to respond? (ask question / provide answer / execute)│
│                                                              │
│  4. Execute decided agents                                   │
│  5. Return to user                                           │
│  6. Update conversation state                                │
│  7. Loop                                                     │
└─────────────────────────────────────────────────────────────┘
```

**Key Changes:**
- ✅ **MetaAgent**: New orchestration layer that decides what to do next
- ✅ **Mode-agnostic agents**: ScopeDiscovery, ContextBuilder work for both Q&A and implementation
- ✅ **Dynamic agent selection**: Not all workflows need all agents
- ✅ **Conversation state**: Track mode, goals, confidence, context focus

---

### Architecture 2: **Conversation State Model**

```java
public class ConversationState {
    // Current mode
    private ConversationMode mode;  // EXPLORE, DEBUG, IMPLEMENT, REVIEW

    // User's goals (multiple can be active)
    private List<UserGoal> goals;
    // Example: [
    //   {type: "understand", target: "authentication flow", confidence: 0.95},
    //   {type: "fix", target: "NullPointerException in login", confidence: 0.8}
    // ]

    // Conversation focus
    private CodeContext currentFocus;  // Classes/methods currently discussing

    // Execution plan (if in IMPLEMENT mode)
    private ExecutionPlan plan;  // Steps to accomplish implementation

    // Confidence in current direction
    private double pathConfidence;  // 0.0 - 1.0

    // Full message history
    private List<ChatMessage> history;
}

public enum ConversationMode {
    EXPLORE,      // User asking questions, exploring codebase
    DEBUG,        // User investigating a bug/issue
    IMPLEMENT,    // User wants code changes
    REVIEW        // Reviewing proposed changes
}

public class UserGoal {
    private GoalType type;      // UNDERSTAND, FIX, BUILD, REFACTOR
    private String target;      // What specifically (class, feature, bug)
    private double confidence;  // How certain we are about this goal
    private boolean achieved;   // Is this goal complete?
}
```

**Benefits:**
- Can track multiple simultaneous goals
- Can switch modes mid-conversation
- Confidence scores guide decisions (not binary yes/no)
- Agent decisions update state, not hardcoded routing

---

### Architecture 3: **MetaAgent (Orchestrator)**

```java
@Component
public class MetaAgent {

    /**
     * Core orchestration logic - decides what to do next
     */
    public OrchestrationDecision decide(ConversationState state, String userMessage) {

        // 1. Update conversation state with new message
        state.addMessage(userMessage, "user");

        // 2. Ask LLM to analyze intent with FULL context
        IntentAnalysis intent = analyzeIntentWithLLM(state);
        // Returns:
        // - What user wants (goals)
        // - Confidence in understanding
        // - What information is missing
        // - Suggested mode (explore / debug / implement)

        // 3. Determine if mode should change
        if (shouldChangeMode(state.getMode(), intent.getSuggestedMode())) {
            state.setMode(intent.getSuggestedMode());
        }

        // 4. Decide which agents to invoke based on MODE + GOALS
        List<AgentInvocation> agentsToRun = selectAgents(state, intent);
        // Example:
        // - EXPLORE mode → [DocumentationAgent, ContextBuilder]
        // - DEBUG mode → [LogAnalyzer, ScopeDiscovery, ContextBuilder]
        // - IMPLEMENT mode → [ScopeDiscovery, ContextBuilder, CodeGenerator, BuildValidator, ...]

        // 5. If confidence too low, ask clarifying questions
        if (intent.getConfidence() < 0.7) {
            return OrchestrationDecision.askDeveloper(intent.getQuestions());
        }

        // 6. Execute agents
        return OrchestrationDecision.executeAgents(agentsToRun);
    }

    /**
     * Use LLM to understand user intent from conversation history
     */
    private IntentAnalysis analyzeIntentWithLLM(ConversationState state) {
        String prompt = """
            You are analyzing a developer conversation to understand intent.

            CONVERSATION HISTORY:
            %s

            CURRENT GOALS:
            %s

            LATEST MESSAGE:
            %s

            Analyze:
            1. What does the developer want to accomplish?
            2. Are there multiple goals? (e.g., "explain this then fix it")
            3. What mode should we be in? (explore / debug / implement / review)
            4. What information do we need?
            5. Confidence in this understanding (0.0 - 1.0)

            Return JSON with:
            {
              "goals": [{type, target, confidence}],
              "suggestedMode": "explore|debug|implement|review",
              "requiredContext": ["class names", "method names"],
              "questions": ["clarifying questions if confidence < 0.7"],
              "confidence": 0.95
            }
            """.formatted(
                formatHistory(state.getHistory()),
                formatGoals(state.getGoals()),
                state.getLastUserMessage()
            );

        return geminiClient.analyze(prompt);
    }

    /**
     * Select which agents to run based on mode and goals
     */
    private List<AgentInvocation> selectAgents(ConversationState state, IntentAnalysis intent) {
        List<AgentInvocation> agents = new ArrayList<>();

        // Dynamic agent selection based on MODE
        switch (state.getMode()) {
            case EXPLORE:
                // Just need context + explanation
                agents.add(AgentInvocation.of("ContextBuilder"));
                agents.add(AgentInvocation.of("DocumentationAgent"));
                break;

            case DEBUG:
                // Need logs + scope + context
                if (state.hasLogs()) {
                    agents.add(AgentInvocation.of("LogAnalyzer"));
                }
                agents.add(AgentInvocation.of("ScopeDiscovery"));
                agents.add(AgentInvocation.of("ContextBuilder"));
                agents.add(AgentInvocation.of("DocumentationAgent"));  // Explain the bug
                break;

            case IMPLEMENT:
                // Full pipeline (but can still skip agents based on goals)
                agents.add(AgentInvocation.of("ScopeDiscovery"));
                agents.add(AgentInvocation.of("ContextBuilder"));
                agents.add(AgentInvocation.of("CodeGenerator"));
                agents.add(AgentInvocation.of("BuildValidator"));
                // ... etc
                break;

            case REVIEW:
                // Just review agents
                agents.add(AgentInvocation.of("PRReviewer"));
                break;
        }

        // Remove agents that aren't needed based on goals
        return agents.stream()
            .filter(agent -> isAgentNeeded(agent, intent.getGoals()))
            .collect(Collectors.toList());
    }
}
```

**Benefits:**
- Single point of orchestration (no hardcoded graph routing)
- LLM decides what to do with full context
- Can invoke different agent combinations per conversation
- Supports mode switching

---

### Architecture 4: **Simplified Prompt (No CRITICAL Rules)**

**OLD (requirement-analyzer.yaml):**
```yaml
CRITICAL RULES FOR QUESTIONS: [30 lines]
CRITICAL: If greeting: [10 lines]
CRITICAL: If conversational: [10 lines]
Determine task type: [20 lines]
Examples of GOOD/BAD questions: [40 lines]
```

**NEW (meta-agent-orchestrator.yaml):**
```yaml
systemPrompt: |
  You are the orchestration brain for an AI code assistant.

  Your job: Understand what the developer wants from the conversation.

  You have access to:
  - Full conversation history
  - Codebase knowledge (Neo4j relationships, Pinecone embeddings)
  - Current goals and mode

  Analyze each message and determine:
  1. What are the developer's goals? (understand / fix / build / refactor)
  2. What mode should we be in? (explore / debug / implement / review)
  3. What context do we need to load? (specific classes, methods, flows)
  4. How confident are you? (0.0 - 1.0)
  5. What questions do you have? (only if confidence < 0.7)

  Trust yourself. Use conversation history. Be flexible.
```

**Benefits:**
- ✅ **Generic**: No hardcoded patterns
- ✅ **Trusts LLM**: No over-constraints
- ✅ **Uses context**: Explicitly mentions conversation history
- ✅ **Maintainable**: Won't grow with every edge case
- ✅ **Flexible**: Can handle new scenarios without prompt updates

---

## 🗑️ Code That Can Be Deleted

### 1. **Hardcoded Task Type Logic**
- `AutoFlowWorkflow.java` lines 88-91 (DOCUMENTATION check)
- `RequirementAnalyzerAgent.java` lines 86-87 (isDocumentationTask check)
- `requirement-analyzer.yaml` lines 64-71 (task type definitions)

### 2. **Greeting/Conversational Patterns**
- `requirement-analyzer.yaml` lines 52-61 (greeting CRITICAL rules)
- All pattern matching logic

### 3. **Duplicate extractRepoName() Implementations**
- Keep only `GitOperationsService.extractRepoName()`
- Delete from 6+ agents

### 4. **Hardcoded Progress Calculation**
- `WorkflowResponse.calculateProgress()` switch statement
- Replace with dynamic calculation based on goals

### 5. **Fixed Workflow Graph** (Eventually)
- Most of `AutoFlowWorkflow.initialize()` can be simplified
- Keep agents as nodes, but routing becomes dynamic

---

## 🎬 Implementation Strategy

### Phase 1: **Foundation** (Clean up existing issues)
1. ✅ Fix NullPointerException validations (DONE)
2. Remove duplicate `extractRepoName()` - use service everywhere
3. Delete hardcoded greeting/conversational patterns from prompt
4. Add `ConversationState` model to track mode/goals

### Phase 2: **MetaAgent Introduction**
1. Create `MetaAgent` with orchestration logic
2. Create `meta-agent-orchestrator.yaml` prompt (simplified)
3. Update `WorkflowController` to invoke MetaAgent first
4. MetaAgent decides which agents to run (keep existing graph for now)

### Phase 3: **Dynamic Orchestration**
1. Refactor `AutoFlowWorkflow` to support dynamic paths
2. Allow skipping agents based on MetaAgent decisions
3. Support mode switching mid-conversation
4. Remove fixed routing logic

### Phase 4: **Cleanup**
1. Delete unused task type classifications
2. Simplify prompts (remove CRITICAL rules)
3. Remove hardcoded progress calculation
4. Update UI to show mode/goals instead of fixed progress bar

---

## 📊 Comparison: Before vs After

| Aspect | BEFORE (Current) | AFTER (Proposed) |
|--------|-----------------|------------------|
| **Intent Understanding** | Forced into 5 task types | LLM analyzes with full context |
| **Workflow** | Fixed linear path | Dynamic agent selection |
| **Routing** | Hardcoded if/else | MetaAgent orchestration |
| **Conversation** | One-shot classification | Continuous state tracking |
| **Mode Switching** | ❌ Impossible | ✅ Seamless (Q&A → Debug → Fix) |
| **Prompts** | 100+ lines of CRITICAL rules | 20 lines generic prompt |
| **Progress** | 11 hardcoded percentages | Goal-based completion |
| **Code Reuse** | `extractRepoName()` × 7 | Single service method |
| **Flexibility** | Brittle, pattern-based | Robust, understanding-based |

---

## 💬 Example: How Conversations Would Work

### Scenario 1: Exploratory Question
```
User: "hi"

MetaAgent analyzes:
- mode: EXPLORE (user just greeted, no specific task)
- goals: []
- confidence: 0.3 (unclear what user wants)
- decision: ASK_DEVELOPER

Response: "Hi! What would you like to explore or work on today?"
```

### Scenario 2: Mode Switching (Q&A → Implementation)
```
User: "Explain how authentication works in this codebase"

MetaAgent analyzes:
- mode: EXPLORE
- goals: [{type: UNDERSTAND, target: "authentication flow", confidence: 0.95}]
- decision: EXECUTE_AGENTS([ContextBuilder, DocumentationAgent])

System: [Loads authentication context, generates explanation]

---

User: "Great! Now add OAuth support"

MetaAgent analyzes:
- mode: IMPLEMENT (mode shift detected!)
- goals: [
    {type: UNDERSTAND, target: "authentication flow", achieved: true},
    {type: BUILD, target: "OAuth integration", confidence: 0.9}
  ]
- decision: EXECUTE_AGENTS([ScopeDiscovery, ContextBuilder, CodeGenerator, ...])

System: [Proposes scope, generates OAuth code, runs tests, creates PR]
```

### Scenario 3: Debugging Then Fixing
```
User: "I'm seeing 500 errors in production"

MetaAgent analyzes:
- mode: DEBUG
- goals: [{type: FIX, target: "500 errors", confidence: 0.6}]
- questions: ["Can you paste the error logs?"]
- decision: ASK_DEVELOPER

System: "I can help debug this. Can you paste the error logs?"

---

User: [pastes logs]

MetaAgent analyzes:
- mode: DEBUG
- goals: [{type: FIX, target: "NullPointerException in /api/checkout", confidence: 0.95}]
- decision: EXECUTE_AGENTS([LogAnalyzer, ScopeDiscovery, ContextBuilder, DocumentationAgent])

System: "I found the issue: NullPointerException in CheckoutService.java:125..."
System: "Here's what's happening: [explanation]"

---

User: "Ok, fix it"

MetaAgent analyzes:
- mode: IMPLEMENT (mode shift!)
- goals: [{type: FIX, target: "NullPointerException in CheckoutService", confidence: 1.0}]
- decision: EXECUTE_AGENTS([CodeGenerator, BuildValidator, TestRunner, ...])

System: [Generates fix, runs tests, creates PR]
```

---

## 🤔 Open Questions for Discussion

1. **MetaAgent Placement**: Should it be a new agent in LangGraph4J or a separate service layer?

2. **Backward Compatibility**: Do we need to support old workflows during migration?

3. **Performance**: Will asking LLM for orchestration decisions on every message be too slow?
   - Potential solution: Cache decisions, only re-analyze on mode shifts

4. **Agent Reusability**: Can we make ALL agents mode-agnostic?
   - Example: ScopeDiscovery for both "explain what files are affected" AND "find files to modify"

5. **Conversation Persistence**: Should ConversationState be saved to Oracle after each turn?

6. **UI Changes**: How to show mode/goals in UI instead of hardcoded progress bar?

---

## 📝 Next Steps

1. **User Review**: Get feedback on this architectural direction
2. **Prioritize**: Which problems to fix first?
3. **Prototype**: Build small proof-of-concept for MetaAgent
4. **Incremental Migration**: Don't rewrite everything - migrate piece by piece

---

**Let's discuss this before writing any code!**

# RAG Orchestrator - Enterprise Architecture

**Version:** 2.0
**Last Updated:** 2025-12-30
**Status:** Production-Ready (Phase 1 Complete)

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Current Architecture (Phase 1)](#current-architecture-phase-1)
3. [Repository Scope Strategy](#repository-scope-strategy)
4. [Multi-LLM Architecture](#multi-llm-architecture)
5. [Implementation Phases](#implementation-phases)
6. [Enterprise Features](#enterprise-features)
7. [Technical Stack](#technical-stack)
8. [Configuration Management](#configuration-management)

---

## Executive Summary

### **What Is RAG Orchestrator?**

An enterprise-grade **autonomous code generation and maintenance platform** that combines:
- **Retrieval-Augmented Generation (RAG)** for code understanding
- **Multi-agent workflow orchestration** for complex tasks
- **LLM-based natural language understanding** for developer interaction
- **Graph-based code analysis** (Neo4j) for dependency tracking
- **Vector search** (Pinecone) for semantic code retrieval

### **Why Does It Matter?**

**vs. Cursor/GitHub Copilot:**
- ✅ **Repeatable workflows** - Nightly code reviews, automated refactoring jobs
- ✅ **Approval gates** - Human-in-the-loop for critical decisions
- ✅ **Audit trails** - Track who requested what, when, why (compliance)
- ✅ **Policy enforcement** - Block PRs with blacklisted libraries
- ✅ **Batch operations** - Refactor 500 microservices overnight
- ✅ **Enterprise integrations** - SonarQube, Snyk, Backstage, ServiceNow

### **Unique Value Proposition**

| Feature | Cursor/Copilot | RAG Orchestrator |
|---------|---------------|------------------|
| **Scope** | Single file/function | Entire codebase + graph |
| **Context** | Limited window | Full graph traversal + vector search |
| **Workflows** | Ad-hoc | Orchestrated multi-agent |
| **Batch Ops** | ❌ Manual | ✅ Automated (all repos) |
| **Audit** | ❌ None | ✅ Full compliance trail |
| **Policy** | ❌ None | ✅ Enforce rules |
| **Temporal** | ❌ None | ✅ "Show changes last month" |

---

## Current Architecture (Phase 1)

### **High-Level Flow**

```
User Input (Natural Language)
    ↓
RequirementAnalyzer (LLM classifies: code_generation, bug_fix, documentation, refactoring)
    ↓
[BRANCH: Based on requirement type]
    ↓
LogAnalyzer → ScopeDiscovery → ScopeApproval (LLM-based NLU)
    ↓                              ↓
[User Approval Required]    ContextBuilder (RAG: Pinecone + Neo4j)
    ↓
CodeGenerator (LLM + Compilation-in-the-loop)
    ↓
BuildValidator (Maven compile, retry with fixes)
    ↓
TestRunner (mvn test, categorize failures)
    ↓
PRReviewer (Static analysis + LLM quality check)
    ↓
ReadmeGenerator + PRCreator
    ↓
GitHub PR Created ✅
```

### **13 Agents (All Configuration-Driven)**

| Agent | Purpose | LLM? | Config Key |
|-------|---------|------|------------|
| **RequirementAnalyzer** | Classify user intent | ✅ | `agents.requirement-analyzer` |
| **LogAnalyzer** | Extract error context | ✅ | - |
| **ScopeDiscovery** | Find relevant files (Pinecone + Neo4j) | ✅ | `agents.scope-discovery` |
| **ScopeApproval** | Validate user approval (NLU) | ✅ | *LLM-based (no hardcoded keywords)* |
| **ContextBuilder** | Build 10k token context | ✅ | `agents.context-builder` |
| **CodeGenerator** | Generate code with compilation loop | ✅ | `gemini.agent-temperatures.code-generator` |
| **BuildValidator** | Compile + fix errors | ⚙️ | `agents.build` |
| **TestRunner** | Run tests, categorize failures | ⚙️ | `agents.test-runner` |
| **PRReviewer** | Static analysis + LLM review | ✅ | `agents.code-review` |
| **ReadmeGenerator** | Create PR description | ✅ | - |
| **PRCreator** | Prepare PR metadata | ✅ | - |
| **DocumentationAgent** | Explain codebase | ✅ | `agents.documentation` |
| **CodeIndexer** | Index repo to Pinecone + Neo4j | ⚙️ | - |

**✅ = LLM-powered | ⚙️ = Rule-based**

### **Data Flow**

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│   Pinecone  │      │    Neo4j    │      │   Gemini    │
│  (Vectors)  │◄────►│   (Graph)   │◄────►│    (LLM)    │
└─────────────┘      └─────────────┘      └─────────────┘
      ▲                     ▲                     ▲
      │                     │                     │
      └─────────────────────┴─────────────────────┘
                            │
                    ┌───────▼────────┐
                    │  RAG Context   │
                    │  (10k tokens)  │
                    └───────┬────────┘
                            │
                    ┌───────▼────────┐
                    │ Code Generator │
                    └────────────────┘
```

---

## Repository Scope Strategy

### **Problem Statement**

Current implementation handles **1 repo per conversation**. Future needs:
- **No repo:** General queries ("Explain OAuth2 flow")
- **1 repo:** Current implementation ✅
- **Multiple repos:** Cross-repo analysis ("Find all services calling this API")
- **All repos:** Batch operations ("Update Spring Boot 2.7 → 3.2 in all 500 microservices")

### **Architecture Design**

#### **1. Repository Context Model**

```java
public enum RepositoryScope {
    NONE,       // No repo (documentation lookup, general questions)
    SINGLE,     // 1 repo (current implementation)
    MULTIPLE,   // Specific list of repos
    ALL,        // All repos in organization
    DOMAIN      // Repos by domain tag (e.g., "payment-services")
}

@Value
@Builder
public class RepositoryContext {
    RepositoryScope scope;
    List<String> repoUrls;          // For SINGLE/MULTIPLE
    String organizationId;          // For ALL
    List<String> domainTags;        // For DOMAIN
    String baseBranch;              // Default branch or specific branch
}
```

#### **2. Workspace Management**

```yaml
app:
  workspace:
    base-dir: ${WORKSPACE_DIR:/tmp/ai-workspace}
    multi-repo:
      enabled: true
      max-concurrent-clones: 10
      clone-timeout-minutes: 5
      shared-cache: true  # Share .m2, node_modules across repos
```

**Implementation:**

```java
public interface WorkspaceManager {
    /**
     * Initialize workspace for repository context.
     * For ALL scope, lazily clones repos on-demand.
     */
    Workspace initialize(RepositoryContext context);

    /**
     * Get or clone specific repository.
     */
    File getRepository(String repoUrl, String branch);

    /**
     * Execute operation across all repos in scope.
     */
    <T> Map<String, T> executeAcrossRepos(
        RepositoryContext context,
        Function<File, T> operation
    );
}
```

#### **3. Cross-Repo Queries (Neo4j)**

**Use Case:** "Find all microservices that call `PaymentService.processPayment()`"

```cypher
// Phase 2: Cross-repo graph
MATCH (caller:MethodNode)-[:CALLS]->(callee:MethodNode)
WHERE callee.className = 'PaymentService'
  AND callee.methodName = 'processPayment'
  AND caller.repoUrl <> callee.repoUrl  // Cross-repo only
RETURN DISTINCT caller.repoUrl, caller.className, caller.methodName
```

**Schema Extension:**

```java
@Node("MethodNode")
public class MethodNode {
    // Existing fields...

    @Property("repoUrl")
    private String repoUrl;  // ← NEW: Track source repo

    @Property("repoName")
    private String repoName;

    @Property("indexedAt")
    private LocalDateTime indexedAt;  // ← NEW: Temporal queries
}
```

#### **4. Batch Operations**

**Example:** "Update all repos from Spring Boot 2.7 to 3.2"

```java
@Component
public class BatchOperationExecutor {

    public BatchOperationResult execute(BatchOperation operation) {
        // 1. Discover affected repos
        List<String> repos = operation.getScope() == ALL
            ? organizationRepos.getAllRepos()
            : operation.getRepoUrls();

        // 2. Parallel execution with throttling
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<OperationResult>> futures = repos.stream()
            .map(repo -> executor.submit(() -> {
                // Clone repo
                File workspace = workspaceManager.getRepository(repo, "main");

                // Execute operation (e.g., update pom.xml)
                CodeGenerationResponse code = codeGenerator.generate(
                    operation.getPrompt(),
                    buildContext(workspace)
                );

                // Compile + Test
                BuildResult build = buildValidator.validate(workspace, code);

                // Create PR
                if (build.isSuccess()) {
                    return prCreator.createPR(repo, code, build);
                }

                return OperationResult.failed(repo, build.getErrors());
            }))
            .toList();

        // 3. Collect results
        return BatchOperationResult.from(futures);
    }
}
```

---

## Multi-LLM Architecture

### **Problem Statement**

Current implementation hardcoded to **Gemini**. Future needs:
- **Switch LLMs:** Gemini, Claude, OpenAI, Llama
- **Per-agent LLM:** Use Claude for code review, Gemini for generation
- **Fallback strategy:** If Gemini rate-limited, fallback to Claude
- **Cost optimization:** Use cheaper models for simple tasks

### **Architecture Design**

#### **1. LLM Provider Abstraction**

```java
public interface LLMProvider {
    /**
     * Generate text response from prompt.
     */
    String generate(String prompt, LLMConfig config);

    /**
     * Generate structured JSON response.
     */
    <T> T generateJson(String prompt, Class<T> responseType, LLMConfig config);

    /**
     * Get embeddings for semantic search.
     */
    List<Float> getEmbedding(String text);

    /**
     * Check if provider is available.
     */
    boolean isAvailable();

    /**
     * Get cost per 1M tokens.
     */
    CostInfo getCostInfo();
}

@Value
public class LLMConfig {
    double temperature;
    int maxTokens;
    List<String> stopSequences;
    Map<String, Object> providerSpecificConfig;
}
```

#### **2. Provider Implementations**

```java
@Component
public class GeminiProvider implements LLMProvider {
    // Existing GeminiClient implementation
}

@Component
public class ClaudeProvider implements LLMProvider {
    private final WebClient anthropicClient;

    @Override
    public String generate(String prompt, LLMConfig config) {
        // Call Anthropic API
        return anthropicClient.post()
            .uri("/v1/messages")
            .bodyValue(Map.of(
                "model", "claude-3-5-sonnet-20241022",
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", config.getTemperature(),
                "max_tokens", config.getMaxTokens()
            ))
            .retrieve()
            .bodyToMono(String.class)
            .block();
    }
}

@Component
public class OpenAIProvider implements LLMProvider {
    // OpenAI implementation
}
```

#### **3. LLM Router with Fallback**

```java
@Component
public class LLMRouter {
    private final Map<String, LLMProvider> providers;
    private final LLMRoutingConfig config;

    public String generate(String agentName, String prompt) {
        // 1. Get preferred provider for agent
        String preferredProvider = config.getProviderForAgent(agentName);

        // 2. Try preferred provider
        LLMProvider provider = providers.get(preferredProvider);
        if (provider.isAvailable()) {
            try {
                return provider.generate(prompt, config.getConfigForAgent(agentName));
            } catch (RateLimitException e) {
                log.warn("Rate limited on {}, trying fallback", preferredProvider);
            }
        }

        // 3. Fallback cascade
        for (String fallback : config.getFallbackChain(agentName)) {
            LLMProvider fallbackProvider = providers.get(fallback);
            if (fallbackProvider.isAvailable()) {
                return fallbackProvider.generate(prompt, config.getConfigForAgent(agentName));
            }
        }

        throw new NoAvailableLLMException("All providers unavailable");
    }
}
```

#### **4. Configuration**

```yaml
app:
  llm:
    default-provider: gemini

    providers:
      gemini:
        enabled: true
        api-key: ${GEMINI_KEY}
        models:
          chat: gemini-1.5-flash
          embedding: text-embedding-004
        rate-limit: 60/min

      claude:
        enabled: true
        api-key: ${ANTHROPIC_KEY}
        models:
          chat: claude-3-5-sonnet-20241022
        rate-limit: 50/min

      openai:
        enabled: false
        api-key: ${OPENAI_KEY}
        models:
          chat: gpt-4-turbo
          embedding: text-embedding-3-large

    # Per-agent routing
    agent-routing:
      code-generator:
        provider: gemini
        fallback: [claude, openai]
        temperature: 0.3

      code-reviewer:
        provider: claude    # Better at reasoning
        fallback: [gemini]
        temperature: 0.5

      documentation:
        provider: gemini    # Cheaper for docs
        fallback: [claude]
        temperature: 0.8
```

---

## Implementation Phases

### **Phase 1: Single-Repo Foundation** ✅ **COMPLETE**

**Status:** Production-Ready
**Duration:** 3 months
**Completion Date:** 2025-12-30

**Deliverables:**
- ✅ 13-agent workflow orchestration
- ✅ Pinecone vector search + Neo4j graph
- ✅ LLM-based natural language understanding
- ✅ Compilation-in-the-loop code generation
- ✅ Configuration-driven architecture (150+ externalized values)
- ✅ Strategy patterns (GitUrlParser, FileOperation, enums)
- ✅ Full audit trail logging
- ✅ CODING_STANDARDS.md enforcement

**Metrics:**
- 64 files changed
- +2,206 lines added
- 24 new files created
- 0 nested classes (all extracted)
- 0 hardcoded values (all externalized)

---

### **Phase 2: Multi-Repo + Cross-Repo Analysis** ⏳ **NEXT**

**Estimated Duration:** 2 months
**Target Start:** Q1 2026

**Deliverables:**

#### **2.1 Repository Scope Management**
- [ ] `RepositoryContext` model (NONE/SINGLE/MULTIPLE/ALL/DOMAIN)
- [ ] `WorkspaceManager` interface + implementation
- [ ] Multi-repo cloning with concurrent limits
- [ ] Shared dependency cache (.m2, node_modules)

#### **2.2 Cross-Repo Graph Schema**
- [ ] Add `repoUrl`, `repoName`, `indexedAt` to all Neo4j nodes
- [ ] Cross-repo relationship queries
- [ ] Temporal queries ("Changes last month")
- [ ] Dependency graph across repos

#### **2.3 Batch Operations**
- [ ] `BatchOperationExecutor` with parallel execution
- [ ] Progress tracking per repo
- [ ] Rollback strategy (failed repos)
- [ ] Batch PR creation

**Use Cases Enabled:**
- "Find all services calling `PaymentService.processPayment()`" (cross-repo)
- "Show all classes changed in payment domain last month" (temporal)
- "Update Spring Boot 2.7 → 3.2 in all microservices" (batch)

---

### **Phase 3: Multi-LLM Support** ⏳ **PLANNED**

**Estimated Duration:** 1 month
**Target Start:** Q2 2026

**Deliverables:**

#### **3.1 LLM Abstraction**
- [ ] `LLMProvider` interface
- [ ] `GeminiProvider`, `ClaudeProvider`, `OpenAIProvider` implementations
- [ ] Unified embedding API

#### **3.2 LLM Router**
- [ ] Per-agent provider routing
- [ ] Fallback cascade
- [ ] Rate limit handling
- [ ] Cost tracking per provider

#### **3.3 Configuration**
- [ ] `app.llm.providers` YAML config
- [ ] `agent-routing` per-agent LLM selection
- [ ] Environment-specific provider selection (dev uses Gemini, prod uses Claude)

**Use Cases Enabled:**
- Switch code generation from Gemini to Claude
- Use OpenAI for embeddings, Claude for code review
- Automatic fallback when rate-limited

---

### **Phase 4: Enterprise Features** ⏳ **PLANNED**

**Estimated Duration:** 3 months
**Target Start:** Q3 2026

**Deliverables:**

#### **4.1 Policy Enforcement**
- [ ] `PolicyEngine` - Define rules in YAML
- [ ] Pre-commit hooks (block blacklisted imports)
- [ ] Post-generation validation (enforce naming conventions)
- [ ] Security policy (require review for auth code)

**Example Policy:**
```yaml
policies:
  blacklisted-imports:
    - "org.apache.commons.lang.StringUtils"  # Deprecated
    - "java.util.Date"                       # Use LocalDate
    action: BLOCK_PR

  naming-conventions:
    services: ".*Service$"
    repositories: ".*Repository$"
    action: WARN

  security-review:
    patterns: [".*Authentication.*", ".*OAuth.*"]
    action: REQUIRE_APPROVAL
    reviewers: ["security-team"]
```

#### **4.2 Audit & Compliance**
- [ ] Full audit trail database (PostgreSQL)
- [ ] "Show all AI-generated code last quarter" query
- [ ] "Prove no PII in logs" compliance report
- [ ] "Track which developer approved which AI suggestion"

#### **4.3 Enterprise Integrations**
- [ ] **SonarQube** - Code quality metrics
- [ ] **Snyk** - Security vulnerability scanning
- [ ] **Backstage** - Service catalog integration
- [ ] **ServiceNow** - Change management tickets

#### **4.4 Scheduled Jobs**
- [ ] Nightly code reviews (all repos)
- [ ] Weekly security scans
- [ ] Monthly dependency updates
- [ ] Quarterly compliance reports

**Use Cases Enabled:**
- "Block all PRs that import `java.util.Date`"
- "Require security team approval for authentication code"
- "Generate quarterly audit report for compliance team"
- "Automatically update dependencies every week"

---

## Enterprise Features

### **Policy Enforcement**

```java
public interface PolicyEngine {
    /**
     * Evaluate code against policies before PR creation.
     */
    PolicyEvaluationResult evaluate(CodeGenerationResponse code);

    /**
     * Block PR if critical policies violated.
     */
    boolean shouldBlockPR(PolicyEvaluationResult result);

    /**
     * Get required approvers for policy violations.
     */
    List<String> getRequiredApprovers(PolicyEvaluationResult result);
}

@Value
public class PolicyEvaluationResult {
    List<PolicyViolation> violations;
    PolicyAction recommendedAction;  // BLOCK, WARN, REQUIRE_APPROVAL
    List<String> requiredApprovers;
}
```

### **Audit Trail**

```java
@Entity
public class AuditLog {
    @Id UUID id;
    LocalDateTime timestamp;
    String userId;
    String conversationId;
    String agentName;
    String action;              // "code_generated", "pr_created", "approved"
    String targetRepo;
    String targetBranch;
    String llmProvider;         // "gemini", "claude"
    String llmModel;
    int tokenCount;
    double costUsd;
    String artifactUrl;         // GitHub PR URL
    Map<String, Object> metadata;
}
```

**Compliance Queries:**
```sql
-- All AI-generated code last quarter
SELECT * FROM audit_log
WHERE action = 'code_generated'
  AND timestamp > NOW() - INTERVAL '3 months';

-- Total cost by team
SELECT user_id, SUM(cost_usd) as total_cost
FROM audit_log
GROUP BY user_id;

-- Security-sensitive code changes
SELECT * FROM audit_log
WHERE metadata->>'code_pattern' LIKE '%Authentication%';
```

### **Batch Operations**

**Example:** Refactor 500 microservices

```java
BatchOperation operation = BatchOperation.builder()
    .scope(RepositoryScope.ALL)
    .organizationId("company-org")
    .operation("Update Spring Boot 2.7 to 3.2")
    .prompt("""
        Update pom.xml to use Spring Boot 3.2.0.
        Update all deprecated imports.
        Fix compilation errors.
        Run tests.
        """)
    .parallelism(10)  // 10 concurrent PRs
    .build();

BatchOperationResult result = batchExecutor.execute(operation);
// Result:
//   - 485 PRs created ✅
//   - 12 failed (compilation errors) ❌
//   - 3 skipped (already up-to-date) ⏭️
```

---

## Technical Stack

### **Core Technologies**

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Framework** | Spring Boot 3.2.3 | Dependency injection, REST APIs |
| **LLM** | Google Gemini 1.5 Flash | Code generation, NLU |
| **Vector DB** | Pinecone | Semantic code search |
| **Graph DB** | Neo4j | Dependency tracking, cross-repo analysis |
| **Relational DB** | Oracle | Conversation history, audit logs |
| **Workflow** | LangGraph4j | Agent orchestration |
| **Build** | Maven | Compilation, dependency management |
| **Git** | JGit | Repository cloning, parsing |

### **Future Additions (Phase 3+)**

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **LLMs** | Claude 3.5, OpenAI GPT-4 | Multi-LLM support |
| **Code Quality** | SonarQube | Static analysis |
| **Security** | Snyk | Vulnerability scanning |
| **Service Catalog** | Backstage | Enterprise integration |
| **Change Mgmt** | ServiceNow | ITSM integration |
| **Scheduler** | Quartz | Batch jobs, nightly scans |

---

## Configuration Management

### **Current Configuration Structure**

```yaml
app:
  workspace-dir: ${WORKSPACE_DIR:/tmp/ai-workspace}

  gemini:
    api-key: ${GEMINI_KEY}
    agent-temperatures:
      code-generator: 0.3    # Deterministic
      documentation: 0.8     # Creative
    retry:
      max-attempts: 3

  agents:
    scope-discovery:
      max-files: 7
      similarity:
        min-threshold: 0.5
    build:
      max-retry-attempts: 3
    test-runner:
      shell-windows: "cmd.exe"
      shell-unix: "sh"

  git:
    providers:
      github:
        pattern: "github.com"
        branch-separator: "/tree/"
```

### **Future Configuration (Phase 2+)**

```yaml
app:
  # Multi-repo support
  workspace:
    multi-repo:
      enabled: true
      max-concurrent-clones: 10

  # Multi-LLM support
  llm:
    default-provider: gemini
    providers:
      gemini: {api-key: ${GEMINI_KEY}}
      claude: {api-key: ${ANTHROPIC_KEY}}
    agent-routing:
      code-generator: {provider: gemini, fallback: [claude]}
      code-reviewer: {provider: claude, fallback: [gemini]}

  # Policy enforcement
  policies:
    blacklisted-imports:
      - "java.util.Date"
    action: BLOCK_PR

  # Enterprise integrations
  integrations:
    sonarqube:
      url: https://sonar.company.com
      token: ${SONAR_TOKEN}
    snyk:
      api-key: ${SNYK_TOKEN}
```

---

## Key Design Principles

### **1. Configuration-Driven**
- **Zero hardcoded values** - All thresholds, limits, patterns in `application.yml`
- **Environment-specific** - Dev/staging/prod use different configs
- **Hot-reloadable** - Changes take effect without recompilation

### **2. LLM-Based Natural Language Understanding**
- **No keyword matching** - Use LLM to interpret user input
- **Context-aware** - LLM has full conversation history
- **Adaptive** - Handles typos, variations, complex requests

### **3. Strategy Pattern Everywhere**
- **GitUrlParser** - Add Git providers via config
- **FileOperation** - Enum-based operation dispatch
- **LLMProvider** - Switch LLMs without code changes

### **4. Enterprise-Grade**
- **Audit trails** - Every action logged
- **Policy enforcement** - Block violations before PR
- **Compliance** - Query audit logs for reports
- **Batch operations** - Scale to 1000s of repos

### **5. Future-Proof Architecture**
- **Repository scopes** - NONE → SINGLE → MULTIPLE → ALL
- **Multi-LLM** - Gemini → Claude → OpenAI
- **Extensible** - Add new agents, providers, policies without breaking changes

---

## Next Steps

### **Immediate (Phase 2 Kickoff):**
1. Design `RepositoryContext` and `WorkspaceManager` interfaces
2. Extend Neo4j schema with `repoUrl` and `indexedAt` fields
3. Implement cross-repo queries
4. Create `BatchOperationExecutor` prototype

### **Mid-Term (Phase 3):**
1. Implement `LLMProvider` abstraction
2. Add Claude and OpenAI providers
3. Create `LLMRouter` with fallback logic
4. Test cost optimization strategies

### **Long-Term (Phase 4):**
1. Build `PolicyEngine` framework
2. Integrate SonarQube, Snyk, Backstage
3. Create compliance reporting dashboard
4. Schedule nightly batch jobs

---

**For detailed coding standards, see:** [CODING_STANDARDS.md](CODING_STANDARDS.md)
**For quick start guide, see:** [README.md](README.md)

# RAG Orchestrator

**Enterprise-grade autonomous code generation and maintenance platform**

[![Java](https://img.shields.io/badge/Java-11+-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Phase](https://img.shields.io/badge/Phase-1%20Complete-success.svg)](ARCHITECTURE.md)

---

## What Is This?

An **autonomous AI agent system** that:
- 🤖 Understands codebases using **RAG** (Retrieval-Augmented Generation)
- 🔄 Orchestrates **13 specialized agents** for complex workflows
- 📝 Generates code with **compilation-in-the-loop** validation
- 🎯 Uses **LLM-based NLU** for natural language understanding
- ✅ Creates **production-ready PRs** with tests and documentation

---

## Quick Start

### **Prerequisites**

```bash
# Required
- Java 11+
- Maven 3.6+
- Neo4j 5.x
- Oracle Database (or H2 for dev)

# API Keys
export GEMINI_KEY=your-gemini-api-key
export NEO4J_URI=bolt://localhost:7687
export NEO4J_USER=neo4j
export NEO4J_PASSWORD=password
```

### **Run**

```bash
# Clone repository
git clone <repo-url>
cd rag-orchestrator

# Configure
cp application.yml.example application.yml
# Edit application.yml with your API keys

# Build
mvn clean install -DskipTests

# Run
mvn spring-boot:run
```

### **Access**

- **API:** http://localhost:8080
- **Health:** http://localhost:8080/actuator/health
- **Metrics:** http://localhost:8080/api/metrics

---

## Example Usage

### **Generate Code**

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "developer@company.com",
    "message": "Add retry logic to PaymentService.processPayment() method",
    "repoUrl": "https://github.com/company/payment-service",
    "mode": "CODE_GENERATION"
  }'
```

**What Happens:**
1. **RequirementAnalyzer** classifies intent (code generation)
2. **ScopeDiscovery** finds `PaymentService.java` using Neo4j graph queries
3. **ScopeApproval** asks for confirmation (LLM-based NLU)
4. **ContextBuilder** retrieves relevant code (10k token budget)
5. **CodeGenerator** generates code with compilation loop
6. **BuildValidator** compiles + fixes errors (max 3 attempts)
7. **TestRunner** runs tests, categorizes failures
8. **PRReviewer** performs static analysis + LLM quality check
9. **PRCreator** creates GitHub PR with tests + documentation

**Result:** Production-ready PR in ~2-5 minutes ✅

---

## Why Use This?

### **vs. Cursor / GitHub Copilot**

| Feature | Cursor/Copilot | RAG Orchestrator |
|---------|---------------|------------------|
| **Scope** | Single file | Entire codebase + graph |
| **Context** | Limited window | Full dependency graph |
| **Workflows** | Ad-hoc | Orchestrated multi-agent |
| **Batch Ops** | ❌ Manual | ✅ 500+ repos overnight |
| **Audit** | ❌ None | ✅ Full compliance trail |
| **Policy** | ❌ None | ✅ Block blacklisted libs |

### **Unique Capabilities**

✅ **Repeatable Workflows** - Nightly code reviews, automated refactoring
✅ **Human-in-the-Loop** - Approval gates for critical decisions
✅ **Cross-Repo Analysis** - "Find all services calling this deprecated API"
✅ **Temporal Queries** - "Show changes in payment domain last month"
✅ **Policy Enforcement** - Block PRs with blacklisted imports
✅ **Batch Operations** - Update Spring Boot across 500 microservices

---

## Architecture

### **High-Level Flow**

```
User Input → RequirementAnalyzer → LogAnalyzer → ScopeDiscovery
    ↓
[User Approval Gate] ← ScopeApproval (LLM-based NLU)
    ↓
ContextBuilder (RAG: Neo4j graph queries)
    ↓
CodeGenerator (LLM + Compilation Loop)
    ↓
BuildValidator → TestRunner → PRReviewer
    ↓
ReadmeGenerator → PRCreator → GitHub PR ✅
```

### **Tech Stack**

- **LLM:** Google Gemini 1.5 Flash (Phase 3: Claude, OpenAI support)
- **Graph DB:** Neo4j (code search + dependency tracking)
- **Framework:** Spring Boot 3.2.3
- **Workflow:** LangGraph4j

**📚 Full architecture:** [ARCHITECTURE.md](ARCHITECTURE.md)

---

## Configuration

All agents are **fully configurable** via `application.yml`:

```yaml
app:
  gemini:
    agent-temperatures:
      code-generator: 0.3  # Deterministic
      documentation: 0.8   # Creative

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
```

**Zero hardcoded values** - tune per environment (dev/staging/prod) ✅

---

## Implementation Phases

### **Phase 1: Single-Repo Foundation** ✅ **COMPLETE**

- 13-agent workflow orchestration
- Neo4j graph-based RAG (replaced Pinecone)
- LLM-based natural language understanding
- Compilation-in-the-loop code generation
- Configuration-driven architecture

### **Phase 2: Multi-Repo + Cross-Repo** ⏳ **Q1 2026**

- Repository scopes (NONE/SINGLE/MULTIPLE/ALL/DOMAIN)
- Cross-repo dependency analysis
- Batch operations (500+ repos)
- Temporal queries

### **Phase 3: Multi-LLM Support** ⏳ **Q2 2026**

- LLM provider abstraction (Gemini/Claude/OpenAI)
- Per-agent LLM routing
- Fallback strategies
- Cost optimization

### **Phase 4: Enterprise Features** ⏳ **Q3 2026**

- Policy enforcement (block blacklisted imports)
- Audit trails (compliance reporting)
- Enterprise integrations (SonarQube, Snyk, Backstage)
- Scheduled batch jobs

**📅 Full roadmap:** [ARCHITECTURE.md](ARCHITECTURE.md#implementation-phases)

---

## Project Structure

```
rag-orchestrator/
├── src/main/java/com/purchasingpower/autoflow/
│   ├── workflow/agents/          # 13 specialized agents
│   │   ├── RequirementAnalyzerAgent.java
│   │   ├── ScopeDiscoveryAgent.java
│   │   ├── CodeGeneratorAgent.java
│   │   └── ...
│   ├── client/                   # External API clients
│   │   ├── GeminiClient.java
│   │   └── ...
│   ├── config/                   # Configuration classes
│   │   ├── GeminiConfig.java
│   │   ├── AgentConfig.java
│   │   └── ...
│   ├── model/                    # Domain models
│   │   ├── WorkflowStatus.java
│   │   ├── FileOperation.java
│   │   └── ...
│   └── util/                     # Utilities
│       ├── GitUrlParser.java
│       └── ...
├── src/main/resources/
│   ├── application.yml           # Configuration
│   └── prompts/                  # LLM prompts (YAML)
│       ├── code-generator.yaml
│       ├── scope-approval.yaml
│       └── ...
├── ARCHITECTURE.md               # Full architecture doc
├── CODING_STANDARDS.md           # Code quality rules
└── README.md                     # This file
```

---

## Development

### **Run Tests**

```bash
mvn test
```

### **Code Quality**

```bash
# Checkstyle
mvn checkstyle:check

# PMD
mvn pmd:check
```

**Enforced Standards:**
- Max method length: 20 lines
- Max cyclomatic complexity: 10
- No if-else chains (use Strategy pattern)
- Interface + Implementation pattern (ALWAYS)

**📏 Full standards:** [CODING_STANDARDS.md](CODING_STANDARDS.md)

---

## Contributing

### **Branching Strategy**

```bash
# Feature branch
git checkout -b feature/your-feature-name

# Bug fix
git checkout -b bugfix/issue-description

# Always prefix with type
```

### **Commit Messages**

```bash
# Good ✅
git commit -m "Add retry logic to GeminiClient with exponential backoff"
git commit -m "Fix workspace path bug in CodeIndexerAgent"

# Bad ❌
git commit -m "fix stuff"
git commit -m "updates"
```

### **Pull Requests**

- Reference issue number (if applicable)
- Include test coverage
- Pass all Checkstyle/PMD checks
- Add documentation for new features

---

## Troubleshooting

### **Maven Build Fails**

```bash
# Clear cache
mvn clean

# Skip tests
mvn clean install -DskipTests

# Debug
mvn -X clean install
```

### **Neo4j Connection Issues**

```bash
# Check Neo4j is running
curl http://localhost:7474

# Verify credentials
export NEO4J_USER=neo4j
export NEO4J_PASSWORD=your-password
```

---

## License

[Your License Here]

---

## Contact

For questions, issues, or feature requests:
- **GitHub Issues:** [Create Issue](https://github.com/yourorg/rag-orchestrator/issues)
- **Email:** your-team@company.com

---

**⚡ Built with enterprise-grade AI orchestration** | **📚 [Full Documentation](ARCHITECTURE.md)**

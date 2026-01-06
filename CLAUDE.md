# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**For full project context, vision, and what we're building:** Use the `project-knowledge` skill (located at `.claude/skills/project-knowledge/SKILL.md`). It contains the four pillars architecture, use cases, current issues, and implementation roadmap.

## Build Commands

```bash
# Build (skip tests for speed)
mvn clean install -DskipTests

# Run application
mvn spring-boot:run

# Run all tests
mvn test

# Code quality (currently disabled in pom.xml)
mvn checkstyle:check
mvn pmd:check
```

## Required Services

- **Neo4j**: `bolt://localhost:7687` (user: neo4j)
- **Oracle DB**: `localhost:1521/XE` (user: autoflow)
- **Environment variables**: `GEMINI_KEY`, `NEO4J_URI`, `NEO4J_USER`, `NEO4J_PASSWORD`

## Architecture Overview

This is an enterprise RAG (Retrieval-Augmented Generation) orchestrator for autonomous code generation. It uses Neo4j for code graph storage and Google Gemini as the LLM.

### Core Flow

```
User Message → ChatController → AutoFlowAgent → Tool Selection (via LLM)
                                      ↓
                              Tool Execution Loop (max 10 iterations)
                                      ↓
                              Response with citations
```

### Key Packages

- `agent/` - Unified tool-based agent system (AutoFlowAgent + Tool interface)
- `agent/tools/` - Individual tools (CodeGenTool, SearchTool, IndexTool, GraphQueryTool, DependencyTool, ExplainTool)
- `api/` - REST controllers (ChatController, KnowledgeController, SearchController)
- `knowledge/` - Neo4j graph store and indexing services
- `core/` - Domain interfaces (Repository, CodeEntity, SearchResult)
- `client/` - External clients (GeminiClient)
- `service/` - Business logic (~40+ services)
- `resources/prompts/` - LLM prompt templates in YAML

### Configuration

All values are externalized in `application.yml` (~150+ config values). Key sections:
- `app.gemini.agent-temperatures` - Per-agent LLM temperature
- `app.agents.*` - Agent-specific settings (max-files, thresholds, timeouts)
- `app.code-quality.*` - Complexity limits, compilation settings

## Coding Standards (MANDATORY)

These are strictly enforced. See `CODING_STANDARDS.md` for full details.

### Method Limits
- **Max 20 lines** per method
- **Max complexity 10**
- **Max 4 parameters** (use parameter objects)
- **Max nesting depth 3**

### Patterns

1. **Interface + Implementation** - ALWAYS create both:
   ```java
   // MyService.java (interface)
   public interface MyService { ... }

   // MyServiceImpl.java (implementation)
   @Service
   public class MyServiceImpl implements MyService { ... }
   ```

2. **Strategy Pattern** - No if-else chains. Use Map<Type, Strategy> routing.

3. **Immutable DTOs** - Use `@Value @Builder` from Lombok.

4. **Optional over null** - Return `Optional<T>` instead of nullable values.

5. **Fail-fast** - Use `Preconditions.checkNotNull()` at method entry.

6. **No hardcoded values** - Everything in `application.yml`.

### Logging

```java
log.info("Starting X for: {}", context);     // Entry points
log.debug("Step completed: {}", detail);      // Significant steps
log.warn("Unusual condition: {}", reason);    // Warnings
log.error("Failed X: {}", message, exception);// Errors with context
```

### Comments

Write **WHY**, not WHAT. Only comment non-obvious business logic.

## Git Workflow

- Feature branches: `feature/description`
- Bugfix branches: `bugfix/issue-description`
- Main branch: `main`

## Project Status

**Current Focus:** Phase 0 - Cleanup & Fix Critical Issues

Critical issues that need fixing (see `project-knowledge` skill for details):
- Semantic search is keyword CONTAINS, not true embeddings
- Conversation history lost on reload (Jackson LocalDateTime)
- Neo4j pipe `|` treated as literal, not OR
- Embeddings infrastructure exists but never called

**Roadmap:**
- Phase 0: Cleanup & Foundation (current)
- Phase 1: Knowledge Layer (vector embeddings, hybrid search)
- Phase 2: Agent Core (tool-based agent, conversation memory)
- Phase 3: Multi-Repo Intelligence
- Phase 4: Legacy Monolith Analyzer
- Phase 5: Action Engine (batch operations)

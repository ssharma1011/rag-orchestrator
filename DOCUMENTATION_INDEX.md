# AutoFlow Documentation Index

**Version:** 2.0.0
**Last Updated:** 2026-01-05
**Project:** Enterprise RAG Orchestrator for Autonomous Code Generation

---

## 📋 Documentation Overview

This repository contains comprehensive documentation for the AutoFlow system. This index will guide you to the right document based on your needs.

---

## 🚀 Getting Started (New Team Members)

**Start here if you're new to the project:**

1. **[EXECUTIVE_SUMMARY.md](./EXECUTIVE_SUMMARY.md)** ⭐ START HERE
   - High-level overview for management and tech leads
   - What the system does, key capabilities, business value
   - Technology stack and architecture summary
   - **Time to read:** 5 minutes

2. **[QUICK_START.md](./QUICK_START.md)**
   - Get the system running in 15-20 minutes
   - Step-by-step setup instructions
   - First test and verification
   - **Time to complete:** 15-20 minutes

3. **[USAGE_GUIDE.md](./USAGE_GUIDE.md)**
   - How to use the system as a developer
   - Common workflows and use cases
   - Example queries and expected responses
   - **Time to read:** 15 minutes

---

## 🏗️ Architecture & Design (Developers)

**Read these to understand how the system works:**

4. **[docs/ARCHITECTURE_COMPLETE.md](./docs/ARCHITECTURE_COMPLETE.md)** ⭐ COMPREHENSIVE
   - Complete system architecture (100+ pages)
   - Request flow from API to Neo4j to LLM
   - Neo4j graph schema and vector indexes
   - Embedding pipeline detailed flow
   - Search mechanisms (Hybrid vs Semantic)
   - Tool-based agent system
   - API reference with examples
   - **Time to read:** 2-3 hours (reference document)

5. **[docs/VISUAL_DIAGRAMS.md](./docs/VISUAL_DIAGRAMS.md)**
   - ASCII art diagrams for presentations
   - System architecture diagram
   - Request flow diagram
   - Neo4j schema visualization
   - Embedding pipeline flow
   - Tool execution flow
   - **Use for:** PowerPoint, Confluence, presentations
   - **Time to read:** 30 minutes

6. **[docs/CODEBASE-UNDERSTANDING-DESIGN.md](./docs/CODEBASE-UNDERSTANDING-DESIGN.md)**
   - Original design document for codebase understanding feature
   - Problem statement and requirements
   - Design decisions and trade-offs
   - **Time to read:** 20 minutes

---

## 🔧 Configuration & Prompts

7. **[docs/PROMPT_CATALOG.md](./docs/PROMPT_CATALOG.md)**
   - All 17 externalized prompt templates
   - Location: `src/main/resources/prompts/*.yaml`
   - Prompt structure, variables, and usage
   - How to modify prompts without code changes
   - **Time to read:** 30 minutes

8. **[application.yml](./src/main/resources/application.yml)**
   - All configuration values (~150+ settings)
   - LLM provider configuration (Gemini, Ollama)
   - Neo4j and Oracle database settings
   - Code quality rules and limits
   - Agent-specific configurations
   - **Reference:** Consult when configuring the system

---

## 🧪 Testing & Quality Assurance

9. **[TEST_PLAN.md](./TEST_PLAN.md)**
   - Comprehensive testing strategy
   - Unit tests, integration tests, end-to-end tests
   - How to verify vector search is working
   - Neo4j diagnostic queries
   - **Time to read:** 20 minutes

10. **[docs/CODING_STANDARDS.md](./docs/CODING_STANDARDS.md)**
    - Mandatory coding standards for the project
    - Method limits (max 20 lines, complexity 10)
    - Required patterns (Interface + Implementation, Strategy)
    - DTO standards (immutable, @Value @Builder)
    - **MUST READ** before contributing code
    - **Time to read:** 15 minutes

---

## 🔨 Implementation & Changes

11. **[IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md)**
    - What was implemented in recent work
    - Smart auto-indexing (no manual indexing required)
    - Semantic search with vector embeddings
    - Change detection via git commit hash
    - **Time to read:** 10 minutes

12. **[IMPROVEMENTS_SUMMARY.md](./IMPROVEMENTS_SUMMARY.md)**
    - Recent improvements to the system
    - Hybrid search implementation
    - LangChain4j integration
    - Tiered LLM service
    - **Time to read:** 15 minutes

13. **[CHANGES_SUMMARY.md](./CHANGES_SUMMARY.md)**
    - Detailed log of recent changes
    - Files modified and why
    - Before/after code comparisons
    - **Reference:** For understanding recent modifications

14. **[VECTOR_SEARCH_FIXES.md](./VECTOR_SEARCH_FIXES.md)**
    - Fixes applied to vector search functionality
    - Debugging steps and solutions
    - **Reference:** Troubleshooting guide

---

## 🧹 Maintenance & Cleanup

15. **[docs/CLEANUP_GUIDE.md](./docs/CLEANUP_GUIDE.md)** ⭐ ACTION REQUIRED
    - Identifies 13 obsolete workflow agents (~15,000 lines)
    - Safe deletion instructions
    - Testing checklist before/after deletion
    - Rollback plan if issues arise
    - **Action:** Review and execute cleanup script
    - **Time to read:** 20 minutes

16. **[docs/TODO.md](./docs/TODO.md)**
    - Known issues and future enhancements
    - Prioritized backlog
    - **Reference:** For planning future work

---

## 🚀 Deployment & Operations

17. **[DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md)**
    - Production deployment instructions
    - Environment setup (Docker, Kubernetes)
    - Monitoring and logging configuration
    - Scaling considerations
    - **Time to read:** 30 minutes

---

## 📊 Diagnostic & Troubleshooting

18. **[diagnose_neo4j.cypher](./diagnose_neo4j.cypher)**
    - Neo4j diagnostic queries
    - Verify embeddings, indexes, relationships
    - **Use when:** Troubleshooting vector search issues

19. **[FIX_PLAN.md](./FIX_PLAN.md)**
    - Previous bug fixes and their resolutions
    - **Reference:** Historical troubleshooting

20. **[DIAGNOSIS_RESULTS.md](./DIAGNOSIS_RESULTS.md)**
    - Results of diagnostic tests
    - **Reference:** Historical issues

---

## 🗂️ Project Management

21. **[CLAUDE.md](./CLAUDE.md)**
    - Instructions for Claude Code (AI assistant)
    - Build commands, architecture overview
    - Coding standards summary
    - **Reference:** For AI-assisted development

22. **[.claude/skills/project-knowledge/SKILL.md](./.claude/skills/project-knowledge/SKILL.md)**
    - Complete project context for Claude Code
    - Four pillars architecture
    - Current issues and roadmap
    - **Reference:** Project vision and strategy

---

## 📚 Documentation by Audience

### For Management / Tech Leads
1. [EXECUTIVE_SUMMARY.md](./EXECUTIVE_SUMMARY.md) - What this system does and why it matters
2. [docs/VISUAL_DIAGRAMS.md](./docs/VISUAL_DIAGRAMS.md) - Diagrams for presentations
3. [docs/CLEANUP_GUIDE.md](./docs/CLEANUP_GUIDE.md) - Code cleanup recommendations

### For New Developers
1. [EXECUTIVE_SUMMARY.md](./EXECUTIVE_SUMMARY.md) - Overview
2. [QUICK_START.md](./QUICK_START.md) - Get up and running
3. [USAGE_GUIDE.md](./USAGE_GUIDE.md) - How to use the system
4. [docs/CODING_STANDARDS.md](./docs/CODING_STANDARDS.md) - How to write code

### For Architects / Senior Developers
1. [docs/ARCHITECTURE_COMPLETE.md](./docs/ARCHITECTURE_COMPLETE.md) - Complete architecture
2. [docs/PROMPT_CATALOG.md](./docs/PROMPT_CATALOG.md) - LLM prompt engineering
3. [docs/CODEBASE-UNDERSTANDING-DESIGN.md](./docs/CODEBASE-UNDERSTANDING-DESIGN.md) - Design decisions
4. [IMPLEMENTATION_SUMMARY.md](./IMPLEMENTATION_SUMMARY.md) - Recent implementations

### For DevOps / SRE
1. [QUICK_START.md](./QUICK_START.md) - Service dependencies
2. [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) - Production deployment
3. [application.yml](./src/main/resources/application.yml) - Configuration reference
4. [diagnose_neo4j.cypher](./diagnose_neo4j.cypher) - Diagnostic queries

### For QA / Testers
1. [TEST_PLAN.md](./TEST_PLAN.md) - Testing strategy
2. [USAGE_GUIDE.md](./USAGE_GUIDE.md) - Expected behavior
3. [VECTOR_SEARCH_FIXES.md](./VECTOR_SEARCH_FIXES.md) - Known issues and fixes

---

## 🔍 Quick Reference

### Technology Stack
- **Backend:** Spring Boot 3.2, Java 17
- **LLM Providers:** Google Gemini, Ollama (local)
- **Graph Database:** Neo4j 5.15+ (with vector indexes)
- **Embedding Model:** mxbai-embed-large (1024 dimensions)
- **Chat Model:** qwen2.5-coder:7b (local) or Gemini Flash
- **Framework:** LangChain4j, LangGraph4j
- **Storage:** Oracle Database (conversation history)

### Key Features
- ✅ Autonomous code generation with tool-based agent
- ✅ Vector embeddings for semantic code search
- ✅ Neo4j graph storage of code structure
- ✅ Hybrid search (exact match + fuzzy + semantic)
- ✅ 17 externalized LLM prompt templates
- ✅ Real-time SSE streaming responses
- ✅ Auto-indexing with git change detection

### Project Metrics
- **Total Classes:** ~265 (after cleanup)
- **Total Lines:** ~30,000 (after cleanup)
- **API Endpoints:** 3 controllers (Chat, Search, Knowledge)
- **Tools Available:** 6+ (Search, Index, Graph Query, Dependency, Explain, Generate)
- **Prompt Templates:** 17 YAML files

---

## 📞 Getting Help

If you can't find what you need:

1. **Search this index** for keywords
2. **Check [docs/TODO.md](./docs/TODO.md)** for known issues
3. **Review [application.yml](./src/main/resources/application.yml)** for configuration
4. **Run [diagnose_neo4j.cypher](./diagnose_neo4j.cypher)** for database issues
5. **Check logs:** `target/logs/application.log`

---

## 🎯 Recommended Reading Order

### Day 1: Onboarding
1. EXECUTIVE_SUMMARY.md (5 min)
2. QUICK_START.md (20 min setup)
3. USAGE_GUIDE.md (15 min)
4. Try the system hands-on (30 min)

### Week 1: Deep Dive
5. docs/ARCHITECTURE_COMPLETE.md (2-3 hours, reference)
6. docs/CODING_STANDARDS.md (15 min)
7. docs/PROMPT_CATALOG.md (30 min)
8. IMPLEMENTATION_SUMMARY.md (10 min)

### Week 2: Contributing
9. Review existing code
10. Read relevant sections of ARCHITECTURE_COMPLETE.md as needed
11. Consult CODING_STANDARDS.md before writing code
12. Execute CLEANUP_GUIDE.md cleanup tasks

---

## 📝 Documentation Maintenance

**When adding new features:**
- Update [docs/ARCHITECTURE_COMPLETE.md](./docs/ARCHITECTURE_COMPLETE.md) with new components
- Add new prompts to [docs/PROMPT_CATALOG.md](./docs/PROMPT_CATALOG.md)
- Update [application.yml](./src/main/resources/application.yml) configuration
- Add test cases to [TEST_PLAN.md](./TEST_PLAN.md)

**When fixing bugs:**
- Document the fix in a new `FIX_*.md` file
- Update [docs/TODO.md](./docs/TODO.md) to mark as resolved
- Add diagnostic query to [diagnose_neo4j.cypher](./diagnose_neo4j.cypher) if applicable

**When changing architecture:**
- Update [docs/ARCHITECTURE_COMPLETE.md](./docs/ARCHITECTURE_COMPLETE.md)
- Update [docs/VISUAL_DIAGRAMS.md](./docs/VISUAL_DIAGRAMS.md)
- Update [EXECUTIVE_SUMMARY.md](./EXECUTIVE_SUMMARY.md)

---

## ✅ Documentation Checklist

Before sharing documentation with your team, verify:

- [ ] All services documented in QUICK_START.md are running
- [ ] Configuration in application.yml is up-to-date
- [ ] Obsolete code cleanup (CLEANUP_GUIDE.md) has been executed
- [ ] All diagrams in VISUAL_DIAGRAMS.md are accurate
- [ ] Test plan (TEST_PLAN.md) passes successfully
- [ ] Deployment guide (DEPLOYMENT_GUIDE.md) reflects current setup

---

**Last Updated:** 2026-01-05
**Maintainer:** Development Team
**Version:** 2.0.0

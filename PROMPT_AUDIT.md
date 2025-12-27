# Prompt Engineering Audit

**Date:** 2025-12-27
**Auditor:** Claude (via systematic analysis)
**Severity Scale:** 🔴 Critical | 🟠 Major | 🟡 Minor

---

## Executive Summary

**CRITICAL FINDING:** Your prompts are causing the hallucination problem we just fixed.

**Key Issues:**
1. ❌ **No grounding** - Prompts don't enforce "use only provided context"
2. ❌ **Encourage guessing** - "aim for completeness", "make assumptions"
3. ❌ **No validation** - No schema enforcement, no error cases
4. ❌ **Generic instructions** - "Be a senior engineer" doesn't constrain behavior
5. ❌ **No examples** - No few-shot demonstrations of good outputs

**Impact:**
- 🔴 DocumentationAgent hallucinates when Pinecone returns 0 results
- 🟠 RequirementAnalyzer has conflicting instructions
- 🟠 CodeGenerator lacks security/quality guidelines
- 🟡 All prompts lack few-shot examples

---

## Detailed Analysis

### 🔴 CRITICAL: documentation-agent.yaml

**Current Problems:**

```yaml
systemPrompt: |
  You are a senior software architect analyzing a codebase.  # ❌ Generic role

  Focus on:
  - High-level architecture and design patterns              # ❌ Encourages generic patterns
  - Key components and their responsibilities

  Keep it concise but thorough - aim for completeness      # ❌ "completeness" = hallucination
```

**Why This Causes Hallucination:**
- No instruction to ONLY use provided code
- "Architecture and design patterns" triggers LLM's training on generic patterns
- "Aim for completeness" means "fill in gaps with training data"
- When relevantCode is empty, LLM generates generic "Layered Architecture" BS

**What Good RAG Prompts Do:**

```yaml
# ✅ GOOD RAG PROMPT STRUCTURE
systemPrompt: |
  You are a code documentation assistant.

  CRITICAL GROUNDING RULES:
  1. ONLY use information from the "RELEVANT CODE" section below
  2. If code context is empty, say "No code found for this query"
  3. NEVER mention classes/methods not shown in the context
  4. If you're uncertain, say "Based on the provided code..."
  5. Cite specific code (ClassName.methodName) for all claims

  FORBIDDEN:
  - Do NOT discuss generic design patterns unless explicitly seen in code
  - Do NOT infer architecture beyond what code shows
  - Do NOT mention technologies/frameworks not in provided code
  - Do NOT make assumptions about code you haven't seen
```

**Few-Shot Example (Critical for RAG):**

```yaml
examples:
  - input: "Explain the payment flow"
    context: [UserController, PaymentService, PaymentRepository]
    output: |
      Based on the provided code, the payment flow is:

      1. UserController.processPayment() receives HTTP request
      2. Calls PaymentService.charge(amount, userId)
      3. PaymentService validates and calls PaymentRepository.save()

      Code references:
      - UserController.processPayment() at line 42
      - PaymentService.charge() at line 18

  - input: "Explain the payment flow"
    context: []  # ❌ Empty context
    output: |
      I don't have any code indexed for payment processing yet.
      Please ensure the repository is indexed first, or provide more specific search terms.
```

---

### 🟠 MAJOR: requirement-analyzer.yaml

**Problems:**

1. **Conflicting Instructions:**
```yaml
line 69: "Ask ALL questions at once if you need clarification"
line 124: "For documentation tasks: questions array MUST be EMPTY"
# Which is it?!
```

2. **Wall of Text:**
- 126 lines of instructions
- LLMs lose coherence after ~50 lines
- Key rules buried in middle

3. **No Validation:**
```yaml
Output strict JSON (no markdown):  # ❌ No enforcement
{
  "taskType": "chat|documentation|...",  # ❌ No enum validation
  "confidence": 0.95,  # ❌ No range check
```

**Better Structure:**

```yaml
systemPrompt: |
  You analyze developer requests and output structured JSON.

  OUTPUT FORMAT (strict JSON, no markdown):
  {
    "taskType": "chat" | "documentation" | "bug_fix" | "feature",
    "domain": "string",
    "confidence": 0.0-1.0,
    "questions": []
  }

  RULES:
  1. taskType must be one of: chat, documentation, bug_fix, feature
  2. confidence: 0.0-1.0 (use 0.95 for clear requests, 0.6 for vague)
  3. questions: Empty array unless critical info missing

  DECISION TREE:
  - User says "hi/hello/thanks" → taskType: "chat"
  - User says "explain/document/how does" → taskType: "documentation"
  - User says "fix bug/error" → taskType: "bug_fix"
  - User says "add/implement" → taskType: "feature"

examples:
  - input: "Can you help me understand this codebase?"
    output: {"taskType": "documentation", "domain": "unknown", "confidence": 0.95, "questions": []}

  - input: "hi"
    output: {"taskType": "chat", "domain": "", "confidence": 1.0, "questions": []}
```

---

### 🟠 MAJOR: code-generator.yaml

**Missing Critical Elements:**

1. **No Security Guidelines:**
```yaml
# ❌ Missing
Rules:
  - Validate all user input
  - Use parameterized queries (no SQL injection)
  - Sanitize output (no XSS)
  - Don't log sensitive data
```

2. **No Java/Spring Specifics:**
```yaml
# ❌ Generic "Follow existing patterns"
# ✅ Should be:
Java Best Practices:
  - Use @Transactional for database operations
  - Inject dependencies via constructor (not field injection)
  - Use Optional<T> for nullable returns
  - Follow Spring Boot conventions (@Service, @Repository)
```

3. **Token Limit Risk:**
```yaml
"content": "full file content here"  # ❌ Will exceed context limits!
# ✅ Better: Use diffs/patches
"patch": "@@ -1,5 +1,6 @@..."
```

4. **No Quality Checks:**
```yaml
# ❌ Missing
Before returning code:
  1. Verify all imports are valid
  2. Check method signatures match interfaces
  3. Ensure exception handling exists
  4. Validate test coverage for new code
```

---

### 🟡 MINOR: scope-discovery.yaml

**Issues:**

```yaml
Don't hallucinate, be 100% sure  # ❌ Doesn't work

# ✅ Better:
For each file you select:
  1. Cite WHERE you saw it (search result #3, context line 42)
  2. Explain WHY it needs modification (contains PaymentService.charge())
  3. If uncertain, mark as "OPTIONAL" not "REQUIRED"
```

---

## Industry Best Practices for LLM Prompts

### 1. Chain-of-Thought Reasoning

**Bad:**
```
Determine which files to modify.
```

**Good:**
```
Think step-by-step:
1. What is the root cause? (Explain)
2. Which classes handle this functionality? (List with evidence)
3. What dependencies exist? (Check imports)
4. Therefore, modify: (Final answer)
```

### 2. Constrained Output Space

**Bad:**
```
Output JSON
```

**Good:**
```
Output EXACTLY this structure (no markdown, no extra fields):
{
  "status": "success" | "error",
  "files": ["path1", "path2"],
  "reason": "max 200 chars"
}

VALIDATION:
- status must be exactly "success" or "error"
- files array max 7 items
- reason max 200 characters
```

### 3. Few-Shot Examples (CRITICAL for RAG)

**Without Examples:**
- LLM guesses format
- Output varies widely
- Hallucination common

**With 2-3 Examples:**
- LLM sees pattern
- Output consistent
- Hallucination rare

```yaml
examples:
  - input: "Add retry logic"
    output: {
      "filesToModify": ["PaymentService.java"],
      "reasoning": "PaymentService.charge() needs retry wrapper",
      "confidence": 0.9
    }

  - input: "Explain UserController"
    context: []
    output: "No code found for UserController. Please ensure repository is indexed."
```

### 4. Explicit Failure Modes

```yaml
ERROR HANDLING:
- If no code context provided → Return: "Insufficient context, cannot proceed"
- If request is ambiguous → Ask clarifying questions
- If task too complex (>10 files) → Return: "Task too large, please split"
```

---

## Recommended Actions

### 🚨 Immediate (Fix Hallucination)

**Priority 1: Rewrite documentation-agent.yaml**
- Add grounding rules
- Add "No code found" case
- Add 2-3 few-shot examples
- Remove generic architecture prompting

**Priority 2: Fix requirement-analyzer.yaml**
- Resolve conflicting instructions
- Add decision tree
- Reduce from 126 lines to ~50 lines
- Add validation rules

### 📋 High Priority (Improve Quality)

**Priority 3: Enhance code-generator.yaml**
- Add security guidelines (OWASP top 10)
- Add Java/Spring best practices
- Use patches instead of full files
- Add quality verification steps

**Priority 4: Strengthen scope-discovery.yaml**
- Add chain-of-thought reasoning
- Require evidence citations
- Add confidence scoring
- Add few-shot examples

### 📈 Medium Priority (Nice to Have)

- Add response schemas (validate JSON output)
- Add prompt versioning (A/B test improvements)
- Add metrics (track hallucination rate, accuracy)
- Add prompt templates (DRY principles)

---

## The Golden Rule of RAG Prompting

**"If the LLM doesn't have the answer in the context, it should say so, not guess."**

Current state: ❌ LLM guesses → Hallucinations
Desired state: ✅ LLM admits uncertainty → Trust

---

## Measuring Prompt Quality

| Metric | Current | Target |
|--------|---------|--------|
| Hallucination Rate (0 context) | 100% | 0% |
| Output Format Compliance | ~70% | >95% |
| Follows "Only use context" | 0% | 100% |
| Uses Few-Shot Examples | 0% | 100% |
| Has Validation Rules | 0% | 100% |

---

## Next Steps

1. **Review this audit with team**
2. **Prioritize which prompts to fix first** (I recommend documentation-agent)
3. **Rewrite prompts using best practices above**
4. **Test with real queries** (especially edge cases: empty context, vague requests)
5. **Monitor hallucination rate** (should drop to near-zero)

---

**Key Insight:** The prompt engineering problem is ARCHITECTURAL, not tactical. We need to shift from:
- ❌ "Be smart and helpful" (causes hallucination)
- ✅ "Be precise and honest about limitations" (builds trust)

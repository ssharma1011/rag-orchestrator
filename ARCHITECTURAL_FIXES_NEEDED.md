# Critical Architectural Issues & Fixes Needed

## Issue #1: Static Prompts vs Adaptive Conversation ⚠️ **CRITICAL**

### Problem
User's insight: "when chatting with gemini/claude/chatgpt, prompts change with each response making things clear... but here the prompts are strictly written"

**Current Flow:**
1. LLM asks question → stores in `RequirementAnalysis.questions`
2. User answers → stored in `WorkflowState.conversationHistory`
3. Workflow re-runs RequirementAnalyzer with **SAME STATIC PROMPT**
4. LLM has no memory of previous exchange → **asks same question again**

### Root Cause
```java
// RequirementAnalyzerAgent.java:152
String prompt = promptLibrary.render("requirement-analyzer", Map.of(
    "requirement", userPrompt,
    "conversationHistory", formatHistory(state.getConversationHistory()) // ← This is passed BUT...
));
```

**The template doesn't use conversationHistory correctly!**

### Fix Needed
**Option A: Append Previous Q&A to Prompt**
```handlebars
{{#if conversationHistory}}
**PREVIOUS CONVERSATION:**
{{#each conversationHistory}}
{{role}}: {{content}}
{{/each}}

CRITICAL: The user already answered your questions above.
DO NOT ask them again. Proceed with what you know.
{{/if}}
```

**Option B: Use Structured History**
```java
if (!state.getConversationHistory().isEmpty()) {
    StringBuilder previousContext = new StringBuilder();
    previousContext.append("\n**CONTEXT FROM PREVIOUS CONVERSATION:**\n");

    for (ChatMessage msg : state.getConversationHistory()) {
        if ("assistant".equals(msg.getRole()) && msg.getContent().contains("questions")) {
            previousContext.append("You previously asked: ").append(msg.getContent()).append("\n");
        } else if ("user".equals(msg.getRole())) {
            previousContext.append("User answered: ").append(msg.getContent()).append("\n");
        }
    }

    promptData.put("previousContext", previousContext.toString());
}
```

---

## Issue #2: Empty Purpose & Dependencies in Neo4j

### Problem
Scope Discovery prompt shows:
```
- GeminiClient
  Purpose:
  Dependencies:
```

### Root Cause
```java
// ScopeDiscoveryAgent.java:274-275
data.put("purpose", node.getSummary() != null ? node.getSummary() : "");  // ← summary is null!
data.put("dependencies", node.getDomain() != null ? node.getDomain() : ""); // ← domain is null!
```

**Why?** `EntityExtractor` doesn't populate these fields during code parsing.

### Fix Needed

**EntityExtractor.java should extract:**
1. **Purpose**: Javadoc summary or inferred from class/method name
2. **Dependencies**: Direct imports/dependencies used

```java
// In EntityExtractor.extractEntities()
private String inferPurpose(ClassOrInterfaceDeclaration classDecl) {
    // 1. Try Javadoc
    if (classDecl.getJavadocComment().isPresent()) {
        JavadocComment javadoc = classDecl.getJavadocComment().get();
        return javadoc.parse().getDescription().toText();
    }

    // 2. Infer from annotations
    if (classDecl.isAnnotationPresent("Service")) {
        return "Service component handling business logic";
    } else if (classDecl.isAnnotationPresent("Controller")) {
        return "REST API controller";
    } else if (classDecl.isAnnotationPresent("Repository")) {
        return "Data access repository";
    }

    // 3. Infer from name
    String name = classDecl.getNameAsString();
    if (name.endsWith("Agent")) {
        return "Workflow agent for " + name.replace("Agent", "").toLowerCase();
    } else if (name.endsWith("Client")) {
        return "API client for external service";
    }

    return "";
}
```

---

## Issue #3: Full Re-index on Pinecone 502 Error

### Problem
```
ERROR: Failed to fetch last indexed commit: 502 Server Error
INFO: No previous index found. Performing INITIAL FULL INDEX.
```

**A temporary Pinecone outage triggers full re-index!**

### Fix Needed

**Add retry + local caching:**

```java
// IncrementalEmbeddingSyncServiceImpl.java
private String getLastIndexedCommit(String repoName) {
    // 1. Try Pinecone (with retries)
    for (int attempt = 1; attempt <= 3; attempt++) {
        try {
            var response = pineconeClient.getIndexConnection(indexName)
                    .fetch(List.of(metadataId), "");

            if (response != null && !response.getVectorsMap().isEmpty()) {
                var vector = response.getVectorsMap().get(metadataId);
                if (vector != null) {
                    var metadata = vector.getMetadata().getFieldsMap();
                    if (metadata.containsKey("last_indexed_commit")) {
                        String commit = metadata.get("last_indexed_commit").getStringValue();

                        // Cache locally for resilience
                        cacheLastIndexedCommit(repoName, commit);

                        return commit;
                    }
                }
            }
        } catch (PineconeUnmappedHttpException e) {
            if (e.getMessage().contains("502")) {
                log.warn("⚠️ Pinecone 502 error (attempt {}), retrying in {}s...", attempt, attempt * 5);
                Thread.sleep(attempt * 5000); // 5s, 10s, 15s
                continue;
            }
            throw e;
        }
    }

    // 2. Fall back to local cache (DB)
    String cachedCommit = getCachedLastIndexedCommit(repoName);
    if (cachedCommit != null) {
        log.info("✅ Using cached last indexed commit: {}", cachedCommit);
        return cachedCommit;
    }

    // 3. Only NOW assume first index
    log.info("📥 No previous index found (Pinecone unavailable + no cache)");
    return null;
}

// Store in Oracle as fallback
@Transactional
private void cacheLastIndexedCommit(String repoName, String commit) {
    jdbcTemplate.update(
        "MERGE INTO index_state (repo_name, last_commit, updated_at) " +
        "VALUES (?, ?, SYSTIMESTAMP)",
        repoName, commit
    );
}
```

---

## Issue #4: Conversation Tables Empty

### Why?
`ConversationContext` repository exists but is **never used**. Conversation is stored in:
```
workflow_states.conversation_history (JSON column)
```

### Decision Needed
**Option A:** Remove `conversation_context` table (unused)
**Option B:** Actually use it to store messages separately

For now, query the correct table:
```sql
SELECT conversation_history FROM workflow_states
WHERE workflow_id = '58923a4e-d81a-4dd1-9121-683284ce35bb';
```

---

## Issue #5: 429 Rate Limiting (Still)

Your logs show the issue persists. Solutions:

1. **Use Gemini Pro** (paid tier): 1000 RPM vs 15 RPM
2. **Add delays between agents**: Sleep 5 seconds between LLM calls
3. **Cache LLM responses**: If same prompt sent twice, return cached response

---

## Priority Fixes

### P0 (Blocking)
1. ✅ **Fix conversational prompts** - Add previous Q&A to prompt context
2. ✅ **Fix Pinecone retry** - Add retry logic + local cache for metadata

### P1 (High Impact)
3. **Populate Neo4j purpose/dependencies** - Extract from Javadoc/annotations
4. **Rate limit handling** - Add delays or upgrade API tier

### P2 (Nice to have)
5. **Clean up conversation tables** - Remove unused or actually use them


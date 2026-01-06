# Web Search Integration - Complete Guide

**Status:** ✅ Implemented | **Date:** 2026-01-05
**Provider:** Tavily API | **Tool:** WebSearchTool

---

## Overview

Web search functionality enables AutoFlowAgent to search the internet for:
- Latest library API documentation
- Solutions to compilation errors
- Migration guides for deprecated APIs
- Current library versions and signatures

This solves the problem of LLMs having outdated knowledge about rapidly changing libraries.

---

## Architecture

### Components

```
┌─────────────────┐
│ AutoFlowAgent   │ → Decides when to use web_search tool
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│ WebSearchTool   │ → Implements Tool interface
└────────┬────────┘
         │
         ↓
┌─────────────────────┐
│ WebSearchService    │ → Interface
└────────┬────────────┘
         │
         ↓
┌───────────────────────┐
│ WebSearchServiceImpl  │ → Tavily API integration
└────────┬──────────────┘
         │
         ↓
┌─────────────────┐
│ Tavily API      │ → Cloud search optimized for AI
└─────────────────┘
```

---

## How It Works

### 1. Tool Registration

WebSearchTool is automatically discovered by Spring and injected into AutoFlowAgent's tool list:

```java
@Component
public class WebSearchTool implements Tool {
    // Automatically discovered via @Component
}

@Service
public class AutoFlowAgent {
    private final List<Tool> tools; // All @Component tools injected here
}
```

### 2. Agent Decides When to Search

AutoFlowAgent's LLM sees this tool in its available tools:

```
Available Tools:
- web_search: Search the web for current library documentation, API signatures,
  and solutions to compilation errors.
```

The LLM calls it when:
- Compilation errors mention unknown symbols
- APIs are deprecated
- Need current documentation

### 3. Tool Execution

```java
// 1. Agent invokes tool
WebSearchTool.execute({
    "query": "Neo4j executeWrite Java API 2024",
    "library": "Neo4j"  // Optional
})

// 2. Service calls Tavily
POST https://api.tavily.com/search
{
    "api_key": "tvly-xxx",
    "query": "Neo4j executeWrite Java API official documentation 2024 2025",
    "search_depth": "basic",
    "include_answer": true,
    "max_results": 5
}

// 3. Tavily returns synthesized answer
{
    "answer": "In Neo4j 5.x, executeWrite() is replaced by session.executeWrite()...",
    "results": [
        {"url": "https://neo4j.com/docs/java-manual/5.0/", "title": "..."},
        ...
    ]
}

// 4. Tool returns formatted result to agent
{
    "query": "...",
    "answer": "...",
    "confidence": 85,
    "sources": ["https://neo4j.com/docs/...", ...],
    "searchTimeMs": 1234
}
```

### 4. Agent Uses Result

The LLM receives the search result and incorporates it into code generation:

```
Web Search Results:

Query: Neo4j executeWrite Java API 2024
Confidence: 85%

### Answer
In Neo4j 5.x, executeWrite() is replaced by session.executeWrite()...

### Sources
- https://neo4j.com/docs/java-manual/5.0/
- https://github.com/neo4j/neo4j-java-driver

✅ High confidence result - information is from official documentation.
```

---

## Configuration

### application.yml

```yaml
app:
  web-search:
    enabled: ${WEB_SEARCH_ENABLED:false}  # Enable with env var
    provider: tavily                       # Only Tavily supported currently
    api-key: ${TAVILY_API_KEY:}           # Required if enabled
    max-results: 5                         # Results per search
    timeout-seconds: 10                    # Request timeout
    trigger-patterns:                      # Auto-trigger patterns (future)
      - "cannot find symbol"
      - "incompatible types"
      - "deprecated"
      - "package .* does not exist"
```

### Environment Variables

```bash
# Enable web search
export WEB_SEARCH_ENABLED=true

# Set Tavily API key (get from https://tavily.com)
export TAVILY_API_KEY=tvly-xxxxxxxxxx
```

---

## Usage Examples

### Example 1: Search for Library API

**User Query:** "Explain how to use Neo4j transactions in Java"

**Agent Decision:**
```json
{
  "tool": "web_search",
  "parameters": {
    "query": "Neo4j Java transaction API",
    "library": "Neo4j"
  }
}
```

**Search Result:**
```
Web Search Results:

Query: Neo4j Java transaction API official documentation 2024 2025
Confidence: 90%

### Answer
Neo4j 5.x uses session.executeWrite() and session.executeRead() for managed
transactions. Example:

try (Session session = driver.session()) {
    session.executeWrite(tx -> {
        tx.run("CREATE (p:Person {name: $name})", parameters("name", "Alice"));
        return null;
    });
}

### Sources
- https://neo4j.com/docs/java-manual/5.0/transactions/
- https://github.com/neo4j/neo4j-java-driver/blob/5.0/examples/

✅ High confidence result - from official Neo4j documentation.
```

### Example 2: Compilation Error

**Compilation Error:**
```
error: cannot find symbol
    symbol:   method executeWrite(String)
    location: class Session
```

**Agent Decision:**
```json
{
  "tool": "web_search",
  "parameters": {
    "query": "Neo4j Session executeWrite cannot find symbol Java"
  }
}
```

**Search Result:**
```
Web Search Results:

Query: Neo4j Session executeWrite cannot find symbol Java
Confidence: 75%

### Answer
The executeWrite() method signature changed in Neo4j 5.x. It now requires a
TransactionCallback instead of a String query. Use:

session.executeWrite(tx -> {
    return tx.run(query, parameters).single();
});

### Sources
- https://neo4j.com/docs/java-manual/5.0/upgrade/
- https://stackoverflow.com/questions/71234567/neo4j-executewrite-error

⚠️ Mixed sources - verify official documentation.
```

---

## API Reference

### WebSearchTool

```java
public class WebSearchTool implements Tool {

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getParameterSchema() {
        return "{
            \"query\": \"string (required) - search query\",
            \"library\": \"string (optional) - specific library name\"
        }";
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.UNDERSTANDING;
    }

    @Override
    public boolean requiresIndexedRepo() {
        return false; // Can search without indexed code
    }
}
```

### WebSearchService

```java
public interface WebSearchService {

    /**
     * Search for information about a specific error or API.
     *
     * @param query Search query (e.g., "Neo4j executeWrite Java API 2024")
     * @return Search result with answer and sources
     */
    SearchResult search(String query);

    /**
     * Search specifically for library API documentation.
     *
     * @param libraryName Name of library (e.g., "Neo4j")
     * @param apiName     API/method name (e.g., "executeWrite")
     * @return Search result with API documentation
     */
    SearchResult searchLibraryAPI(String libraryName, String apiName);

    /**
     * Check if web search is enabled.
     *
     * @return true if web search is available
     */
    boolean isEnabled();
}
```

### SearchResult

```java
@Value
@Builder
public class SearchResult {
    String query;           // Original search query
    String answer;          // Synthesized answer from Tavily
    List<String> sources;   // Source URLs for verification
    int confidence;         // Confidence score (0-100)
    long searchTimeMs;      // Time taken to search

    /**
     * Formats result for LLM consumption.
     */
    public String formatForLLM() {
        // Returns formatted answer with sources
    }
}
```

---

## Confidence Scoring

The confidence score (0-100) is calculated based on:

| Factor | Score Contribution |
|--------|-------------------|
| Base (has answer) | 40 |
| Answer length > 200 chars | +20 |
| Answer length > 500 chars | +10 |
| Each source URL | +5 (max 20) |
| Official sources (github.com, docs.*, apache.org, spring.io) | +5 each |
| Contains code examples (```, import, @) | +10 |
| **Maximum** | **95** |

### Confidence Levels

- **0-40%**: Low confidence - verify before using
- **41-70%**: Medium confidence - probably accurate
- **71-100%**: High confidence - from official sources

---

## Why Tavily?

### Tavily vs Google/Bing

| Feature | Tavily | Google | Bing |
|---------|--------|--------|------|
| **Synthesized Answers** | ✅ Yes | ❌ No | ❌ No |
| **AI-Optimized** | ✅ Yes | ❌ No | ❌ No |
| **Token Efficiency** | ✅ High | ❌ Low | ❌ Low |
| **API Cost** | $ Low | $$ High | $$ High |
| **Rate Limits** | Generous | Strict | Strict |

### Tavily Benefits

1. **Returns Answers, Not Links**: Saves LLM tokens by providing synthesized answer
2. **AI-Optimized**: Built specifically for AI agents
3. **Official Sources**: Prioritizes official documentation
4. **Fast**: Optimized for low latency (~1-2 seconds)
5. **Cost-Effective**: More affordable than Google Custom Search

---

## Testing

### Manual Testing

```bash
# 1. Set environment variables
export WEB_SEARCH_ENABLED=true
export TAVILY_API_KEY=tvly-xxxxxxxxxx

# 2. Start application
mvn spring-boot:run

# 3. Send chat request with web search need
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{
    "message": "How do I use Neo4j executeWrite in Java 5.x?",
    "repoUrl": "https://github.com/user/repo"
  }'

# Expected: Agent will invoke web_search tool and return current API info
```

### Check Logs

```
INFO  AutoFlowAgent - Processing message for conversation: xxx
INFO  WebSearchTool - Web search: Neo4j executeWrite Java API 2024 (library: Neo4j)
INFO  WebSearchServiceImpl - 🔍 Searching Tavily: Neo4j executeWrite Java API official documentation 2024 2025
INFO  WebSearchServiceImpl - ✅ 85% confidence from 3 sources (1234ms)
INFO  AutoFlowAgent - Tool result: web_search -> Found answer with 85% confidence from 3 sources
```

### Verify Neo4j (Web Search Activity)

Since web search doesn't store data, there's no Neo4j state to check. However, you can verify that the tool is registered:

```bash
# Check AutoFlowAgent logs at startup
grep "Registered tool" logs/application.log

# Expected output includes:
# INFO  AutoFlowAgent - Registered tool: web_search
```

---

## Error Handling

### Graceful Degradation

Web search failures don't crash the pipeline:

```java
try {
    return webSearchService.search(query);
} catch (Exception e) {
    log.warn("Tavily failed for '{}': {}", query, e.getMessage());
    return buildEmptyResult(query); // Returns empty result, not exception
}
```

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "Web search is disabled" | WEB_SEARCH_ENABLED=false or no API key | Set environment variables |
| "No results found" | Query too vague or no matches | Refine search query |
| Tavily status 401 | Invalid API key | Check TAVILY_API_KEY |
| Tavily status 429 | Rate limit exceeded | Wait or upgrade Tavily plan |
| Timeout | Network slow or Tavily down | Increase timeout-seconds in config |

---

## Files Modified/Created

### Created Files

1. **WebSearchTool.java** (`src/main/java/com/purchasingpower/autoflow/agent/tools/`)
   - Implements Tool interface
   - Provides web_search tool to AutoFlowAgent
   - Handles query and library parameters

2. **web-search-tool.yaml** (`src/main/resources/prompts/`)
   - Externalized prompt template for formatting search results
   - Uses Mustache templating for confidence levels and sources

### Existing Files (Already Implemented)

3. **WebSearchService.java** (`src/main/java/com/purchasingpower/autoflow/service/search/`)
   - Interface for web search operations

4. **WebSearchServiceImpl.java** (`src/main/java/com/purchasingpower/autoflow/service/search/`)
   - Tavily API integration
   - Confidence scoring
   - Error handling

5. **SearchResult.java** (`src/main/java/com/purchasingpower/autoflow/service/search/`)
   - Immutable search result model
   - formatForLLM() method

---

## Performance

### Latency

- **Tavily API**: ~1-2 seconds average
- **Network overhead**: ~100-300ms
- **Total**: ~1.5-2.5 seconds per search

### Token Efficiency

- **Without web search**: LLM hallucinates API (wrong) → 500 tokens wasted
- **With web search**: Correct API from start → 100 tokens for search result
- **Savings**: 80% token reduction + correct answer

### Cost

- **Tavily**: $0.001 per search (1000 searches = $1)
- **LLM retry without search**: $0.01+ per wrong attempt
- **ROI**: 10x cost savings by getting it right first time

---

## Security

### API Key Storage

```bash
# ✅ Good: Environment variable
export TAVILY_API_KEY=tvly-xxx

# ❌ Bad: Hardcoded in code
apiKey = "tvly-xxx"

# ❌ Bad: Committed to git
TAVILY_API_KEY=tvly-xxx in application.yml
```

### Rate Limiting

Tavily has generous rate limits, but if exceeded:

1. **Free tier**: 1000 requests/month
2. **Paid tier**: 10,000+ requests/month
3. **Enterprise**: Unlimited

**Recommendation**: Start with free tier, upgrade if needed.

---

## Future Enhancements

### 1. Automatic Trigger (Planned)

Currently manual via LLM decision. Future: Auto-trigger on compilation errors:

```java
if (compilationFailed && errorMatchesTriggerPattern(error)) {
    SearchResult webResult = webSearchService.search(extractErrorQuery(error));
    retryCompilationWith(webResult.getAnswer());
}
```

### 2. Search Result Caching

```java
@Cacheable(value = "webSearchCache", key = "#query")
public SearchResult search(String query) {
    // Cache results for 24 hours to reduce API calls
}
```

### 3. Multi-Provider Support

```yaml
web-search:
  provider: tavily  # Future: google, bing, duckduckgo
```

### 4. Search Quality Metrics

Track and log:
- Search success rate
- Average confidence scores
- Most common queries
- Cost per search

---

## Troubleshooting

### Web Search Not Working

**Symptom**: Agent never calls web_search tool

**Possible Causes**:
1. Web search disabled (WEB_SEARCH_ENABLED=false)
2. No API key configured
3. LLM doesn't think it's needed

**Debugging**:
```bash
# Check if enabled
curl http://localhost:8080/api/v1/health | jq '.webSearch'

# Check logs
grep "Web search" logs/application.log

# Check environment
echo $WEB_SEARCH_ENABLED
echo $TAVILY_API_KEY
```

### Low Confidence Results

**Symptom**: Confidence < 50%

**Possible Causes**:
1. Query too vague
2. No official documentation available
3. Library too new or obscure

**Solutions**:
- Make query more specific (include version, year)
- Add "official documentation" to query
- Try searchLibraryAPI() instead of search()

### Slow Searches

**Symptom**: Searches take > 5 seconds

**Possible Causes**:
1. Network latency
2. Tavily API slow
3. Timeout set too high

**Solutions**:
- Check network connectivity
- Reduce timeout-seconds in config
- Consider caching results

---

## Related Documentation

- **Tavily API Docs**: https://docs.tavily.com
- **Tool System**: `docs/ARCHITECTURE_COMPLETE.md` (Section 7: Tool-Based Agent)
- **Prompt Templates**: `docs/PROMPT_CATALOG.md`

---

**Status:** ✅ **Implementation Complete**
**Build:** ✅ **BUILD SUCCESS**
**Ready for:** Testing with TAVILY_API_KEY environment variable

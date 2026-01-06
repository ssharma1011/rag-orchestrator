# Context-Aware Tools - Quick Start Guide

## 🎯 **What Changed?**

Tools are now **smart** - they remember what they did before and adapt based on user feedback.

---

## 🚀 **For Developers: Adding a New Context-Aware Tool**

### **Step 1: Make Your Tool Context-Aware**

```java
@Component
public class MyNewTool implements Tool {

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        // 1. Check execution history
        int executionCount = context.getToolExecutionCount("my_new_tool");
        boolean needsBetter = context.hasNegativeFeedback();

        // 2. Adapt behavior
        if (executionCount > 0 && needsBetter) {
            log.info("User wants better results - using enhanced mode");
            return executeEnhancedMode(parameters, context);
        }

        // 3. Normal execution
        ToolResult result = executeNormalMode(parameters, context);

        // 4. Record execution
        context.recordToolExecution("my_new_tool", result, null);

        return result;
    }

    private ToolResult executeNormalMode(Map<String, Object> params, ToolContext ctx) {
        // Basic implementation
    }

    private ToolResult executeEnhancedMode(Map<String, Object> params, ToolContext ctx) {
        // More detailed/different approach
    }
}
```

---

### **Step 2: Register Alternative Tools**

Add your tool to the alternatives map in `AutoFlowAgent.java`:

```java
private List<String> getAlternativeTools(String toolName) {
    return switch (toolName) {
        case "my_new_tool" -> List.of("search_code", "graph_query");
        // ... existing mappings ...
        default -> List.of();
    };
}
```

---

## 📚 **ToolContext API Reference**

### **Tracking Methods**

```java
// Record that your tool executed
context.recordToolExecution("tool_name", result, "optional_feedback");

// How many times has this tool run in this conversation?
int count = context.getToolExecutionCount("tool_name");

// Does user want better/more detailed results?
boolean needsBetter = context.hasNegativeFeedback();

// What did this tool return last time?
Object lastResult = context.getLastToolResult("tool_name");
```

### **Storage Methods**

```java
// Store custom data for later use
context.setVariable("my_custom_key", value);

// Retrieve custom data
Object value = context.getVariable("my_custom_key");

// Access conversation history
Conversation conv = context.getConversation();
List<ConversationMessage> messages = conv.getMessages();
```

---

## 🎭 **Example Patterns**

### **Pattern 1: Progressive Detail**

Start simple, add detail with each iteration:

```java
if (executionCount == 0) {
    return getBasicInfo();
} else if (executionCount == 1) {
    return getDetailedInfo();
} else {
    return getExhaustiveInfo();
}
```

### **Pattern 2: Alternative Approaches**

Try different strategies based on feedback:

```java
if (needsBetter) {
    // User didn't like keyword search
    return semanticSearch(query);
} else {
    return keywordSearch(query);
}
```

### **Pattern 3: Query Expansion**

Generate variations to cast a wider net:

```java
List<String> queries = new ArrayList<>();
queries.add(originalQuery);

if (needsBetter) {
    queries.add(originalQuery + "Impl");
    queries.add(originalQuery + "Service");
    queries.addAll(generateSynonyms(originalQuery));
}

// Search with all variations and merge
```

### **Pattern 4: Caching Previous Results**

Avoid redundant work:

```java
Object cached = context.getLastToolResult("my_tool");
if (cached != null && !needsBetter) {
    log.info("Returning cached result");
    return (ToolResult) cached;
}

// Fetch fresh data
```

---

## 🔍 **Debugging Tips**

### **Enable Debug Logging**

```yaml
# application.yml
logging:
  level:
    com.purchasingpower.autoflow.agent.tools: DEBUG
    com.purchasingpower.autoflow.agent.impl: DEBUG
```

### **Check Tool Execution Logs**

```
2026-01-06 11:45:30 INFO  [DiscoverProjectTool] - Using DEEP mode (execution #2, feedback=true)
2026-01-06 11:45:31 INFO  [SearchTool] - Searching with 5 queries (execution #1, feedback=false): [auth, authImpl, ...]
2026-01-06 11:45:32 INFO  [AutoFlowAgent] - User wants better results - trying alternative tools: [search_code, dependency_analysis]
```

### **Inspect Context Variables**

```java
// In your tool
Map<String, Object> history = (Map) context.getVariable("tool_execution_history");
log.debug("Execution history: {}", history);
```

---

## 🧪 **Testing Your Context-Aware Tool**

### **Unit Test Template**

```java
@Test
public void testToolAdaptsToBehavior() {
    // Setup
    Conversation conv = createTestConversation();
    ToolContextImpl context = ToolContextImpl.create(conv);

    // First execution
    ToolResult result1 = myTool.execute(params, context);
    assertEquals("NORMAL mode", result1.getData().get("mode"));

    // Simulate user wanting better results
    addUserMessage(conv, "Give me a better answer");

    // Second execution - should use enhanced mode
    ToolResult result2 = myTool.execute(params, context);
    assertEquals("ENHANCED mode", result2.getData().get("mode"));
}

private void addUserMessage(Conversation conv, String message) {
    conv.getMessages().add(ConversationMessage.builder()
        .role("user")
        .content(message)
        .build());
}
```

---

## 🎓 **Common Pitfalls**

### ❌ **DON'T: Forget to record execution**

```java
ToolResult result = execute();
return result;  // ← WRONG! Context doesn't learn
```

### ✅ **DO: Always record**

```java
ToolResult result = execute();
context.recordToolExecution(getName(), result, null);
return result;  // ← CORRECT!
```

---

### ❌ **DON'T: Infinite recursion with alternatives**

```java
// In AutoFlowAgent
private ToolResult executeTool(...) {
    ToolResult result = tool.execute();
    return augmentWithAlternatives(result);  // ← Calls executeTool() again!
}
```

### ✅ **DO: Use direct execution for alternatives**

```java
private ToolResult augmentWithAlternatives(...) {
    ToolResult altResult = executeToolDirectly(altTool);  // ← No augmentation
}
```

---

### ❌ **DON'T: Hardcode feedback detection**

```java
if (userMessage.equals("better")) {  // ← Too specific!
    useEnhancedMode();
}
```

### ✅ **DO: Use context.hasNegativeFeedback()**

```java
if (context.hasNegativeFeedback()) {  // ← Handles many variations
    useEnhancedMode();
}
```

---

## 📊 **Monitoring in Production**

### **Key Metrics to Track**

1. **Tool Execution Counts**
   - Which tools run most frequently?
   - Which require multiple iterations?

2. **Mode Distribution**
   - How often is DEEP/ENHANCED mode used?
   - Indicates user satisfaction

3. **Alternative Tool Triggers**
   - How often do alternatives execute?
   - Which combinations work best?

### **Log Analysis Queries**

```bash
# Count tool executions by mode
grep "Using .* mode" logs/app.log | cut -d' ' -f4 | sort | uniq -c

# Find conversations with negative feedback
grep "hasNegativeFeedback.*true" logs/app.log

# See which alternatives are triggered most
grep "trying alternative tools" logs/app.log | cut -d':' -f2 | sort | uniq -c
```

---

## 🔗 **Related Documentation**

- **Full Implementation**: `CONTEXT_AWARE_TOOLS_IMPLEMENTATION.md`
- **Architecture**: `docs/ARCHITECTURE_COMPLETE.md`
- **Tool Interface**: `src/main/java/com/purchasingpower/autoflow/agent/Tool.java`
- **Context Interface**: `src/main/java/com/purchasingpower/autoflow/agent/ToolContext.java`

---

## 💡 **Pro Tips**

1. **Start Simple**: Make your tool work normally first, add context-awareness second
2. **Log Everything**: Debug mode is your friend - log execution count, feedback, mode selection
3. **Test Edge Cases**: What if tool runs 10 times? What if feedback is unclear?
4. **Fail Gracefully**: If enhanced mode fails, fall back to normal mode
5. **Document Modes**: Clearly explain what each mode does in JavaDoc

---

## 🤝 **Need Help?**

- Check the example implementations:
  - `DiscoverProjectTool.java` - 3 modes (Normal/Deep/Expanded)
  - `SearchTool.java` - Query refinement
  - `AutoFlowAgent.java` - Alternative tool chaining

- Review the context tracking:
  - `ToolContextImpl.java` - Execution history storage

---

**Happy Coding! 🚀**

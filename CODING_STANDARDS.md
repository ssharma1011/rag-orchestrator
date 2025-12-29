# Coding Standards for AI-Generated Code

## Purpose
These standards ensure AI-generated code is maintainable, readable, and debuggable by human developers.

---

## 1. Package Structure

```
com.purchasingpower.autoflow/
├── agent/               # All workflow agents (interface + impl)
├── client/              # External API clients (Gemini, Pinecone)
├── config/              # Spring configuration classes
├── exception/           # Custom exceptions (specific, not generic)
├── model/               # Data models (DTOs, entities)
├── repository/          # Database repositories
├── service/             # Business logic services (interface + impl)
│   ├── validation/      # Validation services
│   ├── compilation/     # Code compilation services
│   └── search/          # Web search services
├── util/                # Utilities (static methods only)
└── workflow/            # Workflow orchestration
```

---

## 2. Interface + Implementation Pattern

**ALWAYS** create interface + implementation:

```java
// AgentExecutor.java (interface)
public interface AgentExecutor {
    /**
     * Executes agent logic and returns workflow updates.
     *
     * @param state Current workflow state
     * @return Map of state updates
     * @throws AgentExecutionException if execution fails
     */
    Map<String, Object> execute(WorkflowState state);
}

// AgentExecutorImpl.java (implementation)
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentExecutorImpl implements AgentExecutor {

    private final DependencyA dependencyA;
    private final DependencyB dependencyB;

    @Override
    public Map<String, Object> execute(WorkflowState state) {
        log.info("Executing agent for conversation: {}", state.getConversationId());
        // Implementation
    }
}
```

---

## 3. Method Complexity Limits

- **Max lines per method:** 20
- **Max cyclomatic complexity:** 10
- **Max parameters:** 4 (use parameter objects if more needed)
- **Max nesting depth:** 3

---

## 4. No If-Else Chains - Use Strategy Pattern

**BAD:**
```java
if (type == Type.A) {
    // 20 lines
} else if (type == Type.B) {
    // 20 lines
} else if (type == Type.C) {
    // 20 lines
}
```

**GOOD:**
```java
public interface ProcessingStrategy {
    void process(Context context);
}

@Component
public class StrategyRouter {
    private final Map<Type, ProcessingStrategy> strategies;

    public void route(Type type, Context context) {
        strategies.get(type).process(context);
    }
}
```

---

## 5. Comments - WHY, Not WHAT

**BAD:**
```java
// Get the user
User user = userRepository.findById(id);

// Check if user is null
if (user == null) {
    throw new Exception();
}
```

**GOOD:**
```java
// Users can be soft-deleted, so we must verify existence before proceeding
// to avoid NullPointerException in downstream billing calculations
User user = userRepository.findById(id)
    .orElseThrow(() -> new UserNotFoundException(id));
```

---

## 6. Logging Standards

```java
// Entry point
log.info("Starting code generation for requirement: {}", requirement);

// Significant steps
log.debug("Compilation succeeded on attempt {}", attemptNumber);

// Warnings
log.warn("Low confidence ({}%) on API correctness, triggering web search", confidence);

// Errors (with context)
log.error("Failed to compile generated code after {} attempts. Error: {}",
          maxAttempts, errorMessage, exception);
```

---

## 7. Exception Handling

**Create specific exceptions:**
```java
public class CompilationFailedException extends RuntimeException {
    private final String generatedCode;
    private final List<CompilationError> errors;

    public CompilationFailedException(String code, List<CompilationError> errors) {
        super(buildMessage(errors));
        this.generatedCode = code;
        this.errors = errors;
    }
}
```

**Never catch generic Exception unless absolutely necessary:**
```java
// BAD
try {
    compile();
} catch (Exception e) {
    log.error("Error", e);
}

// GOOD
try {
    compile();
} catch (CompilationException e) {
    log.error("Compilation failed: {}", e.getErrors());
    throw new CodeGenerationException("Cannot generate compilable code", e);
}
```

---

## 8. No Null - Use Optional

```java
// BAD
public String getName() {
    return name; // Can return null
}

// GOOD
public Optional<String> getName() {
    return Optional.ofNullable(name);
}
```

---

## 9. Externalize All Configuration

```yaml
# application.yml
code-quality:
  max-complexity: 10
  max-method-lines: 20
  compilation:
    max-attempts: 3
    timeout-seconds: 30
  web-search:
    enabled: true
    provider: tavily
    api-key: ${TAVILY_API_KEY}
```

```java
@ConfigurationProperties("code-quality")
@Data
public class CodeQualityConfig {
    private int maxComplexity = 10;
    private int maxMethodLines = 20;
    private CompilationConfig compilation;
    private WebSearchConfig webSearch;
}
```

---

## 10. Proper Javadoc

**For public interfaces:**
```java
/**
 * Validates generated code against quality standards.
 *
 * <p>This validator enforces:
 * <ul>
 *   <li>Cyclomatic complexity limits
 *   <li>Method length restrictions
 *   <li>No hardcoded strings in business logic
 * </ul>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe and can be used
 * concurrently from multiple workflows.
 *
 * @author AutoFlow Pipeline
 * @since 1.0.0
 */
public interface CodeValidator {

    /**
     * Validates the generated code.
     *
     * @param code The Java code to validate
     * @return Validation result with any violations found
     * @throws IllegalArgumentException if code is null or empty
     */
    ValidationResult validate(String code);
}
```

---

## 11. Package-Private Helper Classes

```java
// Only expose public interface
public interface DataProcessor {
    ProcessResult process(Data data);
}

// Implementation can use package-private helpers
@Service
class DataProcessorImpl implements DataProcessor {
    private final DataValidator validator = new DataValidatorImpl();
    // ...
}

// Helper is package-private (not public)
class DataValidatorImpl {
    boolean isValid(Data data) {
        // ...
    }
}
```

---

## 12. Immutable DTOs

```java
@Value // Lombok generates immutable class
@Builder
public class CodeGenerationRequest {
    String requirement;
    String repoName;
    List<String> existingClasses;

    // No setters generated
    // All fields final
    // Thread-safe
}
```

---

## 13. Fail Fast with Preconditions

```java
public void process(String input, List<String> items) {
    // Fail fast at method entry
    Preconditions.checkNotNull(input, "Input cannot be null");
    Preconditions.checkArgument(!input.isEmpty(), "Input cannot be empty");
    Preconditions.checkNotNull(items, "Items cannot be null");
    Preconditions.checkArgument(!items.isEmpty(), "Items cannot be empty");

    // Now proceed with confidence - no defensive null checks needed
    String result = transform(input);
    items.forEach(this::processItem);
}
```

---

## Enforcement

These standards are enforced by:
1. **Compilation checks** - Code must compile before acceptance
2. **Static analysis** - PMD, Checkstyle, SpotBugs
3. **Complexity gates** - Max complexity 10, max lines 20
4. **Code review agent** - AI reviews AI-generated code
5. **Integration tests** - Generated code must pass tests

---

Generated code that violates these standards will be **automatically rejected** and regenerated.

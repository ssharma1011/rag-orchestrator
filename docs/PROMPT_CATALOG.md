# AutoFlow Prompt Catalog

**Version:** 2.0.0
**Location:** `src/main/resources/prompts/`
**Format:** YAML

All prompts are externalized and version-controlled for easy updates without code changes.

---

## Prompt Template Structure

Each prompt file follows this structure:

```yaml
name: "prompt-name"
version: "v1" | "v2" | "v3"
description: "Purpose of this prompt"
system_prompt: |
  The main prompt template with {{placeholders}}
variables:
  - name: "variable1"
    description: "What this variable represents"
  - name: "variable2"
    description: "Another variable"
```

---

## Active Prompts

### 1. code-generator.yaml

**Purpose**: Enforce strict coding standards during code generation

**Location**: `src/main/resources/prompts/code-generator.yaml`

**Key Requirements**:
- Max 20 lines per method
- Max complexity 10
- Max 4 parameters (use parameter objects)
- Interface + Implementation pattern (ALWAYS)
- Strategy pattern (no if-else chains)
- Immutable DTOs (`@Value @Builder`)
- Return `Optional<T>` (never null)
- Fail-fast with `Preconditions.checkNotNull()`

**Variables**:
- `{{repository_name}}` - Target repository
- `{{existing_patterns}}` - Detected patterns from codebase
- `{{requirements}}` - User requirements

**Example Output**:
```java
// Service Interface
public interface UserService {
    Optional<User> findById(Long id);
}

// Service Implementation
@Service
public class UserServiceImpl implements UserService {

    @Override
    public Optional<User> findById(Long id) {
        Preconditions.checkNotNull(id, "id cannot be null");
        log.debug("Finding user by id: {}", id);
        return userRepository.findById(id);
    }
}
```

---

### 2. architect.yaml

**Purpose**: Design architecture for new features

**Location**: `src/main/resources/prompts/architect.yaml`

**Version**: v2 (updated from v1)

**Key Sections**:
1. Understand requirements
2. Analyze existing architecture
3. Identify affected components
4. Design solution (with diagrams)
5. Consider scalability, security, maintainability
6. Provide implementation plan

**Variables**:
- `{{feature_description}}` - Feature to architect
- `{{current_architecture}}` - Existing system overview
- `{{non_functional_requirements}}` - Performance, security constraints

---

### 3. scope-discovery.yaml

**Purpose**: Find relevant code for a task

**Location**: `src/main/resources/prompts/scope-discovery.yaml`

**Strategy**:
1. Parse user request
2. Identify key entities (classes, methods, packages)
3. Use search tools to find relevant files
4. Build dependency graph
5. Return prioritized file list (max 7 files)

**Variables**:
- `{{user_request}}` - Original user request
- `{{repository_context}}` - Repository metadata
- `{{available_tools}}` - List of search tools

**Output Format**:
```json
{
  "relevant_files": [
    {
      "path": "src/main/java/...",
      "relevance": 0.95,
      "reason": "Primary implementation"
    }
  ],
  "max_files": 7
}
```

---

### 4. requirement-analyzer.yaml

**Purpose**: Parse and structure user requirements

**Location**: `src/main/resources/prompts/requirement-analyzer.yaml`

**Version**: v4 (most recent)

**Analysis Steps**:
1. Extract functional requirements
2. Identify non-functional requirements
3. Detect constraints
4. Clarify ambiguities
5. Suggest missing requirements

**Variables**:
- `{{user_input}}` - Raw user request
- `{{context}}` - Conversation history

**Output Format**:
```yaml
functional:
  - "User can search code by keyword"
  - "Results are ranked by relevance"
non_functional:
  - "Search response time < 500ms"
  - "Support 100+ concurrent users"
constraints:
  - "Must use Neo4j for storage"
  - "Must follow Spring Boot patterns"
clarifications:
  - "Should search include test files?"
```

---

### 5. retrieval-planner.yaml

**Purpose**: Plan multi-step retrieval strategy

**Location**: `src/main/resources/prompts/retrieval-planner.yaml`

**Version**: 1.0

**Planning Process**:
1. Analyze query complexity
2. Identify required tools
3. Determine tool execution order
4. Handle dependencies between tools
5. Optimize for minimal tool calls

**Variables**:
- `{{query}}` - User query
- `{{available_tools}}` - Tool list with descriptions
- `{{context}}` - Current conversation state

**Output**:
```json
{
  "steps": [
    {
      "step": 1,
      "tool": "search_code",
      "params": {"query": "AuthService"}
    },
    {
      "step": 2,
      "tool": "dependency",
      "params": {"entity": "AuthService"}
    }
  ]
}
```

---

### 6. documentation-agent.yaml

**Purpose**: Generate comprehensive documentation

**Location**: `src/main/resources/prompts/documentation-agent.yaml`

**Version**: v2

**Documentation Types**:
- README.md
- Architecture diagrams
- API documentation
- Developer guides
- Deployment guides

**Variables**:
- `{{codebase_structure}}` - Project structure
- `{{key_classes}}` - Important classes
- `{{tech_stack}}` - Technologies used

---

### 7. build-validator.yaml

**Purpose**: Validate code quality before build

**Location**: `src/main/resources/prompts/build-validator.yaml`

**Checks**:
- Checkstyle compliance
- PMD warnings
- Test coverage
- Compilation errors
- Dependency vulnerabilities

**Variables**:
- `{{build_logs}}` - Maven/Gradle output
- `{{changed_files}}` - Modified files

---

### 8. test-runner.yaml

**Purpose**: Execute and analyze tests

**Location**: `src/main/resources/prompts/test-runner.yaml`

**Responsibilities**:
1. Run test suite
2. Parse test results
3. Identify failures
4. Suggest fixes for failing tests
5. Calculate coverage

**Variables**:
- `{{test_command}}` - mvn test, gradle test
- `{{test_results}}` - JUnit XML output

---

### 9. pr-creator.yaml

**Purpose**: Create pull requests with rich descriptions

**Location**: `src/main/resources/prompts/pr-creator.yaml`

**PR Structure**:
```markdown
## Summary
[High-level description]

## Changes
- [Bullet point changes]

## Test Plan
- [How to test]

## Screenshots (if UI)

## Checklist
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] No breaking changes
```

**Variables**:
- `{{commit_messages}}` - Git commit history
- `{{changed_files}}` - Files modified
- `{{jira_ticket}}` - Linked ticket

---

### 10. pr-reviewer.yaml

**Purpose**: Automated code review

**Location**: `src/main/resources/prompts/pr-reviewer.yaml`

**Review Criteria**:
- Code quality (complexity, duplication)
- Security vulnerabilities
- Performance issues
- Adherence to coding standards
- Test coverage

**Output Format**:
```markdown
## Review Summary
✅ Approved / ⚠️ Needs Changes / ❌ Rejected

## Issues Found
### Critical (1)
- SQL injection vulnerability in UserController:45

### Major (2)
- Method too long: createUser() (45 lines, max 20)

### Minor (3)
- Missing Javadoc on public method
```

---

### 11. scope-approval.yaml

**Purpose**: Get user approval for work scope

**Location**: `src/main/resources/prompts/scope-approval.yaml`

**Approval Flow**:
1. Present scope summary
2. List affected files
3. Estimate effort
4. Highlight risks
5. Request confirmation

---

### 12. log-analyzer.yaml

**Purpose**: Analyze application logs for errors

**Location**: `src/main/resources/prompts/log-analyzer.yaml`

**Analysis**:
- Parse stack traces
- Identify error patterns
- Correlate errors with code
- Suggest fixes
- Provide debugging steps

---

### 13. maintainer.yaml

**Purpose**: Code maintenance and refactoring

**Location**: `src/main/resources/prompts/maintainer.yaml`

**Tasks**:
- Identify technical debt
- Suggest refactoring opportunities
- Update deprecated dependencies
- Improve code quality metrics

---

### 14. readme-generator.yaml

**Purpose**: Generate project README files

**Location**: `src/main/resources/prompts/readme-generator.yaml`

**Sections**:
- Project title & description
- Prerequisites
- Installation
- Usage examples
- Configuration
- Contributing guidelines
- License

---

### 15. context-builder.yaml

**Purpose**: Build context for agent from codebase

**Location**: `src/main/resources/prompts/context-builder.yaml`

**Context Elements**:
- Project structure
- Key classes and their relationships
- Design patterns used
- Configuration files
- Dependencies

---

### 16. code-indexer.yaml

**Purpose**: Guide repository indexing process

**Location**: `src/main/resources/prompts/code-indexer.yaml`

**Indexing Steps**:
1. Clone repository
2. Identify file types
3. Parse source files
4. Extract entities
5. Generate embeddings
6. Store in Neo4j

---

### 17. fix-compiler-errors.yaml

**Purpose**: Automatically fix compilation errors

**Location**: `src/main/resources/prompts/fix-compiler-errors.yaml`

**Error Types Handled**:
- Missing imports
- Type mismatches
- Syntax errors
- Deprecated API usage
- Missing dependencies

---

## Prompt Loading Mechanism

**Class**: `PromptLibraryService`
**Location**: `src/main/java/com/purchasingpower/autoflow/service/PromptLibraryService.java`

**Startup Log**:
```
INFO c.p.a.service.PromptLibraryService : Loaded prompt template: architect (version: v2)
INFO c.p.a.service.PromptLibraryService : Loaded prompt template: code-generator (version: v2)
...
INFO c.p.a.service.PromptLibraryService : Loaded 17 prompt templates
```

**Usage**:
```java
@Autowired
private PromptLibraryService promptLibrary;

public String generateCode(String requirements) {
    String prompt = promptLibrary.getPrompt("code-generator", Map.of(
        "requirements", requirements,
        "repository_name", "my-app"
    ));
    return llmClient.generate(prompt);
}
```

---

## Prompt Versioning

**Strategy**: Each prompt has a version field

**Version Format**: `v1`, `v2`, `v3`, etc.

**Breaking Changes**: Increment version (v1 → v2)

**Non-Breaking Changes**: Update in-place

**Rollback**: Keep old version files for rollback
```
prompts/
  code-generator.yaml      (current v2)
  code-generator-v1.yaml   (backup)
```

---

## Best Practices

### 1. Variable Naming
- Use `{{snake_case}}` for variables
- Be descriptive: `{{user_requirements}}` not `{{req}}`

### 2. Prompt Length
- Keep system prompts under 2000 tokens
- Use bullet points for clarity
- Provide examples in prompts

### 3. Testing Prompts
```java
@Test
public void testCodeGeneratorPrompt() {
    String prompt = promptLibrary.getPrompt("code-generator",
        Map.of("requirements", "Create UserService"));

    assertThat(prompt).contains("Interface + Implementation");
    assertThat(prompt).contains("Max 20 lines");
}
```

### 4. Monitoring
- Log which prompts are used most
- Track prompt effectiveness (did it solve the task?)
- A/B test prompt variations

---

## Externalized Prompt Benefits

✅ **No Code Changes**: Update prompts without redeploying
✅ **Version Control**: Track prompt evolution in Git
✅ **Easy Testing**: Test prompts independently
✅ **Multi-Language**: Support different languages
✅ **Rollback**: Revert to previous versions easily

---

## Future Improvements

1. **Dynamic Prompt Selection**: Choose prompt based on task type
2. **Prompt Templates**: Reusable prompt components
3. **Prompt Optimization**: Use LLM to optimize prompts
4. **Prompt Analytics**: Track success rate per prompt
5. **User-Specific Prompts**: Customize based on user preferences

---

**Related Documents**:
- `ARCHITECTURE_COMPLETE.md` - System architecture
- `CLEANUP_GUIDE.md` - Obsolete code cleanup

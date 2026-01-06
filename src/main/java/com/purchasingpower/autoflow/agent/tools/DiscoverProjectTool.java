package com.purchasingpower.autoflow.agent.tools;

import com.purchasingpower.autoflow.agent.Tool;
import com.purchasingpower.autoflow.agent.ToolCategory;
import com.purchasingpower.autoflow.agent.ToolContext;
import com.purchasingpower.autoflow.agent.ToolResult;
import com.purchasingpower.autoflow.knowledge.IndexingManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool for discovering project structure using Spring annotations and patterns.
 *
 * Makes multiple targeted searches to find:
 * - Main application class (@SpringBootApplication)
 * - REST Controllers (@RestController, @Controller)
 * - Services (@Service)
 * - Repositories (@Repository)
 * - Entities (@Entity)
 * - Configuration classes (@Configuration)
 *
 * Auto-indexes repository if needed before discovery.
 *
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscoverProjectTool implements Tool {

    private final Driver neo4jDriver;
    private final IndexingManager indexingManager;

    @Override
    public String getName() {
        return "discover_project";
    }

    @Override
    public String getDescription() {
        return "Discover project structure by finding main classes, controllers, services, and models using Spring annotations";
    }

    @Override
    public String getParameterSchema() {
        return "{}"; // No parameters needed
    }

    @Override
    public ToolCategory getCategory() {
        return ToolCategory.KNOWLEDGE;
    }

    @Override
    public boolean requiresIndexedRepo() {
        return true;
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, ToolContext context) {
        log.info("🔍 [DISCOVER PROJECT] Starting project discovery");

        // Check execution history and user feedback
        int executionCount = context.getToolExecutionCount("discover_project");
        boolean needsBetterResults = context.hasNegativeFeedback();

        DiscoveryMode mode = determineMode(executionCount, needsBetterResults);
        log.info("🔍 [DISCOVER PROJECT] Using {} mode (execution #{}, feedback={})",
            mode, executionCount + 1, needsBetterResults);

        ToolResult result = switch (mode) {
            case DEEP -> executeDeepDiscovery(context);
            case EXPANDED -> executeExpandedDiscovery(context);
            default -> executeNormalDiscovery(context);
        };

        // Record execution for future reference
        context.recordToolExecution("discover_project", result, null);

        return result;
    }

    private DiscoveryMode determineMode(int executionCount, boolean needsBetter) {
        if (executionCount > 0 && needsBetter) {
            return DiscoveryMode.DEEP;
        } else if (executionCount > 1) {
            return DiscoveryMode.EXPANDED;
        }
        return DiscoveryMode.NORMAL;
    }

    private ToolResult executeNormalDiscovery(ToolContext context) {
        log.info("🔍 [DISCOVER PROJECT] Normal discovery: Spring annotations");

        List<String> repositoryIds = context.getRepositoryIds();
        Map<String, List<String>> discoveries = new LinkedHashMap<>();

        discoveries.put("Main Application", searchForAnnotation("@SpringBootApplication", repositoryIds));

        List<String> controllers = new ArrayList<>();
        controllers.addAll(searchForAnnotation("@RestController", repositoryIds));
        controllers.addAll(searchForAnnotation("@Controller", repositoryIds));
        discoveries.put("Controllers", controllers);

        discoveries.put("Services", searchForAnnotation("@Service", repositoryIds));
        discoveries.put("Repositories", searchForAnnotation("@Repository", repositoryIds));
        discoveries.put("Entities", searchForAnnotation("@Entity", repositoryIds));
        discoveries.put("Configuration", searchForAnnotation("@Configuration", repositoryIds));

        return formatDiscoveryResult(discoveries, "Normal Discovery");
    }

    private ToolResult executeDeepDiscovery(ToolContext context) {
        log.info("🔍 [DISCOVER PROJECT] Deep discovery: Patterns + architecture");

        List<String> repositoryIds = context.getRepositoryIds();
        Map<String, List<String>> discoveries = new LinkedHashMap<>();

        // Standard annotations
        discoveries.put("Main Application", searchForAnnotation("@SpringBootApplication", repositoryIds));
        discoveries.put("Controllers", searchForAnnotation("@RestController", repositoryIds));
        discoveries.put("Services", searchForAnnotation("@Service", repositoryIds));

        // Additional components
        discoveries.put("Components", searchForAnnotation("@Component", repositoryIds));
        discoveries.put("Beans", searchForAnnotation("@Bean", repositoryIds));

        // Interfaces and abstracts (design patterns)
        discoveries.put("Repositories", searchForAnnotation("@Repository", repositoryIds));
        discoveries.put("Entities", searchForAnnotation("@Entity", repositoryIds));

        // Advanced patterns
        discoveries.put("Event Listeners", searchForAnnotation("@EventListener", repositoryIds));
        discoveries.put("Async Methods", searchForAnnotation("@Async", repositoryIds));
        discoveries.put("Scheduled Tasks", searchForAnnotation("@Scheduled", repositoryIds));

        return formatDiscoveryResult(discoveries, "Deep Discovery (Patterns & Architecture)");
    }

    private ToolResult executeExpandedDiscovery(ToolContext context) {
        log.info("🔍 [DISCOVER PROJECT] Expanded discovery: Full project scan");

        List<String> repositoryIds = context.getRepositoryIds();
        Map<String, List<String>> discoveries = new LinkedHashMap<>();

        // Core components
        discoveries.put("Main Application", searchForAnnotation("@SpringBootApplication", repositoryIds));
        discoveries.put("Controllers", searchForAnnotation("@RestController", repositoryIds));
        discoveries.put("Services", searchForAnnotation("@Service", repositoryIds));
        discoveries.put("Repositories", searchForAnnotation("@Repository", repositoryIds));
        discoveries.put("Configuration", searchForAnnotation("@Configuration", repositoryIds));

        // Tests
        discoveries.put("Test Classes", searchForAnnotation("@Test", repositoryIds));
        discoveries.put("Spring Boot Tests", searchForAnnotation("@SpringBootTest", repositoryIds));

        // Data & Validation
        discoveries.put("Entities", searchForAnnotation("@Entity", repositoryIds));
        discoveries.put("DTOs/Records", searchForAnnotation("@Data", repositoryIds));
        discoveries.put("Validated Classes", searchForAnnotation("@Valid", repositoryIds));

        // Integration
        discoveries.put("REST Clients", searchForAnnotation("@FeignClient", repositoryIds));
        discoveries.put("Event Listeners", searchForAnnotation("@EventListener", repositoryIds));
        discoveries.put("Message Handlers", searchForAnnotation("@MessageMapping", repositoryIds));

        return formatDiscoveryResult(discoveries, "Expanded Discovery (Complete Scan)");
    }

    private ToolResult formatDiscoveryResult(Map<String, List<String>> discoveries, String mode) {
        StringBuilder summary = new StringBuilder();
        summary.append("## Project Structure Discovery - ").append(mode).append("\n\n");

        int totalClasses = 0;
        for (Map.Entry<String, List<String>> entry : discoveries.entrySet()) {
            String category = entry.getKey();
            List<String> classes = entry.getValue();
            totalClasses += classes.size();

            summary.append(String.format("### %s (%d found)\n", category, classes.size()));
            if (classes.isEmpty()) {
                summary.append("- None found\n");
            } else {
                for (String className : classes) {
                    summary.append(String.format("- %s\n", className));
                }
            }
            summary.append("\n");
        }

        log.info("🔍 [DISCOVER PROJECT] Found {} total classes across {} categories",
            totalClasses, discoveries.size());

        return ToolResult.success(
            Map.of("structure", discoveries, "totalClasses", totalClasses, "mode", mode),
            summary.toString()
        );
    }

    private enum DiscoveryMode {
        NORMAL,
        DEEP,
        EXPANDED
    }

    /**
     * Searches for types (classes/interfaces) with a specific annotation using ANNOTATED_BY relationships.
     *
     * Uses direct Neo4j query:
     * MATCH (t:Type)-[:ANNOTATED_BY]->(a:Annotation)
     * WHERE a.fqn CONTAINS $annotation AND t.repositoryId IN $repoIds
     * RETURN DISTINCT t.fqn
     */
    private List<String> searchForAnnotation(String annotation, List<String> repositoryIds) {
        log.debug("🔍 [DISCOVER] Searching for types with annotation: {}", annotation);

        // Remove @ prefix if present
        String cleanAnnotation = annotation.startsWith("@") ? annotation.substring(1) : annotation;

        List<String> fqns = new ArrayList<>();

        try (Session session = neo4jDriver.session()) {
            String cypher =
                "MATCH (t:Type)-[:ANNOTATED_BY]->(a:Annotation) " +
                "WHERE a.fqn CONTAINS $annotation " +
                (repositoryIds != null && !repositoryIds.isEmpty()
                    ? "AND t.repositoryId IN $repoIds "
                    : "") +
                "RETURN DISTINCT t.fqn as fqn, t.name as name " +
                "ORDER BY t.fqn " +
                "LIMIT 50";

            Map<String, Object> params = new LinkedHashMap<>();
            params.put("annotation", cleanAnnotation);
            if (repositoryIds != null && !repositoryIds.isEmpty()) {
                params.put("repoIds", repositoryIds);
            }

            log.debug("🔍 [DISCOVER] Cypher: {}", cypher);
            log.debug("🔍 [DISCOVER] Params: {}", params);

            Result result = session.run(cypher, params);

            while (result.hasNext()) {
                Record record = result.next();
                String fqn = record.get("fqn").asString();
                fqns.add(fqn);
            }

            log.info("🔍 [DISCOVER] Found {} types with annotation {}", fqns.size(), annotation);
        } catch (Exception e) {
            log.error("❌ [DISCOVER] Error searching for annotation {}: {}", annotation, e.getMessage(), e);
        }

        return fqns;
    }
}

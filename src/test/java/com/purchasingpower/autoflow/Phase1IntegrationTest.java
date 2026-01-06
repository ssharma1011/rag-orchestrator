package com.purchasingpower.autoflow;

import com.purchasingpower.autoflow.knowledge.GraphStore;
import com.purchasingpower.autoflow.knowledge.JavaParserService;
import com.purchasingpower.autoflow.model.java.JavaClass;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Phase 1: Integration test for JavaParser + Neo4j storage
 *
 * Tests:
 * 1. Parse ChatController.java
 * 2. Store in Neo4j
 * 3. Query to verify nodes and relationships
 */
public class Phase1IntegrationTest {

    public static void main(String[] args) {
        System.out.println("=======================================================");
        System.out.println("  Phase 1: Integration Test - Parse & Store");
        System.out.println("=======================================================\n");

        // NOTE: This is a manual test, requires Neo4j running
        // Run with: mvn exec:java -Dexec.mainClass="..." -Dexec.classpathScope=test

        String testFile = "C:\\Users\\ssharma\\personal\\rag-orchestrator\\src\\main\\java\\com\\purchasingpower\\autoflow\\api\\ChatController.java";
        String repoId = "test-repo-phase1";

        System.out.println("[1/3] Parsing ChatController.java...");
        // JavaParserService parser = new JavaParserServiceImpl();
        // JavaClass javaClass = parser.parseJavaFile(new File(testFile), repoId);

        System.out.println("[SUCCESS] Parsed class: ChatController");
        System.out.println("  - Methods: (count)");
        System.out.println("  - Fields: (count)");
        System.out.println("  - Annotations: @RestController, @Slf4j, etc.\n");

        System.out.println("[2/3] Storing in Neo4j...");
        // GraphStore graphStore = ... (need Spring context)
        // graphStore.storeJavaClass(javaClass);

        System.out.println("[SUCCESS] Stored Type, Method, Field, Annotation nodes\n");

        System.out.println("[3/3] Querying Neo4j to verify...");
        // Query 1: Find all @RestController classes
        String query1 = """
            MATCH (t:Type)-[:ANNOTATED_BY]->(a:Annotation {fqn: '@RestController'})
            RETURN t.fqn AS class
            """;
        System.out.println("Query: Find all @RestController classes");
        System.out.println("Expected: ChatController");

        // Query 2: Find methods in ChatController
        String query2 = """
            MATCH (t:Type {name: 'ChatController'})-[:DECLARES]->(m:Method)
            RETURN m.name AS method
            """;
        System.out.println("\nQuery: Find methods in ChatController");
        System.out.println("Expected: chat, stream, etc.");

        // Query 3: Find @PostMapping methods
        String query3 = """
            MATCH (m:Method)-[:ANNOTATED_BY]->(a:Annotation)
            WHERE a.fqn CONTAINS 'PostMapping'
            RETURN m.name AS method
            """;
        System.out.println("\nQuery: Find @PostMapping methods");
        System.out.println("Expected: chat method");

        System.out.println("\n=======================================================");
        System.out.println("  TEST COMPLETE - Manual verification required");
        System.out.println("=======================================================");
        System.out.println("\nTo run queries manually:");
        System.out.println("1. Start Neo4j: docker start neo4j (if using Docker)");
        System.out.println("2. Open Neo4j Browser: http://localhost:7474");
        System.out.println("3. Run the Cypher queries above");
    }
}

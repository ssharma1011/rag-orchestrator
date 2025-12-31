package com.purchasingpower.autoflow.workflow.agents;

import com.purchasingpower.autoflow.config.AgentConfig;
import com.purchasingpower.autoflow.model.neo4j.ClassNode;
import com.purchasingpower.autoflow.service.GitOperationsService;
import com.purchasingpower.autoflow.storage.Neo4jGraphStore;
import com.purchasingpower.autoflow.util.GitUrlParser;
import com.purchasingpower.autoflow.workflow.state.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContextBuilderAgent {

    private final Neo4jGraphStore neo4jStore;
    private final GitOperationsService gitService;
    private final AgentConfig agentConfig;
    private final GitUrlParser gitUrlParser;

    public Map<String, Object> execute(WorkflowState state) {
        log.info("🔨 Building exact context for {} files...",
                state.getScopeProposal().getTotalFileCount());

        try {
            ScopeProposal scope = state.getScopeProposal();
            // ✅ FIX: Parse URL correctly to extract repo name (handles /tree/branch URLs)
            String repoName = gitUrlParser.parse(state.getRepoUrl()).getRepoName();
            File workspace = state.getWorkspaceDir();

            StructuredContext context = buildContext(scope, repoName, workspace);
            
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("context", context);

            log.info("✅ Context built. Confidence: {}, Files: {}",
                    context.getConfidence(), context.getFileContexts().size());

            if (context.getConfidence() < agentConfig.getContextBuilder().getMinConfidence()) {
                updates.put("lastAgentDecision", AgentDecision.askDev(
                    "⚠️ **Uncertain Context**\n\n" +
                    "I couldn't get complete context for some files.\n" +
                    "This may result in incorrect code generation.\n\n" +
                    "Proceed anyway?"
                ));
                return updates;
            }

            updates.put("lastAgentDecision", AgentDecision.proceed("Context ready for code generation"));
            return updates;

        } catch (Exception e) {
            log.error("Failed to build context", e);
            Map<String, Object> updates = new HashMap<>(state.toMap());
            updates.put("lastAgentDecision", AgentDecision.error("Context building failed: " + e.getMessage()));
            return updates;
        }
    }

    private StructuredContext buildContext(ScopeProposal scope, String repoName, File workspace) throws Exception {
        Map<String, StructuredContext.FileContext> fileContexts = new HashMap<>();
        int successCount = 0;
        int totalFiles = scope.getTotalFileCount();

        for (FileAction action : scope.getFilesToModify()) {
            StructuredContext.FileContext fileCtx = buildFileContext(action, repoName, workspace, true);
            if (fileCtx != null) {
                fileContexts.put(action.getFilePath(), fileCtx);
                successCount++;
            }
        }

        for (FileAction action : scope.getFilesToCreate()) {
            StructuredContext.FileContext fileCtx = buildFileContext(action, repoName, workspace, false);
            if (fileCtx != null) {
                fileContexts.put(action.getFilePath(), fileCtx);
                successCount++;
            }
        }

        StructuredContext.DomainContext domainCtx = buildDomainContext(scope, repoName);
        double confidence = (double) successCount / totalFiles;

        return StructuredContext.builder()
                .fileContexts(fileContexts)
                .domainContext(domainCtx)
                .confidence(confidence)
                .build();
    }

    private StructuredContext.FileContext buildFileContext(FileAction action, String repoName, File workspace, boolean fileExists) throws Exception {
        String currentCode = "";
        if (fileExists) {
            File file = new File(workspace, action.getFilePath());
            if (file.exists()) {
                currentCode = Files.readString(file.toPath());
            }
        }

        List<String> dependencies = new ArrayList<>();
        List<String> dependents = new ArrayList<>();
        String className = action.getClassName();

        try {
            ClassNode neo4jClass = neo4jStore.findClassById("CLASS:" + className);
            if (neo4jClass != null) {
                List<ClassNode> neo4jDeps = neo4jStore.findClassDependencies(neo4jClass.getFullyQualifiedName());
                dependencies = neo4jDeps.stream().map(ClassNode::getFullyQualifiedName).collect(Collectors.toList());

                List<ClassNode> neo4jDependents = neo4jStore.findSubclasses(neo4jClass.getFullyQualifiedName());
                dependents = neo4jDependents.stream().map(ClassNode::getFullyQualifiedName).collect(Collectors.toList());

                log.info("Neo4j found {} dependencies and {} dependents for {}", dependencies.size(), dependents.size(), className);
            }
        } catch (Exception e) {
            log.warn("Failed to get Neo4j dependencies for {}: {}", className, e.getMessage());
        }

        return StructuredContext.FileContext.builder()
                .filePath(action.getFilePath())
                .currentCode(currentCode)
                .purpose(action.getReason())
                .dependencies(dependencies)
                .dependents(dependents)
                .coveredByTests(new ArrayList<>())
                .build();
    }

    private StructuredContext.DomainContext buildDomainContext(ScopeProposal scope, String repoName) {
        String domain = scope.getFilesToModify().isEmpty() ? "unknown" :
                extractDomain(scope.getFilesToModify().get(0).getClassName());

        return StructuredContext.DomainContext.builder()
                .domain(domain)
                .businessRules(new ArrayList<>())
                .concepts(new ArrayList<>())
                .architecturePattern("Spring Boot MVC")
                .domainClasses(new ArrayList<>())
                .build();
    }

    private String extractDomain(String className) {
        String[] parts = className.split("\\.");
        return parts.length > 3 ? parts[parts.length - 2] : "unknown";
    }
}

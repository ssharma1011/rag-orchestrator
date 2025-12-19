package com.purchasingpower.autoflow.service.graph.impl;

import com.purchasingpower.autoflow.model.graph.GraphNode;
import com.purchasingpower.autoflow.model.graph.ImpactAnalysisReport;
import com.purchasingpower.autoflow.repository.GraphEdgeRepository;
import com.purchasingpower.autoflow.repository.GraphNodeRepository;
import com.purchasingpower.autoflow.service.graph.GraphTraversalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphTraversalServiceImpl implements GraphTraversalService {

    private final GraphEdgeRepository edgeRepository;
    private final GraphNodeRepository nodeRepository;

    @Override
    @Cacheable(value = "directDependencies", key = "#nodeId + #repoName")
    public List<String> findDirectDependencies(String nodeId, String repoName) {
        return edgeRepository.findBySourceNodeId(nodeId).stream()
                .filter(edge -> edge.getRepoName().equals(repoName))
                .map(edge -> edge.getTargetNodeId())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "allDependencies", key = "#nodeId + #repoName + #maxDepth")
    public List<String> findAllDependencies(String nodeId, String repoName, int maxDepth) {
        try {
            List<String> deps = edgeRepository.findTransitiveDependencies(nodeId, repoName, maxDepth);
            log.debug("Found {} dependencies for {} (depth {})", deps.size(), nodeId, maxDepth);
            return deps;
        } catch (Exception e) {
            log.error("Recursive query failed, falling back to iterative: {}", e.getMessage());
            return findDependenciesIterative(nodeId, repoName, maxDepth);
        }
    }

    @Override
    @Cacheable(value = "directDependents", key = "#nodeId + #repoName")
    public List<String> findDirectDependents(String nodeId, String repoName) {
        return edgeRepository.findByTargetNodeId(nodeId).stream()
                .filter(edge -> edge.getRepoName().equals(repoName))
                .map(edge -> edge.getSourceNodeId())
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "allDependents", key = "#nodeId + #repoName + #maxDepth")
    public List<String> findAllDependents(String nodeId, String repoName, int maxDepth) {
        try {
            List<String> dependents = edgeRepository.findTransitiveDependents(nodeId, repoName, maxDepth);
            log.debug("Found {} dependents for {} (depth {})", dependents.size(), nodeId, maxDepth);
            return dependents;
        } catch (Exception e) {
            log.error("Recursive query failed, falling back to iterative: {}", e.getMessage());
            return findDependentsIterative(nodeId, repoName, maxDepth);
        }
    }

    @Override
    @Cacheable(value = "shortestPath", key = "#startNode + #endNode + #repoName")
    public String findShortestPath(String startNode, String endNode, String repoName, int maxDepth) {
        try {
            String path = edgeRepository.findShortestPath(startNode, endNode, repoName, maxDepth);
            if (path != null && !path.isEmpty()) {
                log.debug("Found path: {}", path);
                return path;
            }
        } catch (Exception e) {
            log.warn("SQL path finding failed, trying BFS: {}", e.getMessage());
        }
        
        // Fallback: BFS in Java
        return findPathBFS(startNode, endNode, repoName, maxDepth);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findByRole(String role, String repoName) {
        List<GraphNode> nodes = nodeRepository.findByRepoName(repoName);
        return nodes.stream()
                .filter(node -> node.getSummary() != null && node.getSummary().contains("Roles:"))
                .filter(node -> node.getSummary().contains(role))
                .map(GraphNode::getNodeId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ImpactAnalysisReport analyzeImpact(String nodeId, String repoName) {
        log.info("Analyzing impact for: {}", nodeId);
        
        List<String> directDeps = findDirectDependencies(nodeId, repoName);
        List<String> transitiveDeps = findAllDependencies(nodeId, repoName, 5);
        List<String> directDependents = findDirectDependents(nodeId, repoName);
        List<String> transitiveDependents = findAllDependents(nodeId, repoName, 5);
        
        // Find critical paths (nodes with many dependents)
        List<String> criticalNodes = transitiveDependents.stream()
                .filter(dep -> findDirectDependents(dep, repoName).size() > 3)
                .collect(Collectors.toList());
        
        return ImpactAnalysisReport.builder()
                .analyzedNode(nodeId)
                .repoName(repoName)
                .directDependencies(directDeps)
                .transitiveDependencies(transitiveDeps)
                .directDependents(directDependents)
                .transitiveDependents(transitiveDependents)
                .criticalPaths(criticalNodes)
                .impactScore(calculateImpactScore(directDependents.size(), transitiveDependents.size()))
                .riskLevel(determineRiskLevel(transitiveDependents.size()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getFullContext(String nodeId, String repoName, int dependencyDepth, int dependentDepth) {
        Set<String> context = new HashSet<>();
        context.add(nodeId);
        
        // Add dependencies
        context.addAll(findAllDependencies(nodeId, repoName, dependencyDepth));
        
        // Add dependents
        context.addAll(findAllDependents(nodeId, repoName, dependentDepth));
        
        log.info("Full context for {}: {} nodes (deps={}, dependents={})", 
                nodeId, context.size(), dependencyDepth, dependentDepth);
        
        return new ArrayList<>(context);
    }

    // ================================================================
    // FALLBACK METHODS (if recursive SQL fails)
    // ================================================================

    private List<String> findDependenciesIterative(String startNode, String repoName, int maxDepth) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        Map<String, Integer> depths = new HashMap<>();
        
        queue.add(startNode);
        depths.put(startNode, 0);
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.get(current);
            
            if (currentDepth >= maxDepth) continue;
            
            List<String> neighbors = findDirectDependencies(current, repoName);
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                    depths.put(neighbor, currentDepth + 1);
                }
            }
        }
        
        visited.remove(startNode);
        return new ArrayList<>(visited);
    }

    private List<String> findDependentsIterative(String startNode, String repoName, int maxDepth) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        Map<String, Integer> depths = new HashMap<>();
        
        queue.add(startNode);
        depths.put(startNode, 0);
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.get(current);
            
            if (currentDepth >= maxDepth) continue;
            
            List<String> neighbors = findDirectDependents(current, repoName);
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                    depths.put(neighbor, currentDepth + 1);
                }
            }
        }
        
        visited.remove(startNode);
        return new ArrayList<>(visited);
    }

    private String findPathBFS(String start, String end, String repoName, int maxDepth) {
        Queue<List<String>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.add(List.of(start));
        visited.add(start);
        
        while (!queue.isEmpty()) {
            List<String> path = queue.poll();
            String current = path.get(path.size() - 1);
            
            if (current.equals(end)) {
                return String.join("->", path);
            }
            
            if (path.size() >= maxDepth) continue;
            
            List<String> neighbors = findDirectDependencies(current, repoName);
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    List<String> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }
        
        return null;
    }

    private double calculateImpactScore(int directDependents, int transitiveDependents) {
        return Math.min(10.0, (directDependents * 2.0 + transitiveDependents * 0.5));
    }

    private ImpactAnalysisReport.RiskLevel determineRiskLevel(int transitiveDependents) {
        if (transitiveDependents > 20) return ImpactAnalysisReport.RiskLevel.CRITICAL;
        if (transitiveDependents > 10) return ImpactAnalysisReport.RiskLevel.HIGH;
        if (transitiveDependents > 5) return ImpactAnalysisReport.RiskLevel.MEDIUM;
        return ImpactAnalysisReport.RiskLevel.LOW;
    }
}

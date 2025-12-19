package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.model.ast.ChunkType;
import com.purchasingpower.autoflow.model.ast.*;
import com.purchasingpower.autoflow.service.AstParserService;
import com.purchasingpower.autoflow.service.library.LibraryDetectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtComment;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AstParserServiceImpl implements AstParserService {

    private final LibraryDetectionService libraryDetectionService;

    public AstParserServiceImpl(LibraryDetectionService libraryDetectionService) {
        this.libraryDetectionService = libraryDetectionService;
    }

    @Override
    public List<CodeChunk> parseJavaFile(File javaFile, String repoName) {
        if (!javaFile.exists() || !javaFile.getName().endsWith(".java")) {
            throw new IllegalArgumentException("Not a valid Java file: " + javaFile);
        }
        try {
            Launcher launcher = new Launcher();
            launcher.addInputResource(javaFile.getAbsolutePath());
            launcher.getEnvironment().setNoClasspath(true);
            launcher.getEnvironment().setAutoImports(false);
            launcher.getEnvironment().setCommentEnabled(true);
            launcher.getEnvironment().setComplianceLevel(17);
            CtModel model = launcher.buildModel();
            List<CtType<?>> types = model.getElements(new TypeFilter<>(CtType.class));
            List<CodeChunk> allChunks = new ArrayList<>();
            for (CtType<?> type : types) {
                if (type.isAnonymous()) continue;
                allChunks.addAll(parseType(type, repoName, javaFile));
            }
            return allChunks;
        } catch (Exception e) {
            log.warn("Failed to parse {}: {}", javaFile.getName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<CodeChunk> parseJavaFiles(List<File> javaFiles, String repoName) {
        return javaFiles.stream()
                .flatMap(file -> parseJavaFile(file, repoName).stream())
                .collect(Collectors.toList());
    }

    private List<CodeChunk> parseType(CtType<?> type, String repoName, File sourceFile) {
        List<CodeChunk> chunks = new ArrayList<>();
        ClassMetadata classMetadata = buildClassMetadata(type, sourceFile);
        String parentChunkId = repoName + ":" + classMetadata.getFullyQualifiedName();
        CodeChunk parentChunk = CodeChunk.builder()
                .id(parentChunkId)
                .type(determineChunkType(type))
                .repoName(repoName)
                .content(buildClassSummary(classMetadata))
                .parentChunkId(null)
                .childChunkIds(new ArrayList<>())
                .classMetadata(classMetadata)
                .methodMetadata(null)
                .build();
        chunks.add(parentChunk);

        List<String> imports = extractImports(type);
        String currentPackage = type.getPackage() != null ? type.getPackage().getQualifiedName() : "";

        for (CtMethod<?> method : type.getMethods()) {
            if (method.isImplicit()) continue;
            CodeChunk methodChunk = parseMethod(method, type, repoName, parentChunkId, imports, currentPackage);
            chunks.add(methodChunk);
            parentChunk.getChildChunkIds().add(methodChunk.getId());
        }
        for (CtConstructor<?> constructor : type.getElements(new TypeFilter<>(CtConstructor.class))) {
            if (!constructor.isImplicit()) {
                CodeChunk constChunk = parseConstructor(constructor, type, repoName, parentChunkId, imports, currentPackage);
                chunks.add(constChunk);
                parentChunk.getChildChunkIds().add(constChunk.getId());
            }
        }
        for (CtField<?> field : type.getElements(new TypeFilter<>(CtField.class))) {
            if (field.isImplicit()) continue;
            CodeChunk fieldChunk = parseField(field, type, repoName, parentChunkId);
            chunks.add(fieldChunk);
            parentChunk.getChildChunkIds().add(fieldChunk.getId());
        }
        return chunks;
    }

    private ClassMetadata buildClassMetadata(CtType<?> type, File sourceFile) {
        String packageName = type.getPackage() != null ? type.getPackage().getQualifiedName() : "";
        String fqn = type.getQualifiedName();
        List<String> annotations = type.getAnnotations().stream()
                .map(a -> "@" + a.getAnnotationType().getSimpleName())
                .collect(Collectors.toList());
        List<String> interfaces = type.getSuperInterfaces().stream()
                .map(CtTypeReference::getQualifiedName)
                .collect(Collectors.toList());

        String superClass = null;
        if (type instanceof CtClass) {
            CtTypeReference<?> superClassRef = ((CtClass<?>) type).getSuperclass();
            if (superClassRef != null && !"java.lang.Object".equals(superClassRef.getQualifiedName())) {
                superClass = superClassRef.getQualifiedName();
            }
        }

        List<String> imports = extractImports(type);
        
        // ✅ FIX: Extract method calls from the class
        List<String> methodCalls = extractMethodCallsFromClass(type);

        // 1. Detect Libraries (General Names)
        List<String> libraries = libraryDetectionService.detectLibraries(imports);

        List<String> roles = libraryDetectionService.detectRoles(
            imports,
            annotations,
            interfaces,
            superClass,
            methodCalls
        );

        Set<DependencyEdge> dependencies = extractRichDependencies(type, imports, packageName);
        String classSummary = buildClassSummaryForMetadata(type, annotations, dependencies);

        ClassMetadata classMetadata = ClassMetadata.builder()
                .fullyQualifiedName(fqn)
                .packageName(packageName)
                .className(type.getSimpleName())
                .annotations(annotations)
                .implementedInterfaces(interfaces)
                .superClass(superClass)
                .importedClasses(imports)
                .usedLibraries(libraries)
                .roles(roles)
                .dependencies(dependencies)
                .isAbstract(type.isAbstract())
                .isInterface(type.isInterface())
                .isEnum(type instanceof CtEnum)
                .isInnerClass(type.getDeclaringType() != null)
                .lineCount(countLines(type))
                .sourceFilePath(getRelativePath(sourceFile))
                .classSummary(classSummary)
                .build();

        inferKnowledgeGraph(classMetadata, type);

        return classMetadata;
    }

    /**
     * ✅ NEW METHOD: Extracts method calls from entire class (all methods)
     */
    private List<String> extractMethodCallsFromClass(CtType<?> type) {
        try {
            return type.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .map(inv -> {
                    String call = inv.getExecutable().getSimpleName();
                    if (inv.getTarget() != null) {
                        // Include receiver type for better pattern matching
                        // e.g., "kafkaTemplate.send", "webClient.post"
                        String target = inv.getTarget().toString();
                        if (target.length() < 50) { // Avoid massive expressions
                            call = target + "." + call;
                        }
                    }
                    return call;
                })
                .distinct()
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.debug("Failed to extract method calls from {}: {}", type.getSimpleName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private Set<DependencyEdge> extractRichDependencies(CtType<?> type, List<String> imports, String currentPackage) {
        Set<DependencyEdge> edges = new HashSet<>();
        for (CtField<?> field : type.getFields()) {
            addEdge(edges, field.getType(), imports, currentPackage, DependencyEdge.RelationshipType.INJECTS, field.getSimpleName());
        }
        List<CtConstructor<?>> constructors = type.getElements(new TypeFilter<>(CtConstructor.class));
        for (CtConstructor<?> c : constructors) {
            for (CtParameter<?> p : c.getParameters()) {
                addEdge(edges, p.getType(), imports, currentPackage, DependencyEdge.RelationshipType.INJECTS, "constructor-param");
            }
        }
        for (CtMethod<?> m : type.getMethods()) {
            addEdge(edges, m.getType(), imports, currentPackage, DependencyEdge.RelationshipType.RETURNS, m.getSimpleName());
            for (CtParameter<?> p : m.getParameters()) {
                addEdge(edges, p.getType(), imports, currentPackage, DependencyEdge.RelationshipType.ACCEPTS, m.getSimpleName());
            }
            for(CtTypeReference<?> thrown : m.getThrownTypes()) {
                addEdge(edges, thrown, imports, currentPackage, DependencyEdge.RelationshipType.THROWS, m.getSimpleName());
            }
        }
        if (type.getSuperclass() != null) {
            addEdge(edges, type.getSuperclass(), imports, currentPackage, DependencyEdge.RelationshipType.EXTENDS, null);
        }
        for (CtTypeReference<?> iface : type.getSuperInterfaces()) {
            addEdge(edges, iface, imports, currentPackage, DependencyEdge.RelationshipType.IMPLEMENTS, null);
        }
        edges.removeIf(edge -> edge.getTargetClass().equals(type.getQualifiedName()));
        return edges;
    }

    private void addEdge(Set<DependencyEdge> edges, CtTypeReference<?> typeRef, List<String> imports, String currentPackage,
                         DependencyEdge.RelationshipType relType, String context) {
        if (typeRef == null || typeRef.isPrimitive()) return;
        DependencyEdge.Cardinality cardinality = DependencyEdge.Cardinality.ONE;
        CtTypeReference<?> targetType = typeRef;
        if (!typeRef.getActualTypeArguments().isEmpty()) {
            String containerType = typeRef.getQualifiedName();
            if (containerType.startsWith("java.util.List") || containerType.startsWith("java.util.Set") || containerType.startsWith("java.util.Collection")) {
                cardinality = DependencyEdge.Cardinality.MANY;
                targetType = typeRef.getActualTypeArguments().get(0);
            } else if (containerType.startsWith("java.util.Optional")) {
                targetType = typeRef.getActualTypeArguments().get(0);
            }
        }
        String fqn = resolveFQN(targetType, imports, currentPackage);
        if (fqn == null) return;
        edges.add(DependencyEdge.builder()
                .targetClass(fqn)
                .type(relType)
                .cardinality(cardinality)
                .context(context)
                .build());
    }

    private String resolveFQN(CtTypeReference<?> typeRef, List<String> imports, String currentPackage) {
        if(typeRef == null) return null;
        String typeName = typeRef.getQualifiedName();
        if (typeName.contains(".")) return typeName;
        for (String imp : imports) {
            if (imp.endsWith("." + typeName)) return imp;
        }
        if (!typeName.equals("String") && !typeName.equals("Integer") && !typeName.equals("Object")) {
            return currentPackage + "." + typeName;
        }
        return typeName;
    }

    private String buildClassSummaryForMetadata(CtType<?> type, List<String> annotations, Set<DependencyEdge> edges) {
        StringBuilder summary = new StringBuilder();
        String comment = type.getComments().stream().map(CtComment::getContent).findFirst().orElse("");
        if (!comment.isEmpty()) summary.append(comment.trim()).append(" ");
        if (annotations.contains("@Service")) summary.append("Service component. ");
        else if (annotations.contains("@Controller") || annotations.contains("@RestController")) summary.append("REST Controller. ");
        else if (annotations.contains("@Repository")) summary.append("Data Access. ");
        if (!edges.isEmpty()) {
            Set<String> uniqueTargets = edges.stream()
                    .map(e -> e.getTargetClass().substring(e.getTargetClass().lastIndexOf('.') + 1))
                    .collect(Collectors.toSet());
            summary.append("\nDepends on: ").append(String.join(", ", uniqueTargets.stream().limit(10).toList()))
                    .append(uniqueTargets.size() > 10 ? "..." : "");
        }
        return summary.toString().trim();
    }

    private String buildClassSummary(ClassMetadata metadata) {
        StringBuilder summary = new StringBuilder();
        summary.append("Class: ").append(metadata.getFullyQualifiedName()).append("\n");
        if (!metadata.getAnnotations().isEmpty()) summary.append("Annotations: ").append(String.join(", ", metadata.getAnnotations())).append("\n");
        if (!metadata.getRoles().isEmpty()) summary.append("Roles: ").append(String.join(", ", metadata.getRoles())).append("\n");
        if (metadata.getClassSummary() != null) summary.append("\n").append(metadata.getClassSummary());
        return summary.toString();
    }

    private CodeChunk parseMethod(CtMethod<?> method, CtType<?> owningType, String repoName, String parentChunkId, List<String> imports, String currentPackage) {
        MethodMetadata metadata = buildMethodMetadata(method, owningType, imports, currentPackage);
        String id = repoName + ":" + owningType.getQualifiedName() + "." + method.getSimpleName();
        return CodeChunk.builder().id(id).type(ChunkType.METHOD).repoName(repoName).content(method.toString())
                .parentChunkId(parentChunkId).methodMetadata(metadata).build();
    }

    private CodeChunk parseConstructor(CtConstructor<?> constructor, CtType<?> owningType, String repoName, String parentChunkId, List<String> imports, String currentPackage) {
        MethodMetadata metadata = buildConstructorMetadata(constructor, owningType, imports, currentPackage);
        String id = repoName + ":" + owningType.getQualifiedName() + ".<init>";
        return CodeChunk.builder().id(id).type(ChunkType.CONSTRUCTOR).repoName(repoName).content(constructor.toString())
                .parentChunkId(parentChunkId).methodMetadata(metadata).build();
    }

    private CodeChunk parseField(CtField<?> field, CtType<?> owningType, String repoName, String parentChunkId) {
        String fieldName = field.getSimpleName();
        MethodMetadata metadata = MethodMetadata.builder()
                .methodName(fieldName)
                .fullyQualifiedName(owningType.getQualifiedName() + "." + fieldName)
                .owningClass(owningType.getQualifiedName())
                .returnType(field.getType().getQualifiedName())
                .methodBody(field.toString())
                .isPublic(field.isPublic())
                .isPrivate(field.isPrivate())
                .methodSummary("Field declaration")
                .build();
        String id = repoName + ":" + owningType.getQualifiedName() + "." + fieldName + "_field";
        return CodeChunk.builder().id(id).type(ChunkType.FIELD).repoName(repoName).content(field.toString())
                .parentChunkId(parentChunkId).methodMetadata(metadata).build();
    }

    private MethodMetadata buildMethodMetadata(CtMethod<?> method, CtType<?> owningType, List<String> imports, String currentPackage) {
        List<String> calledMethods = extractCalledMethodsFromAST(method);
        Set<DependencyEdge> methodCalls = extractMethodCalls(method, imports, currentPackage);
        return MethodMetadata.builder()
                .methodName(method.getSimpleName())
                .fullyQualifiedName(owningType.getQualifiedName() + "." + method.getSimpleName())
                .owningClass(owningType.getQualifiedName())
                .parameters(method.getParameters().stream().map(p -> p.getType().getSimpleName() + " " + p.getSimpleName()).toList())
                .returnType(method.getType() != null ? method.getType().getQualifiedName() : "void")
                .annotations(method.getAnnotations().stream().map(a -> "@" + a.getAnnotationType().getSimpleName()).toList())
                .methodBody(method.toString())
                .calledMethods(calledMethods)
                .methodCalls(methodCalls)
                .thrownExceptions(method.getThrownTypes().stream().map(CtTypeReference::getQualifiedName).toList())
                .lineCount(countLines(method))
                .isPublic(method.isPublic())
                .isPrivate(method.isPrivate())
                .isStatic(method.isStatic())
                .methodSummary(extractMethodSummary(method))
                .build();
    }

    private MethodMetadata buildConstructorMetadata(CtConstructor<?> constructor, CtType<?> owningType, List<String> imports, String currentPackage) {
        return MethodMetadata.builder()
                .methodName("<init>")
                .fullyQualifiedName(owningType.getQualifiedName() + ".<init>")
                .owningClass(owningType.getQualifiedName())
                .parameters(constructor.getParameters().stream().map(p -> p.getType().getSimpleName() + " " + p.getSimpleName()).toList())
                .returnType(owningType.getQualifiedName())
                .methodBody(constructor.toString())
                .calledMethods(extractCalledMethodsFromConstructor(constructor))
                .lineCount(countLines(constructor))
                .isPublic(constructor.isPublic())
                .methodSummary("Constructor")
                .build();
    }

    private Set<DependencyEdge> extractMethodCalls(CtMethod<?> method, List<String> imports, String currentPackage) {
        if (method.getBody() == null) return Collections.emptySet();
        Set<DependencyEdge> calls = new HashSet<>();
        try {
            method.getBody().getElements(new TypeFilter<>(CtInvocation.class)).forEach(inv -> {
                if (inv.getTarget() != null && inv.getTarget().getType() != null) {
                    addEdge(calls, inv.getTarget().getType(), imports, currentPackage, DependencyEdge.RelationshipType.USES, inv.getExecutable().getSimpleName());
                }
            });
        } catch (Exception e) { /* ignore */ }
        return calls;
    }

    private List<String> extractImports(CtType<?> type) {
        try {
            if (type.getFactory().CompilationUnit().getMap().isEmpty()) return Collections.emptyList();
            return type.getFactory().CompilationUnit().getMap().values().stream()
                    .flatMap(cu -> cu.getImports().stream())
                    .map(imp -> imp.toString().replace("import ", "").replace(";", "").trim())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private List<String> extractCalledMethodsFromAST(CtMethod<?> method) {
        if (method.getBody() == null) return Collections.emptyList();
        try {
            return method.getBody().getElements(new TypeFilter<>(CtInvocation.class)).stream()
                    .map(inv -> {
                        String call = inv.getExecutable().getSimpleName();
                        if (inv.getTarget() != null) call = inv.getTarget().toString() + "." + call;
                        return call;
                    })
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private List<String> extractCalledMethodsFromConstructor(CtConstructor<?> constructor) {
        if (constructor.getBody() == null) return Collections.emptyList();
        try {
            return constructor.getBody().getElements(new TypeFilter<>(CtInvocation.class)).stream()
                    .map(inv -> inv.getExecutable().getSimpleName())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) { return Collections.emptyList(); }
    }

    private String extractMethodSummary(CtMethod<?> method) {
        return method.getComments().stream().map(CtComment::getContent).findFirst().orElse("").trim();
    }

    private int countLines(CtElement element) {
        return element.getPosition().isValidPosition() ?
                element.getPosition().getEndLine() - element.getPosition().getLine() + 1 : 0;
    }

    private String getRelativePath(File file) {
        String path = file.getAbsolutePath();
        int srcIndex = path.indexOf("src" + File.separator);
        return srcIndex >= 0 ? path.substring(srcIndex) : file.getName();
    }

    @Override
    public List<String> detectLibraries(List<String> importStatements) {
        return libraryDetectionService.detectLibraries(importStatements);
    }

    private ChunkType determineChunkType(CtType<?> type) {
        if (type.isInterface()) return ChunkType.INTERFACE;
        if (type instanceof CtEnum) return ChunkType.ENUM;
        if (type.isAnnotationType()) return ChunkType.ANNOTATION;
        return ChunkType.CLASS;
    }

    // Add this method to AstParserServiceImpl class

    /**
     * Infers knowledge graph metadata from class structure.
     * Analyzes package, class name, annotations, methods to extract semantic meaning.
     */
    private void inferKnowledgeGraph(ClassMetadata metadata, CtType<?> type) {
        // 1. Infer Domain from package name and class name
        metadata.setDomain(inferDomain(metadata));

        // 2. Infer Business Capability from annotations and interfaces
        metadata.setBusinessCapability(inferBusinessCapability(metadata, type));

        // 3. Infer Features from method names
        metadata.setFeatures(inferFeatures(type));

        // 4. Infer Concepts from annotations, interfaces, field types
        metadata.setConcepts(inferConcepts(metadata, type));
    }

    private String inferDomain(ClassMetadata metadata) {
        String packageName = metadata.getPackageName().toLowerCase();
        String className = metadata.getClassName().toLowerCase();

        // Domain keywords mapping
        if (packageName.contains("payment") || className.contains("payment")) return "payment";
        if (packageName.contains("user") || className.contains("user")) return "user-management";
        if (packageName.contains("order") || className.contains("order")) return "order-management";
        if (packageName.contains("product") || className.contains("product")) return "product-catalog";
        if (packageName.contains("inventory") || className.contains("inventory")) return "inventory";
        if (packageName.contains("shipping") || className.contains("shipping")) return "shipping";
        if (packageName.contains("notification") || className.contains("notification")) return "notification";
        if (packageName.contains("auth") || className.contains("auth")) return "authentication";
        if (packageName.contains("report") || className.contains("report")) return "reporting";
        if (packageName.contains("audit") || className.contains("audit")) return "audit";

        // Extract from package structure (e.g., com.company.payment.processor → payment)
        String[] parts = packageName.split("\\.");
        if (parts.length > 2) return parts[2]; // Assume 3rd segment is domain

        return "general";
    }

    private String inferBusinessCapability(ClassMetadata metadata, CtType<?> type) {
        List<String> annotations = metadata.getAnnotations();
        List<String> interfaces = metadata.getImplementedInterfaces();
        String className = metadata.getClassName().toLowerCase();

        // Capability from annotations
        if (annotations.contains("@RestController") || annotations.contains("@Controller")) {
            return "api-gateway";
        }
        if (annotations.contains("@Service")) {
            if (className.contains("payment")) return "transaction-processing";
            if (className.contains("user")) return "user-management";
            if (className.contains("notification")) return "notification-service";
            return "business-logic";
        }
        if (annotations.contains("@Repository")) return "data-access";
        if (annotations.contains("@Configuration")) return "configuration-management";
        if (annotations.contains("@KafkaListener")) return "event-processing";
        if (annotations.contains("@Scheduled")) return "batch-processing";

        // Capability from interfaces
        if (interfaces.stream().anyMatch(i -> i.contains("Repository"))) return "data-access";
        if (interfaces.stream().anyMatch(i -> i.contains("Validator"))) return "validation";
        if (interfaces.stream().anyMatch(i -> i.contains("Converter"))) return "data-transformation";

        // Capability from class name
        if (className.contains("processor")) return "data-processing";
        if (className.contains("handler")) return "event-handling";
        if (className.contains("manager")) return "resource-management";
        if (className.contains("client")) return "external-integration";

        return "general-service";
    }

    private List<String> inferFeatures(CtType<?> type) {
        List<String> features = new ArrayList<>();

        // Extract from method names
        for (CtMethod<?> method : type.getMethods()) {
            String methodName = method.getSimpleName().toLowerCase();

            if (methodName.contains("checkout")) features.add("checkout");
            if (methodName.contains("refund")) features.add("refunds");
            if (methodName.contains("fraud")) features.add("fraud-detection");
            if (methodName.contains("validate")) features.add("validation");
            if (methodName.contains("notify")) features.add("notifications");
            if (methodName.contains("auth")) features.add("authentication");
            if (methodName.contains("encrypt") || methodName.contains("decrypt")) features.add("encryption");
            if (methodName.contains("search")) features.add("search");
            if (methodName.contains("report")) features.add("reporting");
            if (methodName.contains("export")) features.add("data-export");
            if (methodName.contains("import")) features.add("data-import");
        }

        return features.stream().distinct().collect(Collectors.toList());
    }

    private List<String> inferConcepts(ClassMetadata metadata, CtType<?> type) {
        List<String> concepts = new ArrayList<>();
        List<String> annotations = metadata.getAnnotations();
        List<String> interfaces = metadata.getImplementedInterfaces();

        // Financial concepts
        if (metadata.getDomain().equals("payment")) {
            concepts.add("financial");
            if (annotations.contains("@Transactional")) concepts.add("transactional");
        }

        // Security concepts
        if (metadata.getClassName().toLowerCase().contains("secure") ||
                annotations.stream().anyMatch(a -> a.toLowerCase().contains("secure"))) {
            concepts.add("security");
        }
        if (interfaces.stream().anyMatch(i -> i.contains("Encrypted"))) {
            concepts.add("PCI-compliant");
        }

        // Async concepts
        if (annotations.contains("@Async") || annotations.contains("@KafkaListener")) {
            concepts.add("asynchronous");
        }

        // Transaction concepts
        if (annotations.contains("@Transactional")) {
            concepts.add("transactional");
        }

        // REST concepts
        if (annotations.contains("@RestController")) {
            concepts.add("RESTful");
        }

        // Persistence concepts
        if (annotations.contains("@Entity") || annotations.contains("@Repository")) {
            concepts.add("persistent");
        }

        // Reactive concepts
        if (metadata.getImportedClasses().stream().anyMatch(i -> i.contains("reactor") || i.contains("Mono") || i.contains("Flux"))) {
            concepts.add("reactive");
        }

        return concepts.stream().distinct().collect(Collectors.toList());
    }

// Add this call in buildClassMetadata() method, after existing metadata extraction:
// inferKnowledgeGraph(classMetadata, type);
}

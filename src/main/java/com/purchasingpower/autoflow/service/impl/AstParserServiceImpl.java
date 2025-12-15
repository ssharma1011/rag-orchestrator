package com.purchasingpower.autoflow.service.impl;

import com.purchasingpower.autoflow.enums.ChunkType;
import com.purchasingpower.autoflow.model.ast.*;
import com.purchasingpower.autoflow.service.AstParserService;
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

/**
 * AST-based Java parser using Spoon library.
 * Extracts class and method metadata for hierarchical vector storage.
 */
@Slf4j
@Service
public class AstParserServiceImpl implements AstParserService {

    private static final Map<String, List<String>> LIBRARY_PATTERNS = Map.ofEntries(
            Map.entry("Spring Framework", List.of("org.springframework")),
            Map.entry("Lombok", List.of("lombok")),
            Map.entry("JPA/Hibernate", List.of("javax.persistence", "jakarta.persistence", "org.hibernate")),
            Map.entry("Jackson", List.of("com.fasterxml.jackson")),
            Map.entry("SLF4J", List.of("org.slf4j")),
            Map.entry("JUnit", List.of("org.junit")),
            Map.entry("Mockito", List.of("org.mockito")),
            Map.entry("Apache Commons", List.of("org.apache.commons")),
            Map.entry("Google Guava", List.of("com.google.common")),
            Map.entry("Reactor", List.of("reactor.core")),
            Map.entry("WebFlux", List.of("org.springframework.web.reactive")),
            Map.entry("JGit", List.of("org.eclipse.jgit")),
            Map.entry("Pinecone", List.of("io.pinecone")),
            Map.entry("Spoon", List.of("spoon.reflect"))
    );

    @Override
    public List<CodeChunk> parseJavaFile(File javaFile, String repoName) {
        if (!javaFile.exists() || !javaFile.getName().endsWith(".java")) {
            throw new IllegalArgumentException("Not a valid Java file: " + javaFile);
        }

        log.debug("Parsing Java file: {}", javaFile.getName());

        try {
            // 1. Setup Spoon launcher
            Launcher launcher = new Launcher();
            launcher.addInputResource(javaFile.getAbsolutePath());
            launcher.getEnvironment().setNoClasspath(true); // Don't resolve dependencies
            launcher.getEnvironment().setAutoImports(false);
            launcher.getEnvironment().setCommentEnabled(true); // Capture JavaDoc
            launcher.getEnvironment().setComplianceLevel(17); // Java 17

            // 2. Build AST model
            CtModel model = launcher.buildModel();

            // 3. Extract all type declarations (classes, interfaces, enums)
            List<CtType<?>> types = model.getElements(new TypeFilter<>(CtType.class));

            List<CodeChunk> allChunks = new ArrayList<>();

            for (CtType<?> type : types) {
                // Skip anonymous classes (but include inner classes now)
                if (type.isAnonymous()) {
                    continue;
                }

                List<CodeChunk> chunks = parseType(type, repoName, javaFile);
                allChunks.addAll(chunks);
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

    /**
     * Parses a single type (class/interface/enum) and all its methods.
     * Returns: [1 parent chunk (class), N child chunks (methods + constructors + fields)]
     */
    private List<CodeChunk> parseType(CtType<?> type, String repoName, File sourceFile) {
        List<CodeChunk> chunks = new ArrayList<>();

        // 1. Build ClassMetadata (parent chunk)
        ClassMetadata classMetadata = buildClassMetadata(type, sourceFile);

        // 2. Create parent chunk
        String parentChunkId = repoName + ":" + classMetadata.getSourceFilePath();

        CodeChunk parentChunk = CodeChunk.builder()
                .id(parentChunkId)
                .type(determineChunkType(type))
                .repoName(repoName)
                .content(buildClassSummary(classMetadata))
                .parentChunkId(null) // This IS the parent
                .childChunkIds(new ArrayList<>())
                .classMetadata(classMetadata)
                .methodMetadata(null)
                .build();

        chunks.add(parentChunk);

        // 3. Extract all methods (child chunks)
        Set<CtMethod<?>> methods = type.getMethods();

        for (CtMethod<?> method : methods) {
            // Skip synthetic/implicit methods (generated by compiler)
            if (method.isImplicit()) {
                continue;
            }

            CodeChunk methodChunk = parseMethod(method, type, repoName, parentChunkId);
            chunks.add(methodChunk);

            // Link child to parent
            parentChunk.getChildChunkIds().add(methodChunk.getId());
        }

        // 4. Extract constructors using TypeFilter (universal approach)
        List<CtConstructor<?>> constructors = type.getElements(new TypeFilter<>(CtConstructor.class));

        for (CtConstructor<?> constructor : constructors) {
            if (!constructor.isImplicit()) {
                CodeChunk constructorChunk = parseConstructor(constructor, type, repoName, parentChunkId);
                chunks.add(constructorChunk);
                parentChunk.getChildChunkIds().add(constructorChunk.getId());
            }
        }

        // 5. Extract fields (important for understanding class state)
        List<CtField<?>> fields = type.getElements(new TypeFilter<>(CtField.class));

        for (CtField<?> field : fields) {
            // Skip synthetic fields
            if (field.isImplicit()) {
                continue;
            }

            CodeChunk fieldChunk = parseField(field, type, repoName, parentChunkId);
            chunks.add(fieldChunk);
            parentChunk.getChildChunkIds().add(fieldChunk.getId());
        }

        log.debug("Parsed {}: 1 class + {} methods + {} constructors + {} fields",
                classMetadata.getClassName(),
                methods.size(),
                constructors.size(),
                fields.size());

        return chunks;
    }

    /**
     * Builds class-level metadata from Spoon AST.
     */
    private ClassMetadata buildClassMetadata(CtType<?> type, File sourceFile) {
        // Get package name
        String packageName = type.getPackage() != null ?
                type.getPackage().getQualifiedName() : "";

        // Get class name
        String className = type.getSimpleName();
        String fullyQualifiedName = type.getQualifiedName();

        // Extract annotations
        List<String> annotations = type.getAnnotations().stream()
                .map(annotation -> "@" + annotation.getAnnotationType().getSimpleName())
                .collect(Collectors.toList());

        // Extract implemented interfaces
        List<String> interfaces = type.getSuperInterfaces().stream()
                .map(CtTypeReference::getQualifiedName)
                .collect(Collectors.toList());

        // Get superclass
        String superClass = null;
        if (type instanceof CtClass) {
            CtTypeReference<?> superClassRef = ((CtClass<?>) type).getSuperclass();
            if (superClassRef != null && !"java.lang.Object".equals(superClassRef.getQualifiedName())) {
                superClass = superClassRef.getQualifiedName();
            }
        }

        // Extract imports
        List<String> imports = extractImports(type);

        // Detect libraries from imports
        List<String> libraries = detectLibraries(imports);

        // Calculate line count (approximate)
        int lineCount = type.getPosition().isValidPosition() ?
                type.getPosition().getEndLine() - type.getPosition().getLine() + 1 : 0;

        // Build class summary for embedding
        String classSummary = buildClassSummaryForMetadata(type, annotations, interfaces);

        return ClassMetadata.builder()
                .fullyQualifiedName(fullyQualifiedName)
                .packageName(packageName)
                .className(className)
                .annotations(annotations)
                .implementedInterfaces(interfaces)
                .superClass(superClass)
                .importedClasses(imports)
                .usedLibraries(libraries)
                .isAbstract(type.isAbstract())
                .isInterface(type.isInterface())
                .isEnum(type instanceof CtEnum)
                .lineCount(lineCount)
                .sourceFilePath(getRelativePath(sourceFile))
                .classSummary(classSummary)
                .build();
    }

    /**
     * Creates embeddable text summary for the class chunk.
     */
    private String buildClassSummary(ClassMetadata metadata) {
        StringBuilder summary = new StringBuilder();

        summary.append("Class: ").append(metadata.getFullyQualifiedName()).append("\n");

        if (!metadata.getAnnotations().isEmpty()) {
            summary.append("Annotations: ").append(String.join(", ", metadata.getAnnotations())).append("\n");
        }

        if (!metadata.getImplementedInterfaces().isEmpty()) {
            summary.append("Implements: ").append(String.join(", ", metadata.getImplementedInterfaces())).append("\n");
        }

        if (metadata.getSuperClass() != null) {
            summary.append("Extends: ").append(metadata.getSuperClass()).append("\n");
        }

        if (!metadata.getUsedLibraries().isEmpty()) {
            summary.append("Libraries: ").append(String.join(", ", metadata.getUsedLibraries())).append("\n");
        }

        if (metadata.getClassSummary() != null) {
            summary.append("\n").append(metadata.getClassSummary());
        }

        return summary.toString();
    }

    /**
     * Builds class summary from JavaDoc and structure.
     */
    private String buildClassSummaryForMetadata(CtType<?> type, List<String> annotations, List<String> interfaces) {
        StringBuilder summary = new StringBuilder();

        // Try to get JavaDoc comment
        String comment = type.getComments().stream()
                .map(CtComment::getContent)
                .findFirst()
                .orElse("");

        if (!comment.isEmpty()) {
            summary.append(comment.trim()).append(" ");
        }

        // Infer purpose from annotations
        if (annotations.contains("@Service")) {
            summary.append("Service layer component. ");
        } else if (annotations.contains("@Controller") || annotations.contains("@RestController")) {
            summary.append("REST API controller. ");
        } else if (annotations.contains("@Repository")) {
            summary.append("Data access layer. ");
        } else if (annotations.contains("@Component")) {
            summary.append("Spring component. ");
        }

        return summary.toString().trim();
    }

    /**
     * Parses a method into a CodeChunk.
     */
    private CodeChunk parseMethod(CtMethod<?> method, CtType<?> owningType, String repoName, String parentChunkId) {
        MethodMetadata metadata = buildMethodMetadata(method, owningType);

        String methodChunkId = repoName + ":" + owningType.getSimpleName() + "." + method.getSimpleName();

        return CodeChunk.builder()
                .id(methodChunkId)
                .type(ChunkType.METHOD)
                .repoName(repoName)
                .content(method.toString()) // Full method source code
                .parentChunkId(parentChunkId)
                .childChunkIds(Collections.emptyList())
                .classMetadata(null)
                .methodMetadata(metadata)
                .build();
    }

    /**
     * Parses a constructor into a CodeChunk.
     */
    private CodeChunk parseConstructor(CtConstructor<?> constructor, CtType<?> owningType,
                                       String repoName, String parentChunkId) {
        MethodMetadata metadata = buildConstructorMetadata(constructor, owningType);

        String constructorChunkId = repoName + ":" + owningType.getSimpleName() + ".<init>";

        return CodeChunk.builder()
                .id(constructorChunkId)
                .type(ChunkType.CONSTRUCTOR)
                .repoName(repoName)
                .content(constructor.toString())
                .parentChunkId(parentChunkId)
                .childChunkIds(Collections.emptyList())
                .classMetadata(null)
                .methodMetadata(metadata)
                .build();
    }

    /**
     * Parses a field into a CodeChunk.
     * Fields are important for understanding class state and dependencies.
     */
    private CodeChunk parseField(CtField<?> field, CtType<?> owningType, String repoName, String parentChunkId) {
        String fieldName = field.getSimpleName();
        String fieldType = field.getType().getQualifiedName();

        // Extract field annotations
        List<String> annotations = field.getAnnotations().stream()
                .map(a -> "@" + a.getAnnotationType().getSimpleName())
                .collect(Collectors.toList());

        // Build field metadata (reuse MethodMetadata structure for simplicity)
        MethodMetadata metadata = MethodMetadata.builder()
                .methodName(fieldName)
                .fullyQualifiedName(owningType.getQualifiedName() + "." + fieldName)
                .owningClass(owningType.getQualifiedName())
                .parameters(Collections.emptyList())
                .returnType(fieldType)
                .annotations(annotations)
                .methodBody(field.toString())
                .calledMethods(Collections.emptyList())
                .thrownExceptions(Collections.emptyList())
                .isPublic(field.isPublic())
                .isStatic(field.isStatic())
                .isPrivate(field.isPrivate())
                .isProtected(field.isProtected())
                .lineCount(1)
                .cyclomaticComplexity(0)
                .methodSummary("Field of type " + fieldType)
                .build();

        String fieldChunkId = repoName + ":" + owningType.getSimpleName() + "." + fieldName + "_field";

        return CodeChunk.builder()
                .id(fieldChunkId)
                .type(ChunkType.FIELD)
                .repoName(repoName)
                .content(field.toString())
                .parentChunkId(parentChunkId)
                .childChunkIds(Collections.emptyList())
                .classMetadata(null)
                .methodMetadata(metadata)
                .build();
    }

    /**
     * Builds method metadata from Spoon AST.
     */
    private MethodMetadata buildMethodMetadata(CtMethod<?> method, CtType<?> owningType) {
        String methodName = method.getSimpleName();
        String fullyQualifiedName = owningType.getQualifiedName() + "." + methodName;

        // Extract parameters
        List<String> parameters = method.getParameters().stream()
                .map(param -> param.getType().getSimpleName() + " " + param.getSimpleName())
                .collect(Collectors.toList());

        // Return type
        String returnType = method.getType() != null ?
                method.getType().getQualifiedName() : "void";

        // Annotations
        List<String> annotations = method.getAnnotations().stream()
                .map(a -> "@" + a.getAnnotationType().getSimpleName())
                .collect(Collectors.toList());

        // Called methods - Extract using AST (not regex!)
        List<String> calledMethods = extractCalledMethodsFromAST(method);

        // Thrown exceptions
        List<String> exceptions = method.getThrownTypes().stream()
                .map(CtTypeReference::getQualifiedName)
                .collect(Collectors.toList());

        // Line count
        int lineCount = method.getPosition().isValidPosition() ?
                method.getPosition().getEndLine() - method.getPosition().getLine() + 1 : 0;

        return MethodMetadata.builder()
                .methodName(methodName)
                .fullyQualifiedName(fullyQualifiedName)
                .owningClass(owningType.getQualifiedName())
                .parameters(parameters)
                .returnType(returnType)
                .annotations(annotations)
                .methodBody(method.toString())
                .calledMethods(calledMethods)
                .thrownExceptions(exceptions)
                .isPublic(method.isPublic())
                .isStatic(method.isStatic())
                .isPrivate(method.isPrivate())
                .isProtected(method.isProtected())
                .lineCount(lineCount)
                .cyclomaticComplexity(0) // TODO: Calculate if needed
                .methodSummary(extractMethodSummary(method))
                .build();
    }

    /**
     * Builds constructor metadata.
     */
    private MethodMetadata buildConstructorMetadata(CtConstructor<?> constructor, CtType<?> owningType) {
        List<String> parameters = constructor.getParameters().stream()
                .map(param -> param.getType().getSimpleName() + " " + param.getSimpleName())
                .collect(Collectors.toList());

        List<String> annotations = constructor.getAnnotations().stream()
                .map(a -> "@" + a.getAnnotationType().getSimpleName())
                .collect(Collectors.toList());

        // Extract called methods from constructor body
        List<String> calledMethods = extractCalledMethodsFromConstructor(constructor);

        return MethodMetadata.builder()
                .methodName("<init>")
                .fullyQualifiedName(owningType.getQualifiedName() + ".<init>")
                .owningClass(owningType.getQualifiedName())
                .parameters(parameters)
                .returnType(owningType.getQualifiedName())
                .annotations(annotations)
                .methodBody(constructor.toString())
                .calledMethods(calledMethods)
                .thrownExceptions(Collections.emptyList())
                .isPublic(constructor.isPublic())
                .isStatic(false)
                .isPrivate(constructor.isPrivate())
                .isProtected(constructor.isProtected())
                .lineCount(0)
                .cyclomaticComplexity(0)
                .methodSummary("Constructor for " + owningType.getSimpleName())
                .build();
    }

    /**
     * Extracts import statements from a type.
     */
    private List<String> extractImports(CtType<?> type) {
        try {
            return type.getFactory().CompilationUnit().getMap().values().stream()
                    .flatMap(cu -> cu.getImports().stream())
                    .map(imp -> imp.toString().replace("import ", "").replace(";", "").trim())
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Extracts method invocations from a method using AST (proper approach).
     * This is much better than regex parsing!
     */
    private List<String> extractCalledMethodsFromAST(CtMethod<?> method) {
        if (method.getBody() == null) {
            return Collections.emptyList();
        }

        try {
            List<CtInvocation<?>> invocations = method.getBody()
                    .getElements(new TypeFilter<>(CtInvocation.class));

            return invocations.stream()
                    .map(invocation -> {
                        String methodName = invocation.getExecutable().getSimpleName();
                        // Include target if available (e.g., "repository.save" vs just "save")
                        if (invocation.getTarget() != null) {
                            return invocation.getTarget().toString() + "." + methodName;
                        }
                        return methodName;
                    })
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.debug("Failed to extract method calls from {}: {}", method.getSimpleName(), e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Extracts method invocations from constructor.
     */
    private List<String> extractCalledMethodsFromConstructor(CtConstructor<?> constructor) {
        if (constructor.getBody() == null) {
            return Collections.emptyList();
        }

        try {
            List<CtInvocation<?>> invocations = constructor.getBody()
                    .getElements(new TypeFilter<>(CtInvocation.class));

            return invocations.stream()
                    .map(invocation -> invocation.getExecutable().getSimpleName())
                    .distinct()
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Extracts method summary from JavaDoc.
     */
    private String extractMethodSummary(CtMethod<?> method) {
        return method.getComments().stream()
                .map(CtComment::getContent)
                .findFirst()
                .orElse("")
                .trim();
    }

    @Override
    public List<String> detectLibraries(List<String> importStatements) {
        Set<String> detectedLibraries = new HashSet<>();

        for (String importStmt : importStatements) {
            for (Map.Entry<String, List<String>> entry : LIBRARY_PATTERNS.entrySet()) {
                String libraryName = entry.getKey();
                List<String> patterns = entry.getValue();

                for (String pattern : patterns) {
                    if (importStmt.startsWith(pattern)) {
                        detectedLibraries.add(libraryName);
                        break;
                    }
                }
            }
        }

        return new ArrayList<>(detectedLibraries);
    }

    /**
     * Determines chunk type from Spoon type.
     */
    private ChunkType determineChunkType(CtType<?> type) {
        if (type.isInterface()) {
            return ChunkType.INTERFACE;
        } else if (type instanceof CtEnum) {
            return ChunkType.ENUM;
        } else if (type.isAnnotationType()) {
            return ChunkType.ANNOTATION;
        } else {
            return ChunkType.CLASS;
        }
    }

    /**
     * Gets relative path from project root.
     */
    private String getRelativePath(File file) {
        String path = file.getAbsolutePath();
        int srcIndex = path.indexOf("src" + File.separator);
        return srcIndex >= 0 ? path.substring(srcIndex) : file.getName();
    }
}
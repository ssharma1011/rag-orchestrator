package com.purchasingpower.autoflow.knowledge.impl;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.purchasingpower.autoflow.knowledge.DescriptionGenerator;
import com.purchasingpower.autoflow.knowledge.EmbeddingService;
import com.purchasingpower.autoflow.knowledge.JavaParserService;
import com.purchasingpower.autoflow.model.java.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JavaParser implementation for extracting Java code metadata.
 *
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JavaParserServiceImpl implements JavaParserService {

    private final DescriptionGenerator descriptionGenerator;
    private final EmbeddingService embeddingService;
    private final JavaParser parser = new JavaParser();

    @Override
    public JavaClass parseJavaFile(File file, String repositoryId) {
        log.info("📄 Parsing Java file: {}", file.getAbsolutePath());

        // Skip package-info.java files (they don't contain types)
        if (file.getName().equals("package-info.java")) {
            log.debug("⏭️  Skipping package-info.java (documentation file)");
            throw new RuntimeException("Skipping package-info.java");
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            CompilationUnit cu = parser.parse(fis)
                .getResult()
                .orElseThrow(() -> new RuntimeException("Failed to parse " + file.getName()));

            String packageName = extractPackageName(cu);
            String filePath = file.getAbsolutePath();

            // Try to find class/interface first
            var classDecl = cu.findFirst(ClassOrInterfaceDeclaration.class);
            if (classDecl.isPresent()) {
                JavaClass javaClass = buildJavaClass(classDecl.get(), packageName, filePath, repositoryId);
                log.debug("✅ Parsed class: {} with {} methods, {} fields",
                    javaClass.getFullyQualifiedName(),
                    javaClass.getMethods().size(),
                    javaClass.getFields().size());
                return javaClass;
            }

            // If no class/interface, try enum
            var enumDecl = cu.findFirst(EnumDeclaration.class);
            if (enumDecl.isPresent()) {
                JavaClass javaClass = buildJavaEnum(enumDecl.get(), packageName, filePath, repositoryId);
                log.debug("✅ Parsed enum: {} with {} constants",
                    javaClass.getFullyQualifiedName(),
                    javaClass.getFields().size());
                return javaClass;
            }

            // If no class/interface/enum found
            throw new RuntimeException("No class, interface, or enum found in " + file.getName());

        } catch (Exception e) {
            log.error("❌ Failed to parse {}: {}", file.getName(), e.getMessage(), e);
            throw new RuntimeException("Parse failed for " + file.getName(), e);
        }
    }

    @Override
    public List<JavaClass> parseJavaFiles(List<File> files, String repositoryId) {
        log.info("📂 Parsing {} Java files", files.size());
        List<JavaClass> classes = new ArrayList<>();
        List<String> failedFiles = new ArrayList<>();

        for (File file : files) {
            try {
                JavaClass javaClass = parseJavaFile(file, repositoryId);
                classes.add(javaClass);
            } catch (Exception e) {
                failedFiles.add(file.getName());
                log.error("❌ Failed to parse/embed file {}: {}",
                    file.getName(), e.getMessage(), e);
            }
        }

        log.info("✅ Successfully parsed {}/{} files", classes.size(), files.size());
        if (!failedFiles.isEmpty()) {
            log.error("❌ Failed files ({}): {}", failedFiles.size(),
                String.join(", ", failedFiles));
        }

        return classes;
    }

    private String extractPackageName(CompilationUnit cu) {
        return cu.getPackageDeclaration()
            .map(pd -> pd.getNameAsString())
            .orElse("");
    }

    private JavaClass buildJavaClass(ClassOrInterfaceDeclaration cls, String packageName,
                                     String filePath, String repositoryId) {
        String className = cls.getNameAsString();
        String fqn = packageName.isEmpty() ? className : packageName + "." + className;
        String id = UUID.randomUUID().toString();

        // First, build a temporary class without descriptions to generate enriched text
        JavaClass tempClass = JavaClass.builder()
            .id(id)
            .repositoryId(repositoryId)
            .name(className)
            .packageName(packageName)
            .fullyQualifiedName(fqn)
            .filePath(filePath)
            .startLine(cls.getBegin().map(pos -> pos.line).orElse(0))
            .endLine(cls.getEnd().map(pos -> pos.line).orElse(0))
            .kind(determineTypeKind(cls))
            .annotations(extractAnnotations(cls.getAnnotations()))
            .extendsClass(extractExtendsClass(cls))
            .implementsInterfaces(extractImplementsInterfaces(cls))
            .methods(extractMethods(cls))
            .fields(extractFields(cls))
            .description("")
            .build();

        // Generate enriched descriptions
        String classDescription = descriptionGenerator.generateClassDescription(tempClass);

        // Build methods with descriptions and embeddings
        List<JavaMethod> methodsWithDescriptionsAndEmbeddings = tempClass.getMethods().stream()
            .map(method -> {
                String methodDescription = descriptionGenerator.generateMethodDescription(method, tempClass);

                // Build temporary method to generate embedding
                JavaMethod tempMethod = JavaMethod.builder()
                    .id(method.getId())
                    .name(method.getName())
                    .signature(method.getSignature())
                    .annotations(method.getAnnotations())
                    .parameters(method.getParameters())
                    .returnType(method.getReturnType())
                    .startLine(method.getStartLine())
                    .endLine(method.getEndLine())
                    .methodCalls(method.getMethodCalls())
                    .description(methodDescription)
                    .build();

                // Generate embedding
                List<Double> methodEmbedding = embeddingService.generateMethodEmbedding(tempMethod);

                return JavaMethod.builder()
                    .id(method.getId())
                    .name(method.getName())
                    .signature(method.getSignature())
                    .annotations(method.getAnnotations())
                    .parameters(method.getParameters())
                    .returnType(method.getReturnType())
                    .startLine(method.getStartLine())
                    .endLine(method.getEndLine())
                    .methodCalls(method.getMethodCalls())
                    .description(methodDescription)
                    .embedding(methodEmbedding)
                    .build();
            })
            .collect(Collectors.toList());

        // Build class with description for embedding
        JavaClass classForEmbedding = JavaClass.builder()
            .id(id)
            .repositoryId(repositoryId)
            .name(className)
            .packageName(packageName)
            .fullyQualifiedName(fqn)
            .filePath(filePath)
            .startLine(cls.getBegin().map(pos -> pos.line).orElse(0))
            .endLine(cls.getEnd().map(pos -> pos.line).orElse(0))
            .kind(determineTypeKind(cls))
            .annotations(extractAnnotations(cls.getAnnotations()))
            .extendsClass(extractExtendsClass(cls))
            .implementsInterfaces(extractImplementsInterfaces(cls))
            .methods(methodsWithDescriptionsAndEmbeddings)
            .fields(extractFields(cls))
            .description(classDescription)
            .build();

        // Generate class embedding
        List<Double> classEmbedding = embeddingService.generateClassEmbedding(classForEmbedding);

        // Build final class with all data
        return JavaClass.builder()
            .id(id)
            .repositoryId(repositoryId)
            .name(className)
            .packageName(packageName)
            .fullyQualifiedName(fqn)
            .filePath(filePath)
            .startLine(cls.getBegin().map(pos -> pos.line).orElse(0))
            .endLine(cls.getEnd().map(pos -> pos.line).orElse(0))
            .kind(determineTypeKind(cls))
            .annotations(extractAnnotations(cls.getAnnotations()))
            .extendsClass(extractExtendsClass(cls))
            .implementsInterfaces(extractImplementsInterfaces(cls))
            .methods(methodsWithDescriptionsAndEmbeddings)
            .fields(extractFields(cls))
            .description(classDescription)
            .embedding(classEmbedding)
            .build();
    }

    private JavaClass buildJavaEnum(EnumDeclaration enumDecl, String packageName,
                                     String filePath, String repositoryId) {
        String enumName = enumDecl.getNameAsString();
        String fqn = packageName.isEmpty() ? enumName : packageName + "." + enumName;
        String id = UUID.randomUUID().toString();

        // Build temporary enum for description generation
        JavaClass tempEnum = JavaClass.builder()
            .id(id)
            .repositoryId(repositoryId)
            .name(enumName)
            .packageName(packageName)
            .fullyQualifiedName(fqn)
            .filePath(filePath)
            .startLine(enumDecl.getBegin().map(pos -> pos.line).orElse(0))
            .endLine(enumDecl.getEnd().map(pos -> pos.line).orElse(0))
            .kind(JavaTypeKind.ENUM)
            .annotations(extractAnnotations(enumDecl.getAnnotations()))
            .methods(extractEnumMethods(enumDecl))
            .fields(extractEnumConstants(enumDecl))
            .description("")
            .build();

        // Generate description
        String enumDescription = descriptionGenerator.generateClassDescription(tempEnum);

        // Build enum with description for embedding
        JavaClass enumForEmbedding = JavaClass.builder()
            .id(id)
            .repositoryId(repositoryId)
            .name(enumName)
            .packageName(packageName)
            .fullyQualifiedName(fqn)
            .filePath(filePath)
            .startLine(enumDecl.getBegin().map(pos -> pos.line).orElse(0))
            .endLine(enumDecl.getEnd().map(pos -> pos.line).orElse(0))
            .kind(JavaTypeKind.ENUM)
            .annotations(extractAnnotations(enumDecl.getAnnotations()))
            .methods(extractEnumMethods(enumDecl))
            .fields(extractEnumConstants(enumDecl))
            .description(enumDescription)
            .build();

        // Generate embedding
        List<Double> enumEmbedding = embeddingService.generateClassEmbedding(enumForEmbedding);

        // Return final enum with embedding
        return JavaClass.builder()
            .id(id)
            .repositoryId(repositoryId)
            .name(enumName)
            .packageName(packageName)
            .fullyQualifiedName(fqn)
            .filePath(filePath)
            .startLine(enumDecl.getBegin().map(pos -> pos.line).orElse(0))
            .endLine(enumDecl.getEnd().map(pos -> pos.line).orElse(0))
            .kind(JavaTypeKind.ENUM)
            .annotations(extractAnnotations(enumDecl.getAnnotations()))
            .methods(extractEnumMethods(enumDecl))
            .fields(extractEnumConstants(enumDecl))
            .description(enumDescription)
            .embedding(enumEmbedding)
            .build();
    }

    private List<JavaMethod> extractEnumMethods(EnumDeclaration enumDecl) {
        return enumDecl.getMethods().stream()
            .map(this::buildJavaMethod)
            .collect(Collectors.toList());
    }

    private List<JavaField> extractEnumConstants(EnumDeclaration enumDecl) {
        return enumDecl.getEntries().stream()
            .map(entry -> JavaField.builder()
                .id(UUID.randomUUID().toString())
                .name(entry.getNameAsString())
                .type("ENUM_CONSTANT")
                .annotations(extractAnnotations(entry.getAnnotations()))
                .lineNumber(entry.getBegin().map(pos -> pos.line).orElse(0))
                .build())
            .collect(Collectors.toList());
    }

    private JavaTypeKind determineTypeKind(ClassOrInterfaceDeclaration cls) {
        if (cls.isInterface()) {
            return JavaTypeKind.INTERFACE;
        }
        if (cls.isAnnotationDeclaration()) {
            return JavaTypeKind.ANNOTATION;
        }
        if (cls.isEnumDeclaration()) {
            return JavaTypeKind.ENUM;
        }
        return JavaTypeKind.CLASS;
    }

    private List<String> extractAnnotations(List<AnnotationExpr> annotations) {
        return annotations.stream()
            .map(ann -> "@" + ann.getNameAsString())
            .collect(Collectors.toList());
    }

    private String extractExtendsClass(ClassOrInterfaceDeclaration cls) {
        return cls.getExtendedTypes().stream()
            .findFirst()
            .map(ClassOrInterfaceType::getNameAsString)
            .orElse(null);
    }

    private List<String> extractImplementsInterfaces(ClassOrInterfaceDeclaration cls) {
        return cls.getImplementedTypes().stream()
            .map(ClassOrInterfaceType::getNameAsString)
            .collect(Collectors.toList());
    }

    private List<JavaMethod> extractMethods(ClassOrInterfaceDeclaration cls) {
        return cls.getMethods().stream()
            .map(this::buildJavaMethod)
            .collect(Collectors.toList());
    }

    private JavaMethod buildJavaMethod(MethodDeclaration method) {
        String id = UUID.randomUUID().toString();
        String signature = buildMethodSignature(method);
        List<String> methodCalls = extractMethodCalls(method);

        return JavaMethod.builder()
            .id(id)
            .name(method.getNameAsString())
            .signature(signature)
            .annotations(extractAnnotations(method.getAnnotations()))
            .parameters(extractParameters(method))
            .returnType(method.getTypeAsString())
            .startLine(method.getBegin().map(pos -> pos.line).orElse(0))
            .endLine(method.getEnd().map(pos -> pos.line).orElse(0))
            .methodCalls(methodCalls)
            .description("") // Will be populated by MethodDescriptionGenerator
            .build();
    }

    private String buildMethodSignature(MethodDeclaration method) {
        String params = method.getParameters().stream()
            .map(p -> p.getTypeAsString() + " " + p.getNameAsString())
            .collect(Collectors.joining(", "));
        return method.getTypeAsString() + " " + method.getNameAsString() + "(" + params + ")";
    }

    private List<JavaParameter> extractParameters(MethodDeclaration method) {
        return method.getParameters().stream()
            .map(p -> JavaParameter.builder()
                .name(p.getNameAsString())
                .type(p.getTypeAsString())
                .build())
            .collect(Collectors.toList());
    }

    private List<String> extractMethodCalls(MethodDeclaration method) {
        return method.findAll(MethodCallExpr.class).stream()
            .map(MethodCallExpr::getNameAsString)
            .distinct()
            .collect(Collectors.toList());
    }

    private List<JavaField> extractFields(ClassOrInterfaceDeclaration cls) {
        return cls.getFields().stream()
            .flatMap(field -> field.getVariables().stream()
                .map(var -> JavaField.builder()
                    .id(UUID.randomUUID().toString())
                    .name(var.getNameAsString())
                    .type(var.getTypeAsString())
                    .annotations(extractAnnotations(field.getAnnotations()))
                    .lineNumber(field.getBegin().map(pos -> pos.line).orElse(0))
                    .build()))
            .collect(Collectors.toList());
    }
}

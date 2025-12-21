package com.purchasingpower.autoflow.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.purchasingpower.autoflow.model.neo4j.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Extracts code entities and relationships from Java source files.
 *
 * This is the SOLUTION to the chunking problem:
 * - Parses Java code using JavaParser AST
 * - Extracts classes, methods, fields as nodes
 * - Extracts EXTENDS, CALLS, USES, etc. as relationships
 * - Preserves complete code structure for Neo4j knowledge graph
 *
 * NO MORE BROKEN CONTEXT - relationships are preserved!
 */
@Slf4j
@Component
public class EntityExtractor {

    private final JavaParser javaParser = new JavaParser();

    /**
     * Parse a Java file and extract all entities + relationships.
     *
     * @param filePath Path to Java source file
     * @return ParsedCodeGraph containing all nodes and edges
     */
    public ParsedCodeGraph extractFromFile(Path filePath) {
        log.info("Extracting entities from: {}", filePath);

        ParsedCodeGraph.ParsedCodeGraphBuilder builder = ParsedCodeGraph.builder()
                .sourceFilePath(filePath.toString());

        try {
            // Parse Java file
            ParseResult<CompilationUnit> parseResult = javaParser.parse(filePath);

            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                builder.errors(parseResult.getProblems().stream()
                        .map(Object::toString)
                        .collect(Collectors.toList()));
                return builder.build();
            }

            CompilationUnit cu = parseResult.getResult().get();
            String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

            // Extract classes/interfaces
            List<ClassNode> classes = new ArrayList<>();
            List<MethodNode> methods = new ArrayList<>();
            List<FieldNode> fields = new ArrayList<>();
            List<CodeRelationship> relationships = new ArrayList<>();

            // Visit all class declarations
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                ClassNode classNode = extractClass(classDecl, packageName, filePath.toString());
                classes.add(classNode);

                // Extract class-level relationships
                relationships.addAll(extractClassRelationships(classDecl, classNode.getId(), filePath.toString()));

                // Extract methods
                classDecl.getMethods().forEach(methodDecl -> {
                    MethodNode methodNode = extractMethod(methodDecl, classNode.getFullyQualifiedName(), filePath.toString());
                    methods.add(methodNode);

                    // Add HAS_METHOD relationship
                    relationships.add(CodeRelationship.builder()
                            .fromId(classNode.getId())
                            .toId(methodNode.getId())
                            .type(CodeRelationship.RelationType.HAS_METHOD)
                            .sourceFile(filePath.toString())
                            .lineNumber(methodDecl.getBegin().map(pos -> pos.line).orElse(null))
                            .build());

                    // Extract method-level relationships (calls, uses, etc.)
                    relationships.addAll(extractMethodRelationships(methodDecl, methodNode, filePath.toString()));
                });

                // Extract fields
                classDecl.getFields().forEach(fieldDecl -> {
                    fieldDecl.getVariables().forEach(variable -> {
                        FieldNode fieldNode = extractField(variable, fieldDecl, classNode.getFullyQualifiedName(), filePath.toString());
                        fields.add(fieldNode);

                        // Add HAS_FIELD relationship
                        relationships.add(CodeRelationship.builder()
                                .fromId(classNode.getId())
                                .toId(fieldNode.getId())
                                .type(CodeRelationship.RelationType.HAS_FIELD)
                                .sourceFile(filePath.toString())
                                .lineNumber(variable.getBegin().map(pos -> pos.line).orElse(null))
                                .build());

                        // Add TYPE_DEPENDENCY relationship
                        String fieldType = variable.getTypeAsString();
                        relationships.add(CodeRelationship.builder()
                                .fromId(classNode.getId())
                                .toId("CLASS:" + resolveType(fieldType, packageName))
                                .type(CodeRelationship.RelationType.TYPE_DEPENDENCY)
                                .sourceFile(filePath.toString())
                                .lineNumber(variable.getBegin().map(pos -> pos.line).orElse(null))
                                .build());
                    });
                });

                // Extract constructors
                classDecl.getConstructors().forEach(constructorDecl -> {
                    MethodNode constructorNode = extractConstructor(constructorDecl, classNode.getFullyQualifiedName(), filePath.toString());
                    methods.add(constructorNode);

                    relationships.add(CodeRelationship.builder()
                            .fromId(classNode.getId())
                            .toId(constructorNode.getId())
                            .type(CodeRelationship.RelationType.HAS_CONSTRUCTOR)
                            .sourceFile(filePath.toString())
                            .lineNumber(constructorDecl.getBegin().map(pos -> pos.line).orElse(null))
                            .build());
                });
            });

            // Extract enums
            cu.findAll(EnumDeclaration.class).forEach(enumDecl -> {
                ClassNode enumNode = extractEnum(enumDecl, packageName, filePath.toString());
                classes.add(enumNode);
            });

            return builder
                    .classes(classes)
                    .methods(methods)
                    .fields(fields)
                    .relationships(relationships)
                    .build();

        } catch (IOException e) {
            log.error("Failed to parse file: {}", filePath, e);
            builder.errors(List.of("IOException: " + e.getMessage()));
            return builder.build();
        }
    }

    /**
     * Extract ClassNode from ClassOrInterfaceDeclaration
     */
    private ClassNode extractClass(ClassOrInterfaceDeclaration classDecl, String packageName, String sourceFile) {
        String className = classDecl.getNameAsString();
        String fqn = packageName.isEmpty() ? className : packageName + "." + className;

        ClassNode.ClassNodeBuilder builder = ClassNode.builder()
                .id("CLASS:" + fqn)
                .name(className)
                .fullyQualifiedName(fqn)
                .packageName(packageName)
                .sourceFilePath(sourceFile)
                .startLine(classDecl.getBegin().map(pos -> pos.line).orElse(0))
                .endLine(classDecl.getEnd().map(pos -> pos.line).orElse(0))
                .classType(classDecl.isInterface() ? ClassNode.ClassType.INTERFACE :
                          classDecl.isAbstract() ? ClassNode.ClassType.ABSTRACT_CLASS :
                          ClassNode.ClassType.CLASS)
                .isAbstract(classDecl.isAbstract())
                .isFinal(classDecl.isFinal())
                .isStatic(classDecl.isStatic())
                .accessModifier(classDecl.getAccessSpecifier().asString())
                .sourceCode(classDecl.toString());

        // Extract superclass
        classDecl.getExtendedTypes().forEach(extendedType -> {
            builder.superClassName(extendedType.getNameAsString());
        });

        // Extract interfaces
        List<String> interfaces = classDecl.getImplementedTypes().stream()
                .map(ClassOrInterfaceType::getNameAsString)
                .collect(Collectors.toList());
        builder.interfaces(interfaces);

        // Extract annotations
        List<String> annotations = classDecl.getAnnotations().stream()
                .map(ann -> ann.getNameAsString())
                .collect(Collectors.toList());
        builder.annotations(annotations);

        // Extract Javadoc
        classDecl.getJavadoc().ifPresent(javadoc -> builder.javadoc(javadoc.toText()));

        return builder.build();
    }

    /**
     * Extract class-level relationships (EXTENDS, IMPLEMENTS)
     */
    private List<CodeRelationship> extractClassRelationships(ClassOrInterfaceDeclaration classDecl,
                                                               String classId,
                                                               String sourceFile) {
        List<CodeRelationship> relationships = new ArrayList<>();

        // EXTENDS relationship
        classDecl.getExtendedTypes().forEach(extendedType -> {
            relationships.add(CodeRelationship.builder()
                    .fromId(classId)
                    .toId("CLASS:" + extendedType.getNameAsString())
                    .type(CodeRelationship.RelationType.EXTENDS)
                    .sourceFile(sourceFile)
                    .lineNumber(classDecl.getBegin().map(pos -> pos.line).orElse(null))
                    .build());
        });

        // IMPLEMENTS relationship
        classDecl.getImplementedTypes().forEach(implementedType -> {
            relationships.add(CodeRelationship.builder()
                    .fromId(classId)
                    .toId("CLASS:" + implementedType.getNameAsString())
                    .type(CodeRelationship.RelationType.IMPLEMENTS)
                    .sourceFile(sourceFile)
                    .lineNumber(classDecl.getBegin().map(pos -> pos.line).orElse(null))
                    .build());
        });

        return relationships;
    }

    /**
     * Extract MethodNode from MethodDeclaration
     */
    private MethodNode extractMethod(MethodDeclaration methodDecl, String className, String sourceFile) {
        String methodName = methodDecl.getNameAsString();
        String signature = methodName + "(" + methodDecl.getParameters().stream()
                .map(param -> param.getType().asString())
                .collect(Collectors.joining(",")) + ")";
        String fqn = className + "." + signature;

        MethodNode.MethodNodeBuilder builder = MethodNode.builder()
                .id("METHOD:" + fqn)
                .name(methodName)
                .fullyQualifiedName(fqn)
                .className(className)
                .sourceFilePath(sourceFile)
                .startLine(methodDecl.getBegin().map(pos -> pos.line).orElse(0))
                .endLine(methodDecl.getEnd().map(pos -> pos.line).orElse(0))
                .returnType(methodDecl.getType().asString())
                .accessModifier(methodDecl.getAccessSpecifier().asString())
                .isStatic(methodDecl.isStatic())
                .isFinal(methodDecl.isFinal())
                .isAbstract(methodDecl.isAbstract())
                .isSynchronized(methodDecl.isSynchronized())
                .isConstructor(false)
                .sourceCode(methodDecl.toString());

        // Extract parameters
        List<MethodNode.MethodParameter> params = methodDecl.getParameters().stream()
                .map(param -> MethodNode.MethodParameter.builder()
                        .name(param.getNameAsString())
                        .type(param.getType().asString())
                        .isFinal(param.isFinal())
                        .annotations(param.getAnnotations().stream()
                                .map(ann -> ann.getNameAsString())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());
        builder.parameters(params);

        // Extract thrown exceptions
        List<String> exceptions = methodDecl.getThrownExceptions().stream()
                .map(ex -> ex.asString())
                .collect(Collectors.toList());
        builder.thrownExceptions(exceptions);

        // Extract annotations
        List<String> annotations = methodDecl.getAnnotations().stream()
                .map(ann -> ann.getNameAsString())
                .collect(Collectors.toList());
        builder.annotations(annotations);

        // Extract Javadoc
        methodDecl.getJavadoc().ifPresent(javadoc -> builder.javadoc(javadoc.toText()));

        return builder.build();
    }

    /**
     * Extract method-level relationships (CALLS, USES, INSTANTIATES)
     */
    private List<CodeRelationship> extractMethodRelationships(MethodDeclaration methodDecl,
                                                                MethodNode methodNode,
                                                                String sourceFile) {
        List<CodeRelationship> relationships = new ArrayList<>();

        // Find all method calls (CALLS relationship)
        methodDecl.findAll(MethodCallExpr.class).forEach(call -> {
            String calledMethod = call.getNameAsString();
            relationships.add(CodeRelationship.builder()
                    .fromId(methodNode.getId())
                    .toId("METHOD:" + calledMethod)  // Simplified - would need symbol resolution for FQN
                    .type(CodeRelationship.RelationType.CALLS)
                    .sourceFile(sourceFile)
                    .lineNumber(call.getBegin().map(pos -> pos.line).orElse(null))
                    .build());
        });

        // Find all object creations (INSTANTIATES relationship)
        methodDecl.findAll(ObjectCreationExpr.class).forEach(creation -> {
            String createdType = creation.getType().getNameAsString();
            relationships.add(CodeRelationship.builder()
                    .fromId(methodNode.getId())
                    .toId("CLASS:" + createdType)
                    .type(CodeRelationship.RelationType.INSTANTIATES)
                    .sourceFile(sourceFile)
                    .lineNumber(creation.getBegin().map(pos -> pos.line).orElse(null))
                    .build());
        });

        return relationships;
    }

    /**
     * Extract FieldNode from VariableDeclarator
     */
    private FieldNode extractField(VariableDeclarator variable, FieldDeclaration fieldDecl,
                                     String className, String sourceFile) {
        String fieldName = variable.getNameAsString();
        String fqn = className + "." + fieldName;

        FieldNode.FieldNodeBuilder builder = FieldNode.builder()
                .id("FIELD:" + fqn)
                .name(fieldName)
                .fullyQualifiedName(fqn)
                .className(className)
                .sourceFilePath(sourceFile)
                .lineNumber(variable.getBegin().map(pos -> pos.line).orElse(0))
                .type(variable.getType().asString())
                .accessModifier(fieldDecl.getAccessSpecifier().asString())
                .isStatic(fieldDecl.isStatic())
                .isFinal(fieldDecl.isFinal())
                .isTransient(fieldDecl.isTransient())
                .isVolatile(fieldDecl.isVolatile());

        // Extract annotations
        List<String> annotations = fieldDecl.getAnnotations().stream()
                .map(ann -> ann.getNameAsString())
                .collect(Collectors.toList());
        builder.annotations(annotations);

        // Extract initial value
        variable.getInitializer().ifPresent(init -> builder.initialValue(init.toString()));

        // Extract Javadoc
        fieldDecl.getJavadoc().ifPresent(javadoc -> builder.javadoc(javadoc.toText()));

        return builder.build();
    }

    /**
     * Extract MethodNode from ConstructorDeclaration
     */
    private MethodNode extractConstructor(ConstructorDeclaration constructorDecl, String className, String sourceFile) {
        String signature = "<init>(" + constructorDecl.getParameters().stream()
                .map(param -> param.getType().asString())
                .collect(Collectors.joining(",")) + ")";
        String fqn = className + "." + signature;

        return MethodNode.builder()
                .id("METHOD:" + fqn)
                .name("<init>")
                .fullyQualifiedName(fqn)
                .className(className)
                .sourceFilePath(sourceFile)
                .startLine(constructorDecl.getBegin().map(pos -> pos.line).orElse(0))
                .endLine(constructorDecl.getEnd().map(pos -> pos.line).orElse(0))
                .returnType("void")
                .accessModifier(constructorDecl.getAccessSpecifier().asString())
                .isConstructor(true)
                .sourceCode(constructorDecl.toString())
                .build();
    }

    /**
     * Extract ClassNode from EnumDeclaration
     */
    private ClassNode extractEnum(EnumDeclaration enumDecl, String packageName, String sourceFile) {
        String enumName = enumDecl.getNameAsString();
        String fqn = packageName.isEmpty() ? enumName : packageName + "." + enumName;

        return ClassNode.builder()
                .id("CLASS:" + fqn)
                .name(enumName)
                .fullyQualifiedName(fqn)
                .packageName(packageName)
                .sourceFilePath(sourceFile)
                .startLine(enumDecl.getBegin().map(pos -> pos.line).orElse(0))
                .endLine(enumDecl.getEnd().map(pos -> pos.line).orElse(0))
                .classType(ClassNode.ClassType.ENUM)
                .accessModifier(enumDecl.getAccessSpecifier().asString())
                .sourceCode(enumDecl.toString())
                .build();
    }

    /**
     * Resolve type name to fully qualified name (simplified version)
     * In production, use JavaParser's symbol resolution for accurate FQNs
     */
    private String resolveType(String typeName, String packageName) {
        // If already fully qualified
        if (typeName.contains(".")) {
            return typeName;
        }

        // Java built-in types
        if (typeName.matches("int|long|double|float|boolean|byte|short|char")) {
            return "java.lang." + typeName;
        }

        // Assume same package for now (would need symbol resolver for accuracy)
        return packageName.isEmpty() ? typeName : packageName + "." + typeName;
    }
}

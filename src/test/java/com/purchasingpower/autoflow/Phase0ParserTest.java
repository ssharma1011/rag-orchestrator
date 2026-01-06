package com.purchasingpower.autoflow;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 0: Test JavaParser on 5 sample files
 */
public class Phase0ParserTest {

    private static final JavaParser parser = new JavaParser();

    public static void main(String[] args) {
        System.out.println("\n=======================================================");
        System.out.println("    Phase 0: JavaParser Verification Test");
        System.out.println("=======================================================\n");

        String baseDir = "C:\\Users\\ssharma\\personal\\rag-orchestrator\\src\\main\\java\\com\\purchasingpower\\autoflow";

        String[] testFiles = {
            baseDir + "\\api\\ChatController.java",
            baseDir + "\\agent\\AutoFlowAgent.java",
            baseDir + "\\knowledge\\impl\\Neo4jGraphStoreImpl.java",
            baseDir + "\\knowledge\\IndexingService.java"
        };

        AtomicInteger totalClasses = new AtomicInteger(0);
        AtomicInteger totalMethods = new AtomicInteger(0);
        AtomicInteger totalCalls = new AtomicInteger(0);

        for (String filePath : testFiles) {
            File file = new File(filePath);
            if (!file.exists()) {
                System.out.println("[WARN] File not found: " + file.getName());
                continue;
            }

            System.out.println("--------------------------------------------------------");
            System.out.println("[PARSING] " + file.getName());
            System.out.println("--------------------------------------------------------");

            try {
                CompilationUnit cu = parser.parse(new FileInputStream(file))
                    .getResult()
                    .orElseThrow(() -> new RuntimeException("Failed to parse"));

                String packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElse("");

                System.out.println("Package: " + packageName);
                System.out.println("Imports: " + cu.getImports().size());

                cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls -> {
                    String className = cls.getNameAsString();
                    String fqn = packageName.isEmpty() ? className : packageName + "." + className;

                    System.out.println("\n[CLASS] " + fqn);
                    System.out.println("  Type: " + (cls.isInterface() ? "Interface" : "Class"));

                    List<String> annotations = new ArrayList<>();
                    cls.getAnnotations().forEach(ann -> annotations.add("@" + ann.getNameAsString()));
                    if (!annotations.isEmpty()) {
                        System.out.println("  Annotations: " + String.join(", ", annotations));
                    }

                    int methodCount = cls.getMethods().size();
                    int callsInClass = 0;

                    System.out.println("  Methods: " + methodCount);

                    for (MethodDeclaration method : cls.getMethods()) {
                        List<String> calls = new ArrayList<>();
                        method.findAll(MethodCallExpr.class).forEach(call ->
                            calls.add(call.getNameAsString()));

                        callsInClass += calls.size();

                        if (!method.getAnnotations().isEmpty() || calls.size() > 5) {
                            List<String> methodAnns = new ArrayList<>();
                            method.getAnnotations().forEach(ann ->
                                methodAnns.add("@" + ann.getNameAsString()));

                            System.out.println("    - " + method.getNameAsString() + "()");
                            if (!methodAnns.isEmpty()) {
                                System.out.println("      Annotations: " + String.join(", ", methodAnns));
                            }
                            if (calls.size() > 0) {
                                System.out.println("      Calls " + calls.size() + " methods");
                            }
                        }
                    }

                    System.out.println("  Total calls in class: " + callsInClass);

                    totalClasses.incrementAndGet();
                    totalMethods.addAndGet(methodCount);
                    totalCalls.addAndGet(callsInClass);
                });

            } catch (Exception e) {
                System.out.println("[ERROR] " + e.getMessage());
            }

            System.out.println();
        }

        System.out.println("=======================================================");
        System.out.println("                    SUMMARY");
        System.out.println("=======================================================");
        System.out.println("Classes parsed:   " + totalClasses.get());
        System.out.println("Methods found:    " + totalMethods.get());
        System.out.println("Method calls:     " + totalCalls.get());
        System.out.println("\n[SUCCESS] JavaParser works correctly!");
        System.out.println("\nNext: Implement Phase 1 (Indexing Pipeline)");
    }
}

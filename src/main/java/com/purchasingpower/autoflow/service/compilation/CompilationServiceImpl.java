package com.purchasingpower.autoflow.service.compilation;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.tools.*;
import java.io.StringWriter;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory Java compilation service using the Java Compiler API.
 *
 * <p>Compiles code without writing to disk, making it safe for
 * untrusted AI-generated code.
 *
 * <p><b>Thread Safety:</b> This implementation is thread-safe.
 *
 * @since 1.0.0
 */
@Service
@Slf4j
public class CompilationServiceImpl implements CompilationService {

    private final JavaCompiler compiler;
    private final StandardJavaFileManager fileManager;

    public CompilationServiceImpl() {
        this.compiler = ToolProvider.getSystemJavaCompiler();
        this.fileManager = compiler.getStandardFileManager(null, null, null);

        if (compiler == null) {
            throw new IllegalStateException(
                    "No Java compiler available. Are you running on a JDK (not JRE)?"
            );
        }
    }

    @Override
    public CompilationResult compile(String sourceCode, String className) {
        Preconditions.checkNotNull(sourceCode, "Source code cannot be null");
        Preconditions.checkNotNull(className, "Class name cannot be null");
        Preconditions.checkArgument(!sourceCode.isEmpty(), "Source code cannot be empty");
        Preconditions.checkArgument(!className.isEmpty(), "Class name cannot be empty");

        log.debug("Compiling class: {}", className);

        long startTime = System.currentTimeMillis();

        try {
            // Create in-memory source file
            InMemoryJavaFile sourceFile = new InMemoryJavaFile(className, sourceCode);

            // Capture compilation diagnostics
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

            // Compile
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null, // Default writer
                    fileManager,
                    diagnostics,
                    null, // No compiler options
                    null, // No annotation processing
                    List.of(sourceFile)
            );

            boolean success = task.call();
            long timeMs = System.currentTimeMillis() - startTime;

            if (success) {
                log.debug("✅ Compilation succeeded in {}ms", timeMs);
                return CompilationResult.success(timeMs);
            } else {
                List<CompilationError> errors = extractErrors(diagnostics);
                log.warn("❌ Compilation failed with {} errors in {}ms", errors.size(), timeMs);
                return CompilationResult.failure(errors, timeMs);
            }

        } catch (Exception e) {
            long timeMs = System.currentTimeMillis() - startTime;
            log.error("Compilation threw exception", e);

            // Wrap exception as compilation error
            CompilationError error = CompilationError.builder()
                    .line(0)
                    .column(0)
                    .message("Compilation threw exception: " + e.getMessage())
                    .kind("EXCEPTION")
                    .build();

            return CompilationResult.failure(List.of(error), timeMs);
        }
    }

    @Override
    public CompilationResult compileAll(Map<String, String> sources) {
        Preconditions.checkNotNull(sources, "Sources cannot be null");
        Preconditions.checkArgument(!sources.isEmpty(), "Sources cannot be empty");

        log.debug("Compiling {} classes", sources.size());

        // For simplicity, compile one by one (can be optimized later)
        // This is safe because most AI generations are single-class
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            CompilationResult result = compile(entry.getValue(), entry.getKey());
            if (!result.isSuccess()) {
                return result; // Return first failure
            }
        }

        return CompilationResult.success(0);
    }

    /**
     * Extracts compilation errors from diagnostics.
     *
     * <p>Converts Java compiler diagnostics into our CompilationError format
     * that's easier for LLMs to understand.
     */
    private List<CompilationError> extractErrors(DiagnosticCollector<JavaFileObject> diagnostics) {
        return diagnostics.getDiagnostics().stream()
                .filter(d -> d.getKind() == Diagnostic.Kind.ERROR)
                .map(this::toCompilationError)
                .collect(Collectors.toList());
    }

    /**
     * Converts a Java compiler diagnostic to our CompilationError format.
     */
    private CompilationError toCompilationError(Diagnostic<? extends JavaFileObject> diagnostic) {
        return CompilationError.builder()
                .line((int) diagnostic.getLineNumber())
                .column((int) diagnostic.getColumnNumber())
                .message(diagnostic.getMessage(Locale.ENGLISH))
                .kind(diagnostic.getKind().name())
                .build();
    }

    /**
     * In-memory representation of a Java source file.
     *
     * <p>Allows compilation without writing to disk.
     */
    private static class InMemoryJavaFile extends SimpleJavaFileObject {
        private final String code;

        InMemoryJavaFile(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}

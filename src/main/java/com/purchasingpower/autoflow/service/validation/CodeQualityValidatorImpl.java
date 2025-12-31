package com.purchasingpower.autoflow.service.validation;

import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates code quality using regex-based heuristics.
 *
 * <p>This is a lightweight validator focused on critical violations.
 * For comprehensive analysis, use Checkstyle/PMD in the build pipeline.
 *
 * @since 1.0.0
 */
@Slf4j
@Service
public class CodeQualityValidatorImpl implements CodeQualityValidator {

    private static final int MAX_METHOD_LINES = 20;
    private static final int MAX_COMPLEXITY_ESTIMATE = 10;
    private static final int MAX_IF_ELSE_CHAIN = 2;

    @Override
    public ValidationResult validate(String sourceCode) {
        Preconditions.checkNotNull(sourceCode, "Source code cannot be null");
        Preconditions.checkArgument(!sourceCode.isEmpty(), "Source code cannot be empty");

        List<ValidationResult.Violation> violations = new ArrayList<>();

        // Check method length
        violations.addAll(checkMethodLength(sourceCode));

        // Check if-else chains
        violations.addAll(checkIfElseChains(sourceCode));

        // Check for null returns (basic heuristic)
        violations.addAll(checkNullReturns(sourceCode));

        return violations.isEmpty()
                ? ValidationResult.success()
                : ValidationResult.failure(violations);
    }

    @Override
    public boolean hasCriticalViolations(String sourceCode) {
        ValidationResult result = validate(sourceCode);
        return !result.isValid() && result.getViolations().stream()
                .anyMatch(v -> "error".equals(v.getSeverity()));
    }

    /**
     * Checks method length by counting lines between method signature and closing brace.
     */
    private List<ValidationResult.Violation> checkMethodLength(String sourceCode) {
        List<ValidationResult.Violation> violations = new ArrayList<>();

        // Simple heuristic: Find method signatures and count lines until closing brace
        Pattern methodPattern = Pattern.compile(
            "(public|private|protected)\\s+(?:static\\s+)?[\\w<>,\\[\\]]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*\\{",
            Pattern.MULTILINE
        );

        Matcher matcher = methodPattern.matcher(sourceCode);
        while (matcher.find()) {
            String methodName = matcher.group(2);
            int start = matcher.end();
            int braceDepth = 1;
            int lineCount = 0;

            // Count lines until matching closing brace
            String remaining = sourceCode.substring(start);
            String[] lines = remaining.split("\n");

            for (String line : lines) {
                lineCount++;
                for (char c : line.toCharArray()) {
                    if (c == '{') braceDepth++;
                    if (c == '}') braceDepth--;
                }
                if (braceDepth == 0) break;
            }

            if (lineCount > MAX_METHOD_LINES) {
                violations.add(ValidationResult.Violation.builder()
                        .rule("max-method-lines")
                        .severity("error")
                        .location(String.format("Method %s()", methodName))
                        .message(String.format("Method has %d lines (max is %d)", lineCount, MAX_METHOD_LINES))
                        .suggestion("Split into smaller methods or extract helper functions")
                        .build());
            }
        }

        return violations;
    }

    /**
     * Checks for if-else chains with 3+ branches.
     */
    private List<ValidationResult.Violation> checkIfElseChains(String sourceCode) {
        List<ValidationResult.Violation> violations = new ArrayList<>();

        // Find if-else chains
        Pattern ifElsePattern = Pattern.compile(
            "if\\s*\\([^)]+\\)\\s*\\{[^}]+}\\s*else\\s+if\\s*\\([^)]+\\)\\s*\\{[^}]+}\\s*else\\s+if",
            Pattern.DOTALL
        );

        Matcher matcher = ifElsePattern.matcher(sourceCode);
        if (matcher.find()) {
            violations.add(ValidationResult.Violation.builder()
                    .rule("no-if-else-chains")
                    .severity("error")
                    .location("Method contains if-else chain")
                    .message("Found if-else chain with 3+ branches")
                    .suggestion("Refactor using Strategy pattern: Map<Type, Strategy> strategies")
                    .build());
        }

        return violations;
    }

    /**
     * Checks for methods returning null instead of Optional.
     */
    private List<ValidationResult.Violation> checkNullReturns(String sourceCode) {
        List<ValidationResult.Violation> violations = new ArrayList<>();

        // Find "return null;" statements
        Pattern nullReturnPattern = Pattern.compile("return\\s+null\\s*;");
        Matcher matcher = nullReturnPattern.matcher(sourceCode);

        if (matcher.find()) {
            violations.add(ValidationResult.Violation.builder()
                    .rule("no-null-returns")
                    .severity("warning")
                    .location("Method returns null")
                    .message("Method returns null instead of Optional")
                    .suggestion("Change return type to Optional<T> and return Optional.empty()")
                    .build());
        }

        return violations;
    }
}

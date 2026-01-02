/**
 * Action engine: code generation, modification, and PR creation.
 *
 * <p>Handles all write operations on repositories:
 * <ul>
 *   <li>Code generation - Creating new files and code</li>
 *   <li>Code modification - Editing existing code</li>
 *   <li>Git operations - Branches, commits, pushes</li>
 *   <li>PR management - Creating and updating pull requests</li>
 * </ul>
 *
 * <p>Key classes:
 * <ul>
 *   <li>{@code CodeGenerator} - LLM-based code generation</li>
 *   <li>{@code CodeModifier} - Safe code modification with validation</li>
 *   <li>{@code GitOperations} - Git command execution</li>
 *   <li>{@code PullRequestManager} - PR lifecycle management</li>
 * </ul>
 *
 * @since 2.0.0
 */
package com.purchasingpower.autoflow.action;

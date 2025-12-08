package com.purchasingpower.autoflow.workflow.pipeline;


/**
 * Represents a single, isolated unit of work in the automation process.
 * Implementations are automatically detected by Spring and sorted by @Order.
 */
public interface PipelineStep {

    /**
     * Executes the logic for this specific step.
     *
     * @param context The shared state object containing all data gathered so far.
     * @throws RuntimeException if the step fails and the pipeline should abort.
     */
    void execute(PipelineContext context);
}
package com.purchasingpower.autoflow.model.conversation;

public enum ConversationState {
    /**
     * LLM is analyzing the codebase
     */
    INITIAL_ANALYSIS,

    /**
     * LLM has asked clarifying questions, awaiting developer response
     */
    AWAITING_CLARIFICATION,

    /**
     * LLM is generating refactoring plan
     */
    GENERATING_PLAN,

    /**
     * Plan has been posted, awaiting developer approval
     */
    AWAITING_APPROVAL,

    /**
     * Plan approved, LLM is generating code
     */
    GENERATING_CODE,

    /**
     * PR created, conversation complete
     */
    COMPLETED,

    /**
     * Error occurred, conversation terminated
     */
    ERROR
}
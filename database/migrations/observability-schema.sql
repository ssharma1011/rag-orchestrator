-- =====================================================================
-- OBSERVABILITY & AUDIT LOGGING SCHEMA
-- Purpose: Track all system interactions for debugging, optimization, and compliance
-- =====================================================================

-- =====================================================================
-- 1. AGENT_INTERACTIONS: Track every agent execution
-- =====================================================================
CREATE TABLE AGENT_INTERACTIONS (
    interaction_id VARCHAR2(100) PRIMARY KEY,
    conversation_id VARCHAR2(100) NOT NULL,
    workflow_id VARCHAR2(100),
    agent_name VARCHAR2(50) NOT NULL,  -- requirement_analyzer, code_indexer, documentation_agent, etc.

    -- Timing
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms NUMBER,

    -- Input/Output
    input_state CLOB,  -- JSON of WorkflowState before agent execution
    output_state CLOB, -- JSON of WorkflowState after agent execution
    agent_decision CLOB, -- JSON of AgentDecision

    -- Status
    status VARCHAR2(20) CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'TIMEOUT')),
    error_message CLOB,
    error_stack_trace CLOB,

    -- Metrics
    tokens_used NUMBER,
    cost_usd NUMBER(10, 6),

    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Indexes
    CONSTRAINT fk_agent_conversation FOREIGN KEY (conversation_id)
        REFERENCES CONVERSATIONS(conversation_id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_conv ON AGENT_INTERACTIONS(conversation_id);
CREATE INDEX idx_agent_workflow ON AGENT_INTERACTIONS(workflow_id);
CREATE INDEX idx_agent_name ON AGENT_INTERACTIONS(agent_name);
CREATE INDEX idx_agent_started ON AGENT_INTERACTIONS(started_at);
CREATE INDEX idx_agent_status ON AGENT_INTERACTIONS(status);

COMMENT ON TABLE AGENT_INTERACTIONS IS 'Audit log of all agent executions for debugging and optimization';

-- =====================================================================
-- 2. LLM_REQUESTS: Track every LLM API call
-- =====================================================================
CREATE TABLE LLM_REQUESTS (
    request_id VARCHAR2(100) PRIMARY KEY,
    interaction_id VARCHAR2(100),  -- Links to AGENT_INTERACTIONS
    conversation_id VARCHAR2(100) NOT NULL,

    -- LLM Details
    provider VARCHAR2(50) NOT NULL,  -- gemini, openai, anthropic, etc.
    model VARCHAR2(100) NOT NULL,    -- gemini-1.5-pro, gpt-4, claude-3-opus, etc.
    request_type VARCHAR2(50),       -- text_generation, embedding, chat_completion

    -- Timing
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms NUMBER,

    -- Request/Response
    prompt CLOB NOT NULL,
    prompt_tokens NUMBER,
    response CLOB,
    response_tokens NUMBER,
    total_tokens NUMBER,

    -- Cost
    cost_usd NUMBER(10, 6),

    -- Status
    status VARCHAR2(20) CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'TIMEOUT', 'RATE_LIMITED')),
    error_message CLOB,
    http_status_code NUMBER,

    -- Metadata
    temperature NUMBER(3, 2),
    max_tokens NUMBER,

    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Indexes
    CONSTRAINT fk_llm_interaction FOREIGN KEY (interaction_id)
        REFERENCES AGENT_INTERACTIONS(interaction_id) ON DELETE CASCADE
);

CREATE INDEX idx_llm_interaction ON LLM_REQUESTS(interaction_id);
CREATE INDEX idx_llm_conv ON LLM_REQUESTS(conversation_id);
CREATE INDEX idx_llm_provider ON LLM_REQUESTS(provider, model);
CREATE INDEX idx_llm_started ON LLM_REQUESTS(started_at);
CREATE INDEX idx_llm_status ON LLM_REQUESTS(status);

COMMENT ON TABLE LLM_REQUESTS IS 'Audit log of all LLM API calls for cost tracking and prompt optimization';

-- =====================================================================
-- 3. RETRIEVAL_LOGS: Track RAG retrieval operations
-- =====================================================================
CREATE TABLE RETRIEVAL_LOGS (
    retrieval_id VARCHAR2(100) PRIMARY KEY,
    interaction_id VARCHAR2(100),
    conversation_id VARCHAR2(100) NOT NULL,

    -- Retrieval Details
    retrieval_type VARCHAR2(50) NOT NULL,  -- pinecone_vector, neo4j_graph, hybrid
    query_text CLOB NOT NULL,

    -- Timing
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    duration_ms NUMBER,

    -- Vector Search (Pinecone)
    embedding_vector CLOB,  -- JSON array of embedding
    top_k NUMBER,
    similarity_threshold NUMBER(3, 2),
    results_found NUMBER,

    -- Retrieved Context
    retrieved_chunks CLOB,  -- JSON array of CodeContext objects
    avg_relevance_score NUMBER(5, 4),

    -- Graph Search (Neo4j)
    cypher_query CLOB,
    graph_nodes_returned NUMBER,

    -- Status
    status VARCHAR2(20) CHECK (status IN ('SUCCESS', 'FAILED', 'TIMEOUT', 'NO_RESULTS')),
    error_message CLOB,

    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Indexes
    CONSTRAINT fk_retrieval_interaction FOREIGN KEY (interaction_id)
        REFERENCES AGENT_INTERACTIONS(interaction_id) ON DELETE CASCADE
);

CREATE INDEX idx_retrieval_interaction ON RETRIEVAL_LOGS(interaction_id);
CREATE INDEX idx_retrieval_conv ON RETRIEVAL_LOGS(conversation_id);
CREATE INDEX idx_retrieval_type ON RETRIEVAL_LOGS(retrieval_type);
CREATE INDEX idx_retrieval_started ON RETRIEVAL_LOGS(started_at);

COMMENT ON TABLE RETRIEVAL_LOGS IS 'Audit log of all RAG retrieval operations for context quality analysis';

-- =====================================================================
-- 4. WORKFLOW_METRICS: Aggregate metrics per workflow execution
-- =====================================================================
CREATE TABLE WORKFLOW_METRICS (
    metric_id VARCHAR2(100) PRIMARY KEY,
    conversation_id VARCHAR2(100) NOT NULL,
    workflow_id VARCHAR2(100),

    -- Workflow Info
    requirement VARCHAR2(4000),
    task_type VARCHAR2(50),
    data_sources VARCHAR2(500),  -- JSON array

    -- Timing
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    total_duration_ms NUMBER,

    -- Agent Execution Stats
    agents_executed NUMBER,
    agents_succeeded NUMBER,
    agents_failed NUMBER,

    -- Resource Usage
    total_llm_calls NUMBER,
    total_tokens_used NUMBER,
    total_cost_usd NUMBER(10, 4),

    -- Retrieval Stats
    total_retrievals NUMBER,
    avg_retrieval_time_ms NUMBER,
    avg_chunks_retrieved NUMBER,

    -- Git Operations
    git_clones NUMBER,
    git_pulls NUMBER,
    indexing_time_ms NUMBER,

    -- Outcome
    final_status VARCHAR2(50),  -- COMPLETED, FAILED, USER_CANCELLED, TIMEOUT
    success CHAR(1) CHECK (success IN ('Y', 'N')),

    -- Quality Metrics (to be filled manually or by review)
    user_satisfaction NUMBER(1),  -- 1-5 rating
    code_quality_score NUMBER(3, 2),
    review_comments CLOB,

    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,

    -- Indexes
    CONSTRAINT fk_metric_conversation FOREIGN KEY (conversation_id)
        REFERENCES CONVERSATIONS(conversation_id) ON DELETE CASCADE
);

CREATE INDEX idx_metric_conv ON WORKFLOW_METRICS(conversation_id);
CREATE INDEX idx_metric_started ON WORKFLOW_METRICS(started_at);
CREATE INDEX idx_metric_task_type ON WORKFLOW_METRICS(task_type);
CREATE INDEX idx_metric_success ON WORKFLOW_METRICS(success);

COMMENT ON TABLE WORKFLOW_METRICS IS 'Aggregate metrics per workflow for performance monitoring and optimization';

-- =====================================================================
-- 5. SYSTEM_HEALTH_CHECKS: Track system health and availability
-- =====================================================================
CREATE TABLE SYSTEM_HEALTH_CHECKS (
    check_id VARCHAR2(100) PRIMARY KEY,
    check_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Component Health
    component VARCHAR2(50) NOT NULL,  -- pinecone, neo4j, gemini, github, database
    status VARCHAR2(20) CHECK (status IN ('HEALTHY', 'DEGRADED', 'DOWN')),

    -- Metrics
    response_time_ms NUMBER,
    error_rate NUMBER(5, 4),  -- Percentage

    -- Details
    check_details CLOB,  -- JSON with component-specific metrics
    error_message CLOB,

    -- Indexes
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_health_component ON SYSTEM_HEALTH_CHECKS(component);
CREATE INDEX idx_health_timestamp ON SYSTEM_HEALTH_CHECKS(check_timestamp);
CREATE INDEX idx_health_status ON SYSTEM_HEALTH_CHECKS(status);

COMMENT ON TABLE SYSTEM_HEALTH_CHECKS IS 'System health monitoring for external dependencies';

-- =====================================================================
-- 6. QUALITY_FEEDBACK: User feedback on AI responses
-- =====================================================================
CREATE TABLE QUALITY_FEEDBACK (
    feedback_id VARCHAR2(100) PRIMARY KEY,
    conversation_id VARCHAR2(100) NOT NULL,
    interaction_id VARCHAR2(100),

    -- Feedback
    feedback_type VARCHAR2(50),  -- thumbs_up, thumbs_down, report_issue
    rating NUMBER(1) CHECK (rating BETWEEN 1 AND 5),

    -- Details
    feedback_text CLOB,
    issue_category VARCHAR2(100),  -- hallucination, wrong_code, irrelevant_context, slow_response

    -- Resolution
    resolved CHAR(1) DEFAULT 'N' CHECK (resolved IN ('Y', 'N')),
    resolution_notes CLOB,

    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR2(100),

    -- Indexes
    CONSTRAINT fk_feedback_conv FOREIGN KEY (conversation_id)
        REFERENCES CONVERSATIONS(conversation_id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_interaction FOREIGN KEY (interaction_id)
        REFERENCES AGENT_INTERACTIONS(interaction_id) ON DELETE SET NULL
);

CREATE INDEX idx_feedback_conv ON QUALITY_FEEDBACK(conversation_id);
CREATE INDEX idx_feedback_type ON QUALITY_FEEDBACK(feedback_type);
CREATE INDEX idx_feedback_rating ON QUALITY_FEEDBACK(rating);
CREATE INDEX idx_feedback_created ON QUALITY_FEEDBACK(created_at);

COMMENT ON TABLE QUALITY_FEEDBACK IS 'User feedback for continuous quality improvement';

-- =====================================================================
-- ROLLBACK SCRIPT
-- =====================================================================
-- DROP TABLE QUALITY_FEEDBACK CASCADE CONSTRAINTS;
-- DROP TABLE SYSTEM_HEALTH_CHECKS CASCADE CONSTRAINTS;
-- DROP TABLE WORKFLOW_METRICS CASCADE CONSTRAINTS;
-- DROP TABLE RETRIEVAL_LOGS CASCADE CONSTRAINTS;
-- DROP TABLE LLM_REQUESTS CASCADE CONSTRAINTS;
-- DROP TABLE AGENT_INTERACTIONS CASCADE CONSTRAINTS;

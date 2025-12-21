-- ================================================================
-- AutoFlow Database Migration Script
-- Database: Oracle
-- Purpose: Add workflow state persistence + knowledge graph fields
-- ================================================================

-- ================================================================
-- PART 1: Workflow State Persistence
-- ================================================================

-- Create WORKFLOW_STATES table
CREATE TABLE WORKFLOW_STATES (
                                 id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                 conversation_id VARCHAR2(100) NOT NULL UNIQUE,
                                 user_id VARCHAR2(100),
                                 status VARCHAR2(20) NOT NULL,
                                 current_agent VARCHAR2(50),
                                 state_json CLOB NOT NULL,
                                 created_at TIMESTAMP NOT NULL,
                                 updated_at TIMESTAMP NOT NULL
);

-- Create indexes for fast queries
CREATE INDEX idx_workflow_conversation ON WORKFLOW_STATES(conversation_id);
CREATE INDEX idx_workflow_user ON WORKFLOW_STATES(user_id);
CREATE INDEX idx_workflow_status ON WORKFLOW_STATES(status);
CREATE INDEX idx_workflow_updated ON WORKFLOW_STATES(updated_at);

-- Add comments for documentation
COMMENT ON TABLE WORKFLOW_STATES IS 'Stores complete workflow state for pause/resume functionality';
COMMENT ON COLUMN WORKFLOW_STATES.conversation_id IS 'Unique identifier for the workflow conversation';
COMMENT ON COLUMN WORKFLOW_STATES.status IS 'Workflow status: RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED';
COMMENT ON COLUMN WORKFLOW_STATES.current_agent IS 'Currently executing agent node';
COMMENT ON COLUMN WORKFLOW_STATES.state_json IS 'Complete WorkflowState object serialized as JSON';

-- ================================================================
-- PART 2: Knowledge Graph Fields (Update CODE_NODES table)
-- ================================================================

-- Add new columns to CODE_NODES table
ALTER TABLE CODE_NODES ADD (
    domain VARCHAR2(100),
    business_capability VARCHAR2(100),
    features CLOB,
    concepts CLOB
);

-- Create indexes for domain-based search
CREATE INDEX idx_node_domain ON CODE_NODES(domain);
CREATE INDEX idx_node_capability ON CODE_NODES(business_capability);

-- Add comments
COMMENT ON COLUMN CODE_NODES.domain IS 'Business domain extracted by LLM (e.g., "payment", "user", "order")';
COMMENT ON COLUMN CODE_NODES.business_capability IS 'Business capability (e.g., "payment-processing")';
COMMENT ON COLUMN CODE_NODES.features IS 'Comma-separated list of features this class implements';
COMMENT ON COLUMN CODE_NODES.concepts IS 'Comma-separated list of business concepts this class represents';

-- ================================================================
-- PART 3: Agent Execution Tracking (Observability)
-- ================================================================

-- Create AGENT_EXECUTIONS table for monitoring
CREATE TABLE AGENT_EXECUTIONS (
                                  id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                  execution_id VARCHAR2(50) NOT NULL UNIQUE,
                                  conversation_id VARCHAR2(50) NOT NULL,
                                  agent_name VARCHAR2(100) NOT NULL,
                                  input_state CLOB,
                                  output_state CLOB,
                                  decision VARCHAR2(50),
                                  confidence NUMBER(3,2),
                                  token_usage_input NUMBER,
                                  token_usage_output NUMBER,
                                  latency_ms NUMBER,
                                  status VARCHAR2(50),
                                  error_message VARCHAR2(4000),
                                  created_at TIMESTAMP NOT NULL
);

-- Indexes for analytics queries
CREATE INDEX idx_agent_conversation ON AGENT_EXECUTIONS(conversation_id);
CREATE INDEX idx_agent_name ON AGENT_EXECUTIONS(agent_name);
CREATE INDEX idx_agent_created ON AGENT_EXECUTIONS(created_at);
CREATE INDEX idx_agent_status ON AGENT_EXECUTIONS(status);

-- Add comments
COMMENT ON TABLE AGENT_EXECUTIONS IS 'Tracks every agent execution for observability and debugging';
COMMENT ON COLUMN AGENT_EXECUTIONS.execution_id IS 'Unique ID for this specific agent execution';
COMMENT ON COLUMN AGENT_EXECUTIONS.latency_ms IS 'Agent execution time in milliseconds';
COMMENT ON COLUMN AGENT_EXECUTIONS.token_usage_input IS 'LLM input tokens consumed';
COMMENT ON COLUMN AGENT_EXECUTIONS.token_usage_output IS 'LLM output tokens consumed';

-- ================================================================
-- PART 4: Prompt Template Versioning
-- ================================================================

-- Create PROMPT_TEMPLATES table
CREATE TABLE PROMPT_TEMPLATES (
                                  id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                  template_id VARCHAR2(50) NOT NULL,
                                  template_name VARCHAR2(100) NOT NULL,
                                  version VARCHAR2(20) NOT NULL,
                                  content CLOB NOT NULL,
                                  is_active NUMBER(1) DEFAULT 1,
                                  created_at TIMESTAMP NOT NULL,
                                  created_by VARCHAR2(100)
);

-- Unique constraint: one active version per template
CREATE UNIQUE INDEX idx_template_active ON PROMPT_TEMPLATES(template_name, is_active)
    WHERE is_active = 1;

-- Index for version queries
CREATE INDEX idx_template_name_version ON PROMPT_TEMPLATES(template_name, version);

-- Add comments
COMMENT ON TABLE PROMPT_TEMPLATES IS 'Version control for LLM prompt templates';
COMMENT ON COLUMN PROMPT_TEMPLATES.template_name IS 'Template identifier (e.g., "requirement-analyzer")';
COMMENT ON COLUMN PROMPT_TEMPLATES.version IS 'Semantic version (e.g., "1.2.0")';
COMMENT ON COLUMN PROMPT_TEMPLATES.is_active IS '1 = active version, 0 = archived';

-- ================================================================
-- PART 5: Add New Repository Method Support
-- ================================================================

-- Verify CODE_NODES has repo_name column (should exist from previous migrations)
-- If not, add it:
-- ALTER TABLE CODE_NODES ADD repo_name VARCHAR2(200);
-- CREATE INDEX idx_node_repo ON CODE_NODES(repo_name);

-- ================================================================
-- VERIFICATION QUERIES
-- ================================================================

-- Verify tables were created
SELECT table_name FROM user_tables
WHERE table_name IN ('WORKFLOW_STATES', 'AGENT_EXECUTIONS', 'PROMPT_TEMPLATES');

-- Verify columns were added to CODE_NODES
SELECT column_name, data_type FROM user_tab_columns
WHERE table_name = 'CODE_NODES'
  AND column_name IN ('DOMAIN', 'BUSINESS_CAPABILITY', 'FEATURES', 'CONCEPTS');

-- Verify indexes
SELECT index_name, table_name FROM user_indexes
WHERE table_name IN ('WORKFLOW_STATES', 'CODE_NODES', 'AGENT_EXECUTIONS', 'PROMPT_TEMPLATES');

-- ================================================================
-- SAMPLE QUERIES (For Testing)
-- ================================================================

-- Find all payment-related classes
SELECT node_id, simple_name, domain, business_capability
FROM CODE_NODES
WHERE domain = 'payment';

-- Find paused workflows
SELECT conversation_id, user_id, current_agent, updated_at
FROM WORKFLOW_STATES
WHERE status = 'PAUSED'
ORDER BY updated_at DESC;

-- Get agent performance metrics
SELECT
    agent_name,
    COUNT(*) as execution_count,
    AVG(latency_ms) as avg_latency_ms,
    AVG(token_usage_input + token_usage_output) as avg_tokens,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count
FROM AGENT_EXECUTIONS
WHERE created_at > SYSDATE - 7  -- Last 7 days
GROUP BY agent_name
ORDER BY avg_latency_ms DESC;

-- Get all prompt template versions
SELECT template_name, version, is_active, created_at
FROM PROMPT_TEMPLATES
ORDER BY template_name, created_at DESC;

-- ================================================================
-- CLEANUP QUERIES (Use with caution!)
-- ================================================================

-- Delete old completed workflows (>30 days)
-- DELETE FROM WORKFLOW_STATES
-- WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')
--   AND updated_at < SYSDATE - 30;

-- Archive old agent execution logs (>90 days)
-- DELETE FROM AGENT_EXECUTIONS
-- WHERE created_at < SYSDATE - 90;

COMMIT;

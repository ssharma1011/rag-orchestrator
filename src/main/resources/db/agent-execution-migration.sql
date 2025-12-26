-- ================================================================
-- AutoFlow Agent Execution Tracking Migration
-- Database: Oracle
-- Purpose: Add entity/repository for AGENT_EXECUTIONS table
-- Note: Table already exists from workflow-migration.sql, just needs entity
-- ================================================================

-- ================================================================
-- VERIFICATION: Check if AGENT_EXECUTIONS table exists
-- ================================================================

SELECT table_name, column_name, data_type
FROM user_tab_columns
WHERE table_name = 'AGENT_EXECUTIONS'
ORDER BY column_id;

-- If table doesn't exist, create it (should already exist from workflow-migration.sql)
-- But adding CREATE IF NOT EXISTS logic via PL/SQL block

BEGIN
    EXECUTE IMMEDIATE '
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
        )
    ';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN
            DBMS_OUTPUT.PUT_LINE('Table AGENT_EXECUTIONS already exists');
        ELSE
            RAISE;
        END IF;
END;
/

-- ================================================================
-- PART 1: Add Missing Indexes (if not exist)
-- ================================================================

-- Check and create indexes if they don't exist
BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_conversation ON AGENT_EXECUTIONS(conversation_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL; ELSE RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_name ON AGENT_EXECUTIONS(agent_name)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL; ELSE RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_created ON AGENT_EXECUTIONS(created_at)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL; ELSE RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_status ON AGENT_EXECUTIONS(status)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL; ELSE RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_execution_id ON AGENT_EXECUTIONS(execution_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE = -955 THEN NULL; ELSE RAISE; END IF;
END;
/

-- ================================================================
-- PART 2: Add Comments
-- ================================================================

COMMENT ON TABLE AGENT_EXECUTIONS IS 'Tracks every agent execution for observability and debugging';
COMMENT ON COLUMN AGENT_EXECUTIONS.execution_id IS 'Unique ID for this specific agent execution (UUID)';
COMMENT ON COLUMN AGENT_EXECUTIONS.conversation_id IS 'Which conversation this execution belongs to';
COMMENT ON COLUMN AGENT_EXECUTIONS.agent_name IS 'Name of agent that executed (e.g., RequirementAnalyzerAgent)';
COMMENT ON COLUMN AGENT_EXECUTIONS.input_state IS 'WorkflowState snapshot before agent execution (JSON)';
COMMENT ON COLUMN AGENT_EXECUTIONS.output_state IS 'WorkflowState snapshot after agent execution (JSON)';
COMMENT ON COLUMN AGENT_EXECUTIONS.decision IS 'Agent decision: PROCEED, ASK_DEV, ERROR, RETRY, etc.';
COMMENT ON COLUMN AGENT_EXECUTIONS.confidence IS 'Confidence score (0.00 - 1.00)';
COMMENT ON COLUMN AGENT_EXECUTIONS.token_usage_input IS 'LLM input tokens consumed';
COMMENT ON COLUMN AGENT_EXECUTIONS.token_usage_output IS 'LLM output tokens generated';
COMMENT ON COLUMN AGENT_EXECUTIONS.latency_ms IS 'Agent execution time in milliseconds';
COMMENT ON COLUMN AGENT_EXECUTIONS.status IS 'Execution status: SUCCESS, FAILED, TIMEOUT, etc.';
COMMENT ON COLUMN AGENT_EXECUTIONS.error_message IS 'Error message if status = FAILED';

-- ================================================================
-- PART 3: Add Reference to CONVERSATIONS Table
-- ================================================================

-- Add foreign key constraint to link agent executions to conversations
-- (Only if CONVERSATIONS table exists from conversation-persistence-migration.sql)

DECLARE
    v_table_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table_exists
    FROM user_tables
    WHERE table_name = 'CONVERSATIONS';

    IF v_table_exists > 0 THEN
        BEGIN
            EXECUTE IMMEDIATE '
                ALTER TABLE AGENT_EXECUTIONS
                ADD CONSTRAINT fk_agent_exec_conversation
                FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS(conversation_id)
            ';
            DBMS_OUTPUT.PUT_LINE('Foreign key constraint added successfully');
        EXCEPTION
            WHEN OTHERS THEN
                IF SQLCODE = -2275 THEN
                    DBMS_OUTPUT.PUT_LINE('Foreign key constraint already exists');
                ELSE
                    RAISE;
                END IF;
        END;
    ELSE
        DBMS_OUTPUT.PUT_LINE('CONVERSATIONS table does not exist yet. Run conversation-persistence-migration.sql first.');
    END IF;
END;
/

-- ================================================================
-- VERIFICATION QUERIES
-- ================================================================

-- Verify table structure
SELECT column_name, data_type, data_length, nullable
FROM user_tab_columns
WHERE table_name = 'AGENT_EXECUTIONS'
ORDER BY column_id;

-- Verify indexes
SELECT index_name, column_name
FROM user_ind_columns
WHERE table_name = 'AGENT_EXECUTIONS'
ORDER BY index_name, column_position;

-- Verify constraints
SELECT constraint_name, constraint_type, search_condition
FROM user_constraints
WHERE table_name = 'AGENT_EXECUTIONS';

-- Check if table is empty (expected for new installation)
SELECT COUNT(*) as execution_count FROM AGENT_EXECUTIONS;

-- ================================================================
-- SAMPLE QUERIES (For Testing After Implementation)
-- ================================================================

-- Get agent performance metrics (last 7 days)
SELECT
    agent_name,
    COUNT(*) as execution_count,
    AVG(latency_ms) as avg_latency_ms,
    MAX(latency_ms) as max_latency_ms,
    MIN(latency_ms) as min_latency_ms,
    AVG(token_usage_input + token_usage_output) as avg_total_tokens,
    SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failure_count,
    ROUND(100.0 * SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) / COUNT(*), 2) as success_rate
FROM AGENT_EXECUTIONS
WHERE created_at > SYSDATE - 7
GROUP BY agent_name
ORDER BY avg_latency_ms DESC;

-- Get execution timeline for a specific conversation
SELECT
    execution_id,
    agent_name,
    decision,
    confidence,
    latency_ms,
    status,
    created_at
FROM AGENT_EXECUTIONS
WHERE conversation_id = 'REPLACE_WITH_CONVERSATION_ID'
ORDER BY created_at ASC;

-- Find slowest agent executions
SELECT
    execution_id,
    conversation_id,
    agent_name,
    latency_ms,
    created_at
FROM AGENT_EXECUTIONS
WHERE latency_ms IS NOT NULL
ORDER BY latency_ms DESC
FETCH FIRST 20 ROWS ONLY;

-- Find failed agent executions with errors
SELECT
    execution_id,
    conversation_id,
    agent_name,
    error_message,
    created_at
FROM AGENT_EXECUTIONS
WHERE status = 'FAILED'
ORDER BY created_at DESC
FETCH FIRST 50 ROWS ONLY;

-- Get token usage by agent
SELECT
    agent_name,
    SUM(token_usage_input) as total_input_tokens,
    SUM(token_usage_output) as total_output_tokens,
    SUM(token_usage_input + token_usage_output) as total_tokens
FROM AGENT_EXECUTIONS
WHERE created_at > SYSDATE - 30  -- Last 30 days
GROUP BY agent_name
ORDER BY total_tokens DESC;

-- Get agent decision distribution
SELECT
    agent_name,
    decision,
    COUNT(*) as decision_count,
    ROUND(AVG(confidence), 3) as avg_confidence
FROM AGENT_EXECUTIONS
WHERE decision IS NOT NULL
GROUP BY agent_name, decision
ORDER BY agent_name, decision_count DESC;

-- ================================================================
-- CLEANUP QUERIES (Use with caution!)
-- ================================================================

-- Archive old agent execution logs (>90 days)
-- DELETE FROM AGENT_EXECUTIONS
-- WHERE created_at < SYSDATE - 90;

-- Delete failed executions older than 30 days
-- DELETE FROM AGENT_EXECUTIONS
-- WHERE status = 'FAILED'
--   AND created_at < SYSDATE - 30;

COMMIT;

-- ================================================================
-- ROLLBACK SCRIPT (Use if needed)
-- ================================================================

-- To rollback (WARNING: This deletes all tracking data):
-- DROP TABLE AGENT_EXECUTIONS CASCADE CONSTRAINTS;
-- COMMIT;

-- ================================================================
-- AutoFlow Agent Execution Tracking Migration - CORRECTED
-- Database: Oracle
-- Fix: Proper Oracle syntax with SYSDATE instead of CURRENT_TIMESTAMP
-- ================================================================

-- ================================================================
-- PART 1: Create AGENT_EXECUTIONS Table (If not exists)
-- ================================================================

DECLARE
    v_table_exists NUMBER;
BEGIN
    -- Check if table exists
    SELECT COUNT(*) INTO v_table_exists
    FROM user_tables
    WHERE table_name = 'AGENT_EXECUTIONS';

    IF v_table_exists = 0 THEN
        -- Create table
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
                created_at TIMESTAMP DEFAULT SYSDATE NOT NULL
            )
        ';
        DBMS_OUTPUT.PUT_LINE('Table AGENT_EXECUTIONS created successfully');
    ELSE
        DBMS_OUTPUT.PUT_LINE('Table AGENT_EXECUTIONS already exists');
    END IF;
END;
/

-- ================================================================
-- PART 2: Create Indexes (If not exist)
-- ================================================================

DECLARE
    v_index_exists NUMBER;
BEGIN
    -- idx_agent_conversation
    SELECT COUNT(*) INTO v_index_exists
    FROM user_indexes WHERE index_name = 'IDX_AGENT_CONVERSATION';
    IF v_index_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_conversation ON AGENT_EXECUTIONS(conversation_id)';
        DBMS_OUTPUT.PUT_LINE('Created index: idx_agent_conversation');
    END IF;

    -- idx_agent_name
    SELECT COUNT(*) INTO v_index_exists
    FROM user_indexes WHERE index_name = 'IDX_AGENT_NAME';
    IF v_index_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_name ON AGENT_EXECUTIONS(agent_name)';
        DBMS_OUTPUT.PUT_LINE('Created index: idx_agent_name');
    END IF;

    -- idx_agent_created
    SELECT COUNT(*) INTO v_index_exists
    FROM user_indexes WHERE index_name = 'IDX_AGENT_CREATED';
    IF v_index_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_created ON AGENT_EXECUTIONS(created_at)';
        DBMS_OUTPUT.PUT_LINE('Created index: idx_agent_created');
    END IF;

    -- idx_agent_status
    SELECT COUNT(*) INTO v_index_exists
    FROM user_indexes WHERE index_name = 'IDX_AGENT_STATUS';
    IF v_index_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_status ON AGENT_EXECUTIONS(status)';
        DBMS_OUTPUT.PUT_LINE('Created index: idx_agent_status');
    END IF;

    -- idx_agent_execution_id
    SELECT COUNT(*) INTO v_index_exists
    FROM user_indexes WHERE index_name = 'IDX_AGENT_EXECUTION_ID';
    IF v_index_exists = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX idx_agent_execution_id ON AGENT_EXECUTIONS(execution_id)';
        DBMS_OUTPUT.PUT_LINE('Created index: idx_agent_execution_id');
    END IF;
END;
/

-- ================================================================
-- PART 3: Add Comments
-- ================================================================

COMMENT ON TABLE AGENT_EXECUTIONS IS 'Tracks every agent execution for observability';
COMMENT ON COLUMN AGENT_EXECUTIONS.execution_id IS 'Unique ID (UUID)';
COMMENT ON COLUMN AGENT_EXECUTIONS.conversation_id IS 'Which conversation';
COMMENT ON COLUMN AGENT_EXECUTIONS.agent_name IS 'Agent that executed';
COMMENT ON COLUMN AGENT_EXECUTIONS.decision IS 'Agent decision: PROCEED, ASK_DEV, ERROR, etc.';
COMMENT ON COLUMN AGENT_EXECUTIONS.confidence IS 'Confidence score (0.00 - 1.00)';
COMMENT ON COLUMN AGENT_EXECUTIONS.latency_ms IS 'Execution time in milliseconds';
COMMENT ON COLUMN AGENT_EXECUTIONS.status IS 'SUCCESS, FAILED, TIMEOUT, etc.';

-- ================================================================
-- PART 4: Add Foreign Key to CONVERSATIONS (If exists)
-- ================================================================

DECLARE
    v_conversations_exists NUMBER;
    v_constraint_exists NUMBER;
BEGIN
    -- Check if CONVERSATIONS table exists
    SELECT COUNT(*) INTO v_conversations_exists
    FROM user_tables
    WHERE table_name = 'CONVERSATIONS';

    IF v_conversations_exists > 0 THEN
        -- Check if foreign key already exists
        SELECT COUNT(*) INTO v_constraint_exists
        FROM user_constraints
        WHERE constraint_name = 'FK_AGENT_EXEC_CONVERSATION';

        IF v_constraint_exists = 0 THEN
            EXECUTE IMMEDIATE '
                ALTER TABLE AGENT_EXECUTIONS
                ADD CONSTRAINT fk_agent_exec_conversation
                FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS(conversation_id)
            ';
            DBMS_OUTPUT.PUT_LINE('Foreign key constraint added successfully');
        ELSE
            DBMS_OUTPUT.PUT_LINE('Foreign key constraint already exists');
        END IF;
    ELSE
        DBMS_OUTPUT.PUT_LINE('CONVERSATIONS table does not exist - skipping foreign key');
        DBMS_OUTPUT.PUT_LINE('Run conversation-persistence-migration.sql first');
    END IF;
END;
/

-- ================================================================
-- VERIFICATION
-- ================================================================

-- Show table structure
SELECT column_name, data_type, data_length, nullable
FROM user_tab_columns
WHERE table_name = 'AGENT_EXECUTIONS'
ORDER BY column_id;

-- Show indexes
SELECT index_name, column_name
FROM user_ind_columns
WHERE table_name = 'AGENT_EXECUTIONS'
ORDER BY index_name, column_position;

-- Show row count
SELECT COUNT(*) as execution_count FROM AGENT_EXECUTIONS;

COMMIT;

-- ================================================================
-- SAMPLE QUERIES (For testing after data collection)
-- ================================================================

-- Agent performance (last 7 days)
-- SELECT
--     agent_name,
--     COUNT(*) as execution_count,
--     ROUND(AVG(latency_ms), 2) as avg_latency_ms,
--     MAX(latency_ms) as max_latency_ms,
--     ROUND(AVG(token_usage_input + token_usage_output), 2) as avg_tokens,
--     SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) as success_count,
--     SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failure_count
-- FROM AGENT_EXECUTIONS
-- WHERE created_at > SYSDATE - 7
-- GROUP BY agent_name
-- ORDER BY avg_latency_ms DESC;

-- ================================================================
-- ROLLBACK SCRIPT
-- ================================================================
-- To rollback, run:
-- DROP TABLE AGENT_EXECUTIONS CASCADE CONSTRAINTS;
-- COMMIT;

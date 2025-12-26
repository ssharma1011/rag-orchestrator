-- ================================================================
-- AutoFlow Conversation Persistence Migration - CORRECTED
-- Database: Oracle
-- Fix: Removed DEFAULT CURRENT_TIMESTAMP (not supported in Oracle)
-- ================================================================

-- ================================================================
-- PART 1: Create CONVERSATIONS Table (CORRECTED)
-- ================================================================

CREATE TABLE CONVERSATIONS (
    conversation_id VARCHAR2(100) PRIMARY KEY,
    user_id VARCHAR2(100) NOT NULL,
    repo_url VARCHAR2(500),
    repo_name VARCHAR2(200),
    mode VARCHAR2(20),
    is_active NUMBER(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT SYSDATE NOT NULL,
    last_activity TIMESTAMP DEFAULT SYSDATE NOT NULL
);

-- Indexes
CREATE INDEX idx_conv_user ON CONVERSATIONS(user_id);
CREATE INDEX idx_conv_repo ON CONVERSATIONS(repo_name);
CREATE INDEX idx_conv_active ON CONVERSATIONS(is_active);
CREATE INDEX idx_conv_last_activity ON CONVERSATIONS(last_activity);

-- Comments
COMMENT ON TABLE CONVERSATIONS IS 'Long-lived conversation sessions';
COMMENT ON COLUMN CONVERSATIONS.mode IS 'EXPLORE, DEBUG, IMPLEMENT, REVIEW';
COMMENT ON COLUMN CONVERSATIONS.is_active IS '1 = active, 0 = closed';

-- ================================================================
-- PART 2: Create WORKFLOWS Table (CORRECTED)
-- ================================================================

CREATE TABLE WORKFLOWS (
    workflow_id VARCHAR2(100) PRIMARY KEY,
    conversation_id VARCHAR2(100) NOT NULL,
    goal CLOB,
    status VARCHAR2(20) NOT NULL,
    current_agent VARCHAR2(50),
    state_json CLOB,
    started_at TIMESTAMP DEFAULT SYSDATE NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT fk_workflow_conversation FOREIGN KEY (conversation_id)
        REFERENCES CONVERSATIONS(conversation_id)
);

-- Indexes
CREATE INDEX idx_workflow_conv ON WORKFLOWS(conversation_id);
CREATE INDEX idx_workflow_status ON WORKFLOWS(status);
CREATE INDEX idx_workflow_started ON WORKFLOWS(started_at);

-- Comments
COMMENT ON TABLE WORKFLOWS IS 'Individual workflow executions within conversations';

-- ================================================================
-- PART 3: Create WORKFLOW_BRANCHES Table (CORRECTED)
-- ================================================================

CREATE TABLE WORKFLOW_BRANCHES (
    branch_id VARCHAR2(100) PRIMARY KEY,
    parent_workflow_id VARCHAR2(100) NOT NULL,
    child_workflow_id VARCHAR2(100) NOT NULL,
    branch_point_agent VARCHAR2(50),
    reason CLOB,
    created_at TIMESTAMP DEFAULT SYSDATE NOT NULL,
    CONSTRAINT fk_branch_parent FOREIGN KEY (parent_workflow_id)
        REFERENCES WORKFLOWS(workflow_id),
    CONSTRAINT fk_branch_child FOREIGN KEY (child_workflow_id)
        REFERENCES WORKFLOWS(workflow_id)
);

-- Indexes
CREATE INDEX idx_branch_parent ON WORKFLOW_BRANCHES(parent_workflow_id);
CREATE INDEX idx_branch_child ON WORKFLOW_BRANCHES(child_workflow_id);

-- Comments
COMMENT ON TABLE WORKFLOW_BRANCHES IS 'Tracks workflow branching';

-- ================================================================
-- PART 4: Update CONVERSATION_CONTEXT (If exists)
-- ================================================================

-- Add conversation_id reference (only if table exists)
DECLARE
    v_table_exists NUMBER;
    v_column_exists NUMBER;
BEGIN
    -- Check if CONVERSATION_CONTEXT table exists
    SELECT COUNT(*) INTO v_table_exists
    FROM user_tables
    WHERE table_name = 'CONVERSATION_CONTEXT';

    IF v_table_exists > 0 THEN
        -- Check if column already exists
        SELECT COUNT(*) INTO v_column_exists
        FROM user_tab_columns
        WHERE table_name = 'CONVERSATION_CONTEXT'
        AND column_name = 'CONVERSATION_ID_REF';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE CONVERSATION_CONTEXT ADD conversation_id_ref VARCHAR2(100)';
            EXECUTE IMMEDIATE 'CREATE INDEX idx_context_conv_ref ON CONVERSATION_CONTEXT(conversation_id_ref)';
            DBMS_OUTPUT.PUT_LINE('Added conversation_id_ref to CONVERSATION_CONTEXT');
        ELSE
            DBMS_OUTPUT.PUT_LINE('Column conversation_id_ref already exists in CONVERSATION_CONTEXT');
        END IF;
    ELSE
        DBMS_OUTPUT.PUT_LINE('CONVERSATION_CONTEXT table does not exist - skipping');
    END IF;
END;
/

-- ================================================================
-- PART 5: Update CONVERSATION_MESSAGES (If exists)
-- ================================================================

DECLARE
    v_table_exists NUMBER;
    v_column_exists NUMBER;
BEGIN
    -- Check if CONVERSATION_MESSAGES table exists
    SELECT COUNT(*) INTO v_table_exists
    FROM user_tables
    WHERE table_name = 'CONVERSATION_MESSAGES';

    IF v_table_exists > 0 THEN
        -- Check if column already exists
        SELECT COUNT(*) INTO v_column_exists
        FROM user_tab_columns
        WHERE table_name = 'CONVERSATION_MESSAGES'
        AND column_name = 'CONVERSATION_ID_REF';

        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE CONVERSATION_MESSAGES ADD conversation_id_ref VARCHAR2(100)';
            EXECUTE IMMEDIATE 'CREATE INDEX idx_message_conv_ref ON CONVERSATION_MESSAGES(conversation_id_ref)';
            DBMS_OUTPUT.PUT_LINE('Added conversation_id_ref to CONVERSATION_MESSAGES');
        ELSE
            DBMS_OUTPUT.PUT_LINE('Column conversation_id_ref already exists in CONVERSATION_MESSAGES');
        END IF;
    ELSE
        DBMS_OUTPUT.PUT_LINE('CONVERSATION_MESSAGES table does not exist - skipping');
    END IF;
END;
/

-- ================================================================
-- PART 6: Migrate Existing Data (If WORKFLOW_STATES exists)
-- ================================================================

DECLARE
    v_table_exists NUMBER;
BEGIN
    -- Check if WORKFLOW_STATES table exists
    SELECT COUNT(*) INTO v_table_exists
    FROM user_tables
    WHERE table_name = 'WORKFLOW_STATES';

    IF v_table_exists > 0 THEN
        -- Migrate to CONVERSATIONS
        INSERT INTO CONVERSATIONS (
            conversation_id,
            user_id,
            repo_url,
            repo_name,
            mode,
            is_active,
            created_at,
            last_activity
        )
        SELECT
            conversation_id,
            user_id,
            JSON_VALUE(state_json, '$.repoUrl'),
            REGEXP_SUBSTR(
                REGEXP_REPLACE(JSON_VALUE(state_json, '$.repoUrl'), '.git$', ''),
                '[^/]+$'
            ) as repo_name,
            'IMPLEMENT' as mode,
            CASE WHEN status IN ('RUNNING', 'PAUSED') THEN 1 ELSE 0 END as is_active,
            created_at,
            updated_at
        FROM WORKFLOW_STATES
        WHERE NOT EXISTS (
            SELECT 1 FROM CONVERSATIONS c
            WHERE c.conversation_id = WORKFLOW_STATES.conversation_id
        );

        DBMS_OUTPUT.PUT_LINE('Migrated ' || SQL%ROWCOUNT || ' conversations from WORKFLOW_STATES');

        -- Migrate to WORKFLOWS
        INSERT INTO WORKFLOWS (
            workflow_id,
            conversation_id,
            goal,
            status,
            current_agent,
            state_json,
            started_at,
            completed_at
        )
        SELECT
            conversation_id || '_wf1',
            conversation_id,
            JSON_VALUE(state_json, '$.requirement'),
            status,
            current_agent,
            state_json,
            created_at,
            CASE WHEN status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN updated_at ELSE NULL END
        FROM WORKFLOW_STATES
        WHERE NOT EXISTS (
            SELECT 1 FROM WORKFLOWS w
            WHERE w.workflow_id = WORKFLOW_STATES.conversation_id || '_wf1'
        );

        DBMS_OUTPUT.PUT_LINE('Migrated ' || SQL%ROWCOUNT || ' workflows from WORKFLOW_STATES');
    ELSE
        DBMS_OUTPUT.PUT_LINE('WORKFLOW_STATES table does not exist - skipping data migration');
    END IF;
END;
/

-- ================================================================
-- VERIFICATION
-- ================================================================

-- Show tables created
SELECT table_name,
       (SELECT COUNT(*) FROM user_tab_columns WHERE table_name = ut.table_name) as column_count
FROM user_tables ut
WHERE table_name IN ('CONVERSATIONS', 'WORKFLOWS', 'WORKFLOW_BRANCHES')
ORDER BY table_name;

-- Show row counts
SELECT 'CONVERSATIONS' as table_name, COUNT(*) as row_count FROM CONVERSATIONS
UNION ALL
SELECT 'WORKFLOWS', COUNT(*) FROM WORKFLOWS
UNION ALL
SELECT 'WORKFLOW_BRANCHES', COUNT(*) FROM WORKFLOW_BRANCHES;

COMMIT;

-- ================================================================
-- ROLLBACK SCRIPT
-- ================================================================
-- To rollback, run:
-- DROP TABLE WORKFLOW_BRANCHES CASCADE CONSTRAINTS;
-- DROP TABLE WORKFLOWS CASCADE CONSTRAINTS;
-- DROP TABLE CONVERSATIONS CASCADE CONSTRAINTS;
-- ALTER TABLE CONVERSATION_CONTEXT DROP COLUMN conversation_id_ref;
-- ALTER TABLE CONVERSATION_MESSAGES DROP COLUMN conversation_id_ref;
-- COMMIT;

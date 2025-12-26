-- ================================================================
-- AutoFlow Conversation Persistence Migration - SIMPLE VERSION
-- Database: Oracle
-- Fix: Uses DATE instead of TIMESTAMP for compatibility
-- ================================================================

-- ================================================================
-- PART 1: Create CONVERSATIONS Table
-- ================================================================

CREATE TABLE CONVERSATIONS (
    conversation_id VARCHAR2(100) PRIMARY KEY,
    user_id VARCHAR2(100) NOT NULL,
    repo_url VARCHAR2(500),
    repo_name VARCHAR2(200),
    mode VARCHAR2(20),
    is_active NUMBER(1) DEFAULT 1,
    created_at DATE DEFAULT SYSDATE NOT NULL,
    last_activity DATE DEFAULT SYSDATE NOT NULL
);

CREATE INDEX idx_conv_user ON CONVERSATIONS(user_id);
CREATE INDEX idx_conv_repo ON CONVERSATIONS(repo_name);
CREATE INDEX idx_conv_active ON CONVERSATIONS(is_active);
CREATE INDEX idx_conv_last_activity ON CONVERSATIONS(last_activity);

-- ================================================================
-- PART 2: Create WORKFLOWS Table
-- ================================================================

CREATE TABLE WORKFLOWS (
    workflow_id VARCHAR2(100) PRIMARY KEY,
    conversation_id VARCHAR2(100) NOT NULL,
    goal CLOB,
    status VARCHAR2(20) NOT NULL,
    current_agent VARCHAR2(50),
    state_json CLOB,
    started_at DATE DEFAULT SYSDATE NOT NULL,
    completed_at DATE,
    CONSTRAINT fk_workflow_conversation FOREIGN KEY (conversation_id)
        REFERENCES CONVERSATIONS(conversation_id)
);

CREATE INDEX idx_workflow_conv ON WORKFLOWS(conversation_id);
CREATE INDEX idx_workflow_status ON WORKFLOWS(status);
CREATE INDEX idx_workflow_started ON WORKFLOWS(started_at);

-- ================================================================
-- PART 3: Create WORKFLOW_BRANCHES Table
-- ================================================================

CREATE TABLE WORKFLOW_BRANCHES (
    branch_id VARCHAR2(100) PRIMARY KEY,
    parent_workflow_id VARCHAR2(100) NOT NULL,
    child_workflow_id VARCHAR2(100) NOT NULL,
    branch_point_agent VARCHAR2(50),
    reason CLOB,
    created_at DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT fk_branch_parent FOREIGN KEY (parent_workflow_id)
        REFERENCES WORKFLOWS(workflow_id),
    CONSTRAINT fk_branch_child FOREIGN KEY (child_workflow_id)
        REFERENCES WORKFLOWS(workflow_id)
);

CREATE INDEX idx_branch_parent ON WORKFLOW_BRANCHES(parent_workflow_id);
CREATE INDEX idx_branch_child ON WORKFLOW_BRANCHES(child_workflow_id);

-- ================================================================
-- PART 4: Add Columns to Existing Tables (Safe)
-- ================================================================

-- Add to CONVERSATION_CONTEXT if it exists
DECLARE
    v_table_exists NUMBER;
    v_column_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table_exists FROM user_tables WHERE table_name = 'CONVERSATION_CONTEXT';
    IF v_table_exists > 0 THEN
        SELECT COUNT(*) INTO v_column_exists FROM user_tab_columns
        WHERE table_name = 'CONVERSATION_CONTEXT' AND column_name = 'CONVERSATION_ID_REF';
        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE CONVERSATION_CONTEXT ADD conversation_id_ref VARCHAR2(100)';
            EXECUTE IMMEDIATE 'CREATE INDEX idx_context_conv_ref ON CONVERSATION_CONTEXT(conversation_id_ref)';
        END IF;
    END IF;
END;
/

-- Add to CONVERSATION_MESSAGES if it exists
DECLARE
    v_table_exists NUMBER;
    v_column_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table_exists FROM user_tables WHERE table_name = 'CONVERSATION_MESSAGES';
    IF v_table_exists > 0 THEN
        SELECT COUNT(*) INTO v_column_exists FROM user_tab_columns
        WHERE table_name = 'CONVERSATION_MESSAGES' AND column_name = 'CONVERSATION_ID_REF';
        IF v_column_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE CONVERSATION_MESSAGES ADD conversation_id_ref VARCHAR2(100)';
            EXECUTE IMMEDIATE 'CREATE INDEX idx_message_conv_ref ON CONVERSATION_MESSAGES(conversation_id_ref)';
        END IF;
    END IF;
END;
/

-- ================================================================
-- PART 5: Migrate Data from WORKFLOW_STATES (If exists)
-- ================================================================

DECLARE
    v_table_exists NUMBER;
    v_migrated NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_table_exists FROM user_tables WHERE table_name = 'WORKFLOW_STATES';

    IF v_table_exists > 0 THEN
        -- Migrate to CONVERSATIONS
        INSERT INTO CONVERSATIONS (
            conversation_id,
            user_id,
            repo_url,
            mode,
            is_active,
            created_at,
            last_activity
        )
        SELECT
            conversation_id,
            user_id,
            JSON_VALUE(state_json, '$.repoUrl'),
            'IMPLEMENT',
            CASE WHEN status IN ('RUNNING', 'PAUSED') THEN 1 ELSE 0 END,
            created_at,
            updated_at
        FROM WORKFLOW_STATES
        WHERE NOT EXISTS (
            SELECT 1 FROM CONVERSATIONS c
            WHERE c.conversation_id = WORKFLOW_STATES.conversation_id
        );

        v_migrated := SQL%ROWCOUNT;
        DBMS_OUTPUT.PUT_LINE('Migrated ' || v_migrated || ' conversations');

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

        v_migrated := SQL%ROWCOUNT;
        DBMS_OUTPUT.PUT_LINE('Migrated ' || v_migrated || ' workflows');
    ELSE
        DBMS_OUTPUT.PUT_LINE('WORKFLOW_STATES table does not exist - no migration needed');
    END IF;
END;
/

-- ================================================================
-- VERIFICATION
-- ================================================================

SELECT 'CONVERSATIONS' as table_name, COUNT(*) as row_count FROM CONVERSATIONS
UNION ALL
SELECT 'WORKFLOWS', COUNT(*) FROM WORKFLOWS
UNION ALL
SELECT 'WORKFLOW_BRANCHES', COUNT(*) FROM WORKFLOW_BRANCHES;

COMMIT;

-- ================================================================
-- ROLLBACK (Run these if you need to undo)
-- ================================================================
-- DROP TABLE WORKFLOW_BRANCHES CASCADE CONSTRAINTS;
-- DROP TABLE WORKFLOWS CASCADE CONSTRAINTS;
-- DROP TABLE CONVERSATIONS CASCADE CONSTRAINTS;
-- COMMIT;

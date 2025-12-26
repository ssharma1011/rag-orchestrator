-- ================================================================
-- AutoFlow Conversation Persistence Migration
-- Database: Oracle
-- Purpose: Separate Conversation (long-lived) from Workflow (task-specific)
-- ================================================================

-- ================================================================
-- PART 1: Conversations Table (Long-lived conversation sessions)
-- ================================================================

CREATE TABLE CONVERSATIONS (
    conversation_id VARCHAR2(100) PRIMARY KEY,
    user_id VARCHAR2(100) NOT NULL,
    repo_url VARCHAR2(500),
    repo_name VARCHAR2(200),
    mode VARCHAR2(20),  -- EXPLORE / DEBUG / IMPLEMENT / REVIEW
    is_active NUMBER(1) DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- Indexes for fast queries
CREATE INDEX idx_conv_user ON CONVERSATIONS(user_id);
CREATE INDEX idx_conv_repo ON CONVERSATIONS(repo_name);
CREATE INDEX idx_conv_active ON CONVERSATIONS(is_active);
CREATE INDEX idx_conv_last_activity ON CONVERSATIONS(last_activity);

-- Comments
COMMENT ON TABLE CONVERSATIONS IS 'Long-lived conversation sessions that can span multiple workflows';
COMMENT ON COLUMN CONVERSATIONS.conversation_id IS 'Unique conversation identifier';
COMMENT ON COLUMN CONVERSATIONS.mode IS 'Current conversation mode: EXPLORE, DEBUG, IMPLEMENT, REVIEW';
COMMENT ON COLUMN CONVERSATIONS.is_active IS '1 = active, 0 = closed';

-- ================================================================
-- PART 2: Workflows Table (Task-specific workflow executions)
-- ================================================================

CREATE TABLE WORKFLOWS (
    workflow_id VARCHAR2(100) PRIMARY KEY,
    conversation_id VARCHAR2(100) NOT NULL,
    goal CLOB,
    status VARCHAR2(20) NOT NULL,  -- RUNNING / COMPLETED / FAILED / CANCELLED
    current_agent VARCHAR2(50),
    state_json CLOB,  -- WorkflowState snapshot
    started_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT fk_workflow_conversation FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS(conversation_id)
);

-- Indexes
CREATE INDEX idx_workflow_conv ON WORKFLOWS(conversation_id);
CREATE INDEX idx_workflow_status ON WORKFLOWS(status);
CREATE INDEX idx_workflow_started ON WORKFLOWS(started_at);

-- Comments
COMMENT ON TABLE WORKFLOWS IS 'Individual workflow executions within a conversation';
COMMENT ON COLUMN WORKFLOWS.workflow_id IS 'Unique workflow identifier';
COMMENT ON COLUMN WORKFLOWS.goal IS 'What this workflow is trying to accomplish';
COMMENT ON COLUMN WORKFLOWS.state_json IS 'Complete WorkflowState as JSON snapshot';

-- ================================================================
-- PART 3: Workflow Branches (Track workflow lineage)
-- ================================================================

CREATE TABLE WORKFLOW_BRANCHES (
    branch_id VARCHAR2(100) PRIMARY KEY,
    parent_workflow_id VARCHAR2(100) NOT NULL,
    child_workflow_id VARCHAR2(100) NOT NULL,
    branch_point_agent VARCHAR2(50),  -- Which agent we branched from
    reason CLOB,  -- Why we branched
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_branch_parent FOREIGN KEY (parent_workflow_id) REFERENCES WORKFLOWS(workflow_id),
    CONSTRAINT fk_branch_child FOREIGN KEY (child_workflow_id) REFERENCES WORKFLOWS(workflow_id)
);

-- Indexes
CREATE INDEX idx_branch_parent ON WORKFLOW_BRANCHES(parent_workflow_id);
CREATE INDEX idx_branch_child ON WORKFLOW_BRANCHES(child_workflow_id);

-- Comments
COMMENT ON TABLE WORKFLOW_BRANCHES IS 'Tracks workflow branching for retry/correction scenarios';

-- ================================================================
-- PART 4: Update Existing CONVERSATION_CONTEXT Table
-- ================================================================

-- Add conversation_id reference to existing table
ALTER TABLE CONVERSATION_CONTEXT ADD (
    conversation_id_ref VARCHAR2(100)
);

-- Create index
CREATE INDEX idx_context_conv_ref ON CONVERSATION_CONTEXT(conversation_id_ref);

-- Add foreign key (nullable for backward compatibility)
ALTER TABLE CONVERSATION_CONTEXT ADD CONSTRAINT fk_context_conversation
    FOREIGN KEY (conversation_id_ref) REFERENCES CONVERSATIONS(conversation_id);

-- Comments
COMMENT ON COLUMN CONVERSATION_CONTEXT.conversation_id_ref IS 'Reference to CONVERSATIONS table (new model)';

-- ================================================================
-- PART 5: Update Existing CONVERSATION_MESSAGES Table
-- ================================================================

-- Add conversation_id reference
ALTER TABLE CONVERSATION_MESSAGES ADD (
    conversation_id_ref VARCHAR2(100)
);

-- Create index
CREATE INDEX idx_message_conv_ref ON CONVERSATION_MESSAGES(conversation_id_ref);

-- Add foreign key
ALTER TABLE CONVERSATION_MESSAGES ADD CONSTRAINT fk_message_conversation
    FOREIGN KEY (conversation_id_ref) REFERENCES CONVERSATIONS(conversation_id);

-- Comments
COMMENT ON COLUMN CONVERSATION_MESSAGES.conversation_id_ref IS 'Reference to CONVERSATIONS table';

-- ================================================================
-- PART 6: Migrate Existing Data from WORKFLOW_STATES
-- ================================================================

-- Migrate existing workflow states to new conversation model
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
    'IMPLEMENT' as mode,  -- Default mode for existing workflows
    CASE WHEN status IN ('RUNNING', 'PAUSED') THEN 1 ELSE 0 END as is_active,
    created_at,
    updated_at
FROM WORKFLOW_STATES
WHERE NOT EXISTS (
    SELECT 1 FROM CONVERSATIONS c WHERE c.conversation_id = WORKFLOW_STATES.conversation_id
);

-- Create workflows from existing workflow states
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
    conversation_id || '_wf1',  -- Add suffix for workflow ID
    conversation_id,
    JSON_VALUE(state_json, '$.requirement'),
    status,
    current_agent,
    state_json,
    created_at,
    CASE WHEN status IN ('COMPLETED', 'FAILED', 'CANCELLED') THEN updated_at ELSE NULL END
FROM WORKFLOW_STATES
WHERE NOT EXISTS (
    SELECT 1 FROM WORKFLOWS w WHERE w.workflow_id = WORKFLOW_STATES.conversation_id || '_wf1'
);

-- ================================================================
-- PART 7: Create Sequences (if needed for auto-increment)
-- ================================================================

-- Optional: Create sequences for numeric IDs
-- CREATE SEQUENCE seq_workflow_branches START WITH 1 INCREMENT BY 1;

-- ================================================================
-- VERIFICATION QUERIES
-- ================================================================

-- Verify tables were created
SELECT table_name FROM user_tables
WHERE table_name IN ('CONVERSATIONS', 'WORKFLOWS', 'WORKFLOW_BRANCHES')
ORDER BY table_name;

-- Verify columns were added
SELECT column_name, data_type FROM user_tab_columns
WHERE table_name IN ('CONVERSATION_CONTEXT', 'CONVERSATION_MESSAGES')
  AND column_name = 'CONVERSATION_ID_REF';

-- Verify indexes
SELECT index_name, table_name FROM user_indexes
WHERE table_name IN ('CONVERSATIONS', 'WORKFLOWS', 'WORKFLOW_BRANCHES')
ORDER BY table_name, index_name;

-- Verify data migration
SELECT
    (SELECT COUNT(*) FROM CONVERSATIONS) as conversation_count,
    (SELECT COUNT(*) FROM WORKFLOWS) as workflow_count,
    (SELECT COUNT(*) FROM WORKFLOW_STATES) as old_workflow_state_count
FROM DUAL;

-- ================================================================
-- SAMPLE QUERIES (For Testing)
-- ================================================================

-- Get all active conversations
SELECT conversation_id, user_id, repo_name, mode, last_activity
FROM CONVERSATIONS
WHERE is_active = 1
ORDER BY last_activity DESC;

-- Get workflows for a conversation
SELECT
    w.workflow_id,
    w.goal,
    w.status,
    w.started_at,
    w.completed_at
FROM WORKFLOWS w
WHERE w.conversation_id = 'REPLACE_WITH_CONVERSATION_ID'
ORDER BY w.started_at DESC;

-- Get conversation with workflow count
SELECT
    c.conversation_id,
    c.repo_name,
    c.mode,
    COUNT(w.workflow_id) as workflow_count,
    MAX(w.completed_at) as last_workflow_completion
FROM CONVERSATIONS c
LEFT JOIN WORKFLOWS w ON c.conversation_id = w.conversation_id
GROUP BY c.conversation_id, c.repo_name, c.mode
ORDER BY last_workflow_completion DESC NULLS LAST;

-- Find workflows that can be retried (failed or cancelled)
SELECT
    w.workflow_id,
    w.conversation_id,
    w.goal,
    w.status,
    w.started_at
FROM WORKFLOWS w
WHERE w.status IN ('FAILED', 'CANCELLED')
ORDER BY w.started_at DESC;

-- ================================================================
-- CLEANUP QUERIES (Use with caution!)
-- ================================================================

-- Archive old completed conversations (>90 days inactive)
-- UPDATE CONVERSATIONS SET is_active = 0
-- WHERE last_activity < SYSDATE - 90
--   AND NOT EXISTS (
--       SELECT 1 FROM WORKFLOWS w
--       WHERE w.conversation_id = CONVERSATIONS.conversation_id
--       AND w.status = 'RUNNING'
--   );

-- Delete old workflow branches (>180 days)
-- DELETE FROM WORKFLOW_BRANCHES
-- WHERE created_at < SYSDATE - 180;

COMMIT;

-- ================================================================
-- ROLLBACK SCRIPT (Use if migration needs to be undone)
-- ================================================================

-- To rollback:
-- DROP TABLE WORKFLOW_BRANCHES CASCADE CONSTRAINTS;
-- DROP TABLE WORKFLOWS CASCADE CONSTRAINTS;
-- DROP TABLE CONVERSATIONS CASCADE CONSTRAINTS;
-- ALTER TABLE CONVERSATION_CONTEXT DROP COLUMN conversation_id_ref;
-- ALTER TABLE CONVERSATION_MESSAGES DROP COLUMN conversation_id_ref;
-- COMMIT;

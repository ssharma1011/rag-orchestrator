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

CREATE TABLE WORKFLOWS (
    workflow_id VARCHAR2(100) PRIMARY KEY,
    conversation_id VARCHAR2(100) NOT NULL,
    goal CLOB,
    status VARCHAR2(20) NOT NULL,
    current_agent VARCHAR2(50),
    state_json CLOB,
    started_at DATE DEFAULT SYSDATE NOT NULL,
    completed_at DATE,
    CONSTRAINT fk_workflow_conversation FOREIGN KEY (conversation_id) REFERENCES CONVERSATIONS(conversation_id)
);

CREATE INDEX idx_workflow_conv ON WORKFLOWS(conversation_id);
CREATE INDEX idx_workflow_status ON WORKFLOWS(status);
CREATE INDEX idx_workflow_started ON WORKFLOWS(started_at);

CREATE TABLE WORKFLOW_BRANCHES (
    branch_id VARCHAR2(100) PRIMARY KEY,
    parent_workflow_id VARCHAR2(100) NOT NULL,
    child_workflow_id VARCHAR2(100) NOT NULL,
    branch_point_agent VARCHAR2(50),
    reason CLOB,
    created_at DATE DEFAULT SYSDATE NOT NULL,
    CONSTRAINT fk_branch_parent FOREIGN KEY (parent_workflow_id) REFERENCES WORKFLOWS(workflow_id),
    CONSTRAINT fk_branch_child FOREIGN KEY (child_workflow_id) REFERENCES WORKFLOWS(workflow_id)
);

CREATE INDEX idx_branch_parent ON WORKFLOW_BRANCHES(parent_workflow_id);
CREATE INDEX idx_branch_child ON WORKFLOW_BRANCHES(child_workflow_id);

COMMIT;

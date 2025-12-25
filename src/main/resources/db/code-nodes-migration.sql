-- ================================================================
-- AutoFlow CODE_NODES Table Migration
-- Database: Oracle
-- Purpose: Create CODE_NODES table for code knowledge graph
-- ================================================================

-- ================================================================
-- CLEANUP: Drop existing objects if they exist
-- ================================================================

-- Drop table (CASCADE CONSTRAINTS removes foreign keys if any)
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE CODE_NODES CASCADE CONSTRAINTS';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -942 THEN -- -942 = table does not exist
         RAISE;
      END IF;
END;
/

-- ================================================================
-- CREATE TABLE
-- ================================================================

CREATE TABLE CODE_NODES (
    node_id VARCHAR2(500) PRIMARY KEY,
    node_type VARCHAR2(50) NOT NULL,
    repo_name VARCHAR2(200) NOT NULL,
    fully_qualified_name VARCHAR2(500) NOT NULL,
    simple_name VARCHAR2(200) NOT NULL,
    package_name VARCHAR2(500),
    file_path VARCHAR2(1000),
    parent_node_id VARCHAR2(500),
    summary CLOB,
    line_count NUMBER,

    -- Knowledge Graph Fields
    domain VARCHAR2(100),
    business_capability VARCHAR2(100),
    features CLOB,
    concepts CLOB,

    -- Audit
    last_updated TIMESTAMP
);

-- ================================================================
-- CREATE INDEXES
-- ================================================================

CREATE INDEX idx_node_fqn ON CODE_NODES(fully_qualified_name);
CREATE INDEX idx_node_repo ON CODE_NODES(repo_name);
CREATE INDEX idx_node_domain ON CODE_NODES(domain);
CREATE INDEX idx_node_capability ON CODE_NODES(business_capability);

-- ================================================================
-- ADD COMMENTS
-- ================================================================

COMMENT ON TABLE CODE_NODES IS 'Stores code graph nodes for RAG-based code understanding';
COMMENT ON COLUMN CODE_NODES.node_id IS 'Unique identifier for this node (same as Neo4j ID)';
COMMENT ON COLUMN CODE_NODES.node_type IS 'Type of node: CLASS, METHOD, FIELD, etc.';
COMMENT ON COLUMN CODE_NODES.repo_name IS 'Repository name this node belongs to';
COMMENT ON COLUMN CODE_NODES.fully_qualified_name IS 'Fully qualified name (e.g., com.example.MyClass)';
COMMENT ON COLUMN CODE_NODES.simple_name IS 'Simple name without package (e.g., MyClass)';
COMMENT ON COLUMN CODE_NODES.domain IS 'Business domain extracted by LLM (e.g., payment, user, order)';
COMMENT ON COLUMN CODE_NODES.business_capability IS 'Business capability (e.g., payment-processing)';
COMMENT ON COLUMN CODE_NODES.features IS 'Comma-separated list of features this class implements';
COMMENT ON COLUMN CODE_NODES.concepts IS 'Comma-separated list of business concepts';

-- ================================================================
-- VERIFICATION QUERIES
-- ================================================================

-- Verify table was created
SELECT table_name FROM user_tables WHERE table_name = 'CODE_NODES';

-- Verify indexes
SELECT index_name FROM user_indexes WHERE table_name = 'CODE_NODES';

-- Verify columns
SELECT column_name, data_type, data_length
FROM user_tab_columns
WHERE table_name = 'CODE_NODES'
ORDER BY column_id;

COMMIT;

-- ================================================================
-- ROLLBACK SCRIPT (Use if needed)
-- ================================================================

-- To rollback changes:
-- DROP TABLE CODE_NODES CASCADE CONSTRAINTS;
-- COMMIT;

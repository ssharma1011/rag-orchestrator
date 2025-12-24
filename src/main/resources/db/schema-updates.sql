-- ================================================================
-- AutoFlow Knowledge Graph Enhancement
-- Database: Oracle
-- Purpose: Add knowledge graph fields to enable big app ingestion
-- ================================================================

-- ================================================================
-- PART 1: Add Knowledge Graph Fields to CODE_NODES
-- ================================================================

-- These fields enable intelligent scope discovery across large codebases
ALTER TABLE CODE_NODES ADD (
    domain VARCHAR2(100),              -- Business domain (e.g., "payment", "user", "order")
    business_capability VARCHAR2(100), -- Business capability (e.g., "payment-processing")
    features CLOB,                     -- Comma-separated features this class implements
    concepts CLOB                      -- Comma-separated domain concepts
);

-- Create indexes for fast domain-based queries
CREATE INDEX idx_node_domain ON CODE_NODES(domain);
CREATE INDEX idx_node_capability ON CODE_NODES(business_capability);

-- Add comments for documentation
COMMENT ON COLUMN CODE_NODES.domain IS 'Business domain extracted by LLM (e.g., payment, user, order)';
COMMENT ON COLUMN CODE_NODES.business_capability IS 'Business capability (e.g., payment-processing, authentication)';
COMMENT ON COLUMN CODE_NODES.features IS 'Comma-separated list of features this class implements';
COMMENT ON COLUMN CODE_NODES.concepts IS 'Comma-separated list of business concepts this class represents';

-- ================================================================
-- VERIFICATION QUERIES
-- ================================================================

-- Verify columns were added
SELECT column_name, data_type, data_length
FROM user_tab_columns
WHERE table_name = 'CODE_NODES'
  AND column_name IN ('DOMAIN', 'BUSINESS_CAPABILITY', 'FEATURES', 'CONCEPTS');

-- Verify indexes were created
SELECT index_name, column_name
FROM user_ind_columns
WHERE table_name = 'CODE_NODES'
  AND column_name IN ('DOMAIN', 'BUSINESS_CAPABILITY');

-- ================================================================
-- SAMPLE QUERIES (For Testing After Ingestion)
-- ================================================================

-- Find all payment-related classes
SELECT node_id, simple_name, domain, business_capability
FROM CODE_NODES
WHERE domain = 'payment';

-- Find all classes with retry feature
SELECT node_id, simple_name, features
FROM CODE_NODES
WHERE features LIKE '%retry%';

-- Find all authentication-related classes
SELECT node_id, simple_name, business_capability
FROM CODE_NODES
WHERE business_capability LIKE '%authentication%';

-- Get domain statistics
SELECT domain, COUNT(*) as class_count
FROM CODE_NODES
WHERE domain IS NOT NULL
GROUP BY domain
ORDER BY class_count DESC;

COMMIT;

-- ================================================================
-- ROLLBACK SCRIPT (Use if needed)
-- ================================================================

-- To rollback changes:
-- ALTER TABLE CODE_NODES DROP COLUMN domain;
-- ALTER TABLE CODE_NODES DROP COLUMN business_capability;
-- ALTER TABLE CODE_NODES DROP COLUMN features;
-- ALTER TABLE CODE_NODES DROP COLUMN concepts;
-- DROP INDEX idx_node_domain;
-- DROP INDEX idx_node_capability;
-- COMMIT;

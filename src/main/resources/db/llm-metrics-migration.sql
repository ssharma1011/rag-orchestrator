-- ================================================================
-- AutoFlow LLM Metrics Table Migration
-- Database: Oracle
-- Purpose: Add LLM call metrics tracking with proper Oracle sequence
-- ================================================================

-- ================================================================
-- CLEANUP: Drop existing objects if they exist
-- ================================================================

-- Drop table (CASCADE CONSTRAINTS removes foreign keys if any)
BEGIN
   EXECUTE IMMEDIATE 'DROP TABLE llm_call_metrics CASCADE CONSTRAINTS';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -942 THEN -- -942 = table does not exist
         RAISE;
      END IF;
END;
/

-- Drop sequence
BEGIN
   EXECUTE IMMEDIATE 'DROP SEQUENCE llm_metrics_seq';
EXCEPTION
   WHEN OTHERS THEN
      IF SQLCODE != -2289 THEN -- -2289 = sequence does not exist
         RAISE;
      END IF;
END;
/

-- ================================================================
-- CREATE NEW OBJECTS
-- ================================================================

-- Create sequence for ID generation
CREATE SEQUENCE llm_metrics_seq
    START WITH 1
    INCREMENT BY 1
    NOCACHE
    NOCYCLE;

-- Create LLM_CALL_METRICS table
CREATE TABLE llm_call_metrics (
    id NUMBER DEFAULT llm_metrics_seq.NEXTVAL PRIMARY KEY,

    -- CALL IDENTIFICATION
    call_id VARCHAR2(100) NOT NULL UNIQUE,
    agent_name VARCHAR2(100) NOT NULL,
    conversation_id VARCHAR2(100),
    timestamp TIMESTAMP NOT NULL,

    -- REQUEST DETAILS
    model VARCHAR2(100) NOT NULL,
    prompt CLOB,
    prompt_length NUMBER,
    temperature BINARY_DOUBLE,
    max_tokens NUMBER,

    -- RESPONSE DETAILS
    response CLOB,
    response_length NUMBER,
    success NUMBER(1) NOT NULL,
    error_message VARCHAR2(4000),

    -- PERFORMANCE METRICS
    time_to_first_token NUMBER,
    latency_ms NUMBER NOT NULL,
    tokens_per_second BINARY_DOUBLE,

    -- TOKEN USAGE (COST TRACKING)
    input_tokens NUMBER NOT NULL,
    output_tokens NUMBER NOT NULL,
    total_tokens NUMBER NOT NULL,
    estimated_cost BINARY_DOUBLE,

    -- RAGAS QUALITY METRICS
    context_relevance_score BINARY_DOUBLE,
    answer_relevance_score BINARY_DOUBLE,
    faithfulness_score BINARY_DOUBLE,
    response_quality_score BINARY_DOUBLE,

    -- RETRY & ERROR HANDLING
    retry_count NUMBER DEFAULT 0,
    is_retry NUMBER(1) DEFAULT 0,
    http_status_code NUMBER,

    -- AUDIT
    created_at TIMESTAMP DEFAULT SYSTIMESTAMP
);

-- Create indexes for common queries
CREATE INDEX idx_llm_metrics_call_id ON llm_call_metrics(call_id);
CREATE INDEX idx_llm_metrics_agent ON llm_call_metrics(agent_name);
CREATE INDEX idx_llm_metrics_conversation ON llm_call_metrics(conversation_id);
CREATE INDEX idx_llm_metrics_timestamp ON llm_call_metrics(timestamp);
CREATE INDEX idx_llm_metrics_created ON llm_call_metrics(created_at);

-- Add comments for documentation
COMMENT ON TABLE llm_call_metrics IS 'Tracks all LLM API calls for cost monitoring and quality analysis';
COMMENT ON COLUMN llm_call_metrics.call_id IS 'Unique identifier for this LLM call';
COMMENT ON COLUMN llm_call_metrics.agent_name IS 'Name of the agent making the LLM call';
COMMENT ON COLUMN llm_call_metrics.latency_ms IS 'Total request latency in milliseconds';
COMMENT ON COLUMN llm_call_metrics.input_tokens IS 'Number of input tokens consumed';
COMMENT ON COLUMN llm_call_metrics.output_tokens IS 'Number of output tokens generated';
COMMENT ON COLUMN llm_call_metrics.estimated_cost IS 'Estimated cost in USD for this call';

-- ================================================================
-- VERIFICATION QUERIES
-- ================================================================

-- Verify sequence was created
SELECT sequence_name FROM user_sequences WHERE sequence_name = 'LLM_METRICS_SEQ';

-- Verify table was created
SELECT table_name FROM user_tables WHERE table_name = 'LLM_CALL_METRICS';

-- Verify indexes
SELECT index_name FROM user_indexes WHERE table_name = 'LLM_CALL_METRICS';

-- ================================================================
-- SAMPLE QUERIES (For Testing)
-- ================================================================

-- Get recent LLM calls
SELECT call_id, agent_name, model, latency_ms, total_tokens, timestamp
FROM llm_call_metrics
ORDER BY timestamp DESC
FETCH FIRST 10 ROWS ONLY;

-- Get cost by agent
SELECT
    agent_name,
    COUNT(*) as call_count,
    SUM(input_tokens) as total_input_tokens,
    SUM(output_tokens) as total_output_tokens,
    SUM(total_tokens) as total_tokens,
    SUM(estimated_cost) as total_cost,
    AVG(latency_ms) as avg_latency_ms
FROM llm_call_metrics
WHERE timestamp > SYSTIMESTAMP - INTERVAL '7' DAY
GROUP BY agent_name
ORDER BY total_cost DESC;

-- Get error rate by agent
SELECT
    agent_name,
    COUNT(*) as total_calls,
    SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) as failed_calls,
    ROUND(100.0 * SUM(CASE WHEN success = 0 THEN 1 ELSE 0 END) / COUNT(*), 2) as error_rate_pct
FROM llm_call_metrics
WHERE timestamp > SYSTIMESTAMP - INTERVAL '24' HOUR
GROUP BY agent_name
ORDER BY error_rate_pct DESC;

-- Get quality scores
SELECT
    agent_name,
    AVG(context_relevance_score) as avg_context_relevance,
    AVG(answer_relevance_score) as avg_answer_relevance,
    AVG(faithfulness_score) as avg_faithfulness,
    AVG(response_quality_score) as avg_quality
FROM llm_call_metrics
WHERE timestamp > SYSTIMESTAMP - INTERVAL '7' DAY
  AND success = 1
GROUP BY agent_name
ORDER BY avg_quality DESC;

COMMIT;

-- ================================================================
-- ROLLBACK SCRIPT (Use if needed)
-- ================================================================

-- To rollback changes:
-- DROP TABLE llm_call_metrics CASCADE CONSTRAINTS;
-- DROP SEQUENCE llm_metrics_seq;
-- COMMIT;

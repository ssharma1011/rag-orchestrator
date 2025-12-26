-- Test with different table name (in case CONVERSATIONS is reserved)
CREATE TABLE CONV_TEST (
    id VARCHAR2(100) PRIMARY KEY
);

SELECT table_name FROM user_tables WHERE table_name = 'CONV_TEST';

DROP TABLE CONV_TEST CASCADE CONSTRAINTS;

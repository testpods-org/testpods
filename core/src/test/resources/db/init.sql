-- Test init script for PostgreSQL integration tests
CREATE TABLE test_table (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);

INSERT INTO test_table (name) VALUES ('test_value');

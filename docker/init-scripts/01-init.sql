-- Initialize pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Grant necessary permissions
GRANT ALL PRIVILEGES ON DATABASE simplerag TO raguser;
GRANT ALL ON SCHEMA public TO raguser;

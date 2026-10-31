-- Baseline: privileges of the runtime role. Runs as bandcore_owner, which owns the schema.
-- The roles and the schema themselves are created by api/db/bootstrap (see docs/DATABASE.md).

GRANT USAGE ON SCHEMA bandcore TO bandcore_app;

-- Every table and sequence the owner creates from now on is usable by the runtime role.
-- Append-only tables (audit log, stock movements) must REVOKE UPDATE, DELETE explicitly.
ALTER DEFAULT PRIVILEGES FOR ROLE bandcore_owner IN SCHEMA bandcore
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO bandcore_app;

ALTER DEFAULT PRIVILEGES FOR ROLE bandcore_owner IN SCHEMA bandcore
    GRANT USAGE, SELECT ON SEQUENCES TO bandcore_app;

ALTER DEFAULT PRIVILEGES FOR ROLE bandcore_owner IN SCHEMA bandcore
    GRANT EXECUTE ON FUNCTIONS TO bandcore_app;

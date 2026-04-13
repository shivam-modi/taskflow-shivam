-- DOWN migration (manual execution: flyway doesn't auto-run downs)
-- To rollback: psql -f V1__init_schema_down.sql

DROP TRIGGER IF EXISTS tasks_updated_at ON tasks;
DROP FUNCTION IF EXISTS set_tasks_updated_at();

DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS projects;
DROP TABLE IF EXISTS users;

DROP TYPE IF EXISTS task_priority;
DROP TYPE IF EXISTS task_status;

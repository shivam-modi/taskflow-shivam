-- Composite indexes for the EXISTS subqueries in findAccessibleByUser and userCanAccess.
-- Without these, PostgreSQL scans idx_tasks_project then filters rows in memory.
-- With these, the (project_id, assignee_id) and (project_id, creator_id) lookups
-- are single B-tree range scans that short-circuit on first match.

CREATE INDEX idx_tasks_project_assignee ON tasks (project_id, assignee_id) WHERE assignee_id IS NOT NULL;
CREATE INDEX idx_tasks_project_creator  ON tasks (project_id, creator_id);

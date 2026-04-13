-- Explicit project membership table.
-- Replaces the implicit membership model where access was derived by scanning
-- the tasks table for every access check (O(tasks) → O(1) with PK lookup).

CREATE TABLE project_members (
    project_id UUID NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       TEXT NOT NULL DEFAULT 'member',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, user_id)
);

-- Reverse lookup: "which projects does this user belong to?"
CREATE INDEX idx_project_members_user ON project_members (user_id);

-- Backfill from existing data ------------------------------------------------

-- Project owners
INSERT INTO project_members (project_id, user_id, role)
SELECT id, owner_id, 'owner' FROM projects
ON CONFLICT DO NOTHING;

-- Task creators
INSERT INTO project_members (project_id, user_id, role)
SELECT DISTINCT project_id, creator_id, 'member' FROM tasks
ON CONFLICT DO NOTHING;

-- Task assignees
INSERT INTO project_members (project_id, user_id, role)
SELECT DISTINCT project_id, assignee_id, 'member' FROM tasks WHERE assignee_id IS NOT NULL
ON CONFLICT DO NOTHING;

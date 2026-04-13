-- Seed data for reviewers: test@example.com / password123
-- bcrypt hash of "password123" with cost 12
INSERT INTO users (id, name, email, password_hash)
VALUES (
    '11111111-1111-1111-1111-111111111111',
    'Test User',
    'test@example.com',
    '$2b$12$LhQ7pw0EgC09AgYngqf4JedvA/Zgl.qzM7pj.Wx4Bd9KWwDLmbeaq'
) ON CONFLICT (email) DO NOTHING;

INSERT INTO projects (id, name, description, owner_id)
VALUES (
    '22222222-2222-2222-2222-222222222222',
    'Sample product launch',
    'Seeded project for reviewers',
    '11111111-1111-1111-1111-111111111111'
) ON CONFLICT (id) DO NOTHING;

INSERT INTO tasks (id, title, description, status, priority, project_id, assignee_id, creator_id, due_date)
VALUES
    ('33333333-3333-3333-3333-333333333331', 'Draft announcement', 'Internal copy', 'todo', 'medium',
     '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
     '11111111-1111-1111-1111-111111111111', CURRENT_DATE + 7),
    ('33333333-3333-3333-3333-333333333332', 'Review legal checklist', NULL, 'in_progress', 'high',
     '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
     '11111111-1111-1111-1111-111111111111', CURRENT_DATE + 3),
    ('33333333-3333-3333-3333-333333333333', 'Book launch venue', NULL, 'done', 'low',
     '22222222-2222-2222-2222-222222222222', NULL,
     '11111111-1111-1111-1111-111111111111', CURRENT_DATE - 1)
ON CONFLICT (id) DO NOTHING;

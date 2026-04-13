# TaskFlow

## Overview

A task management REST API: users register/login with JWT, create projects, manage tasks with status/priority/assignee tracking, and see stats. Backend only — no frontend; a Postman collection (`taskflow.postman_collection.json`) is included for exercising every endpoint.

| Layer | Choice |
|-------|--------|
| **Runtime** | Kotlin 1.9 on Java 21 (Eclipse Temurin) |
| **Framework** | Spring Boot 3.3 (Web, Security, Data JPA) |
| **Database** | PostgreSQL 16 with Flyway migrations |
| **Auth** | JWT (HS256) via jjwt, bcrypt (cost 12) via Spring Security |
| **Build** | Gradle 8.8 (Kotlin DSL), multi-stage Docker |
| **Testing** | JUnit 5, MockMvc, Testcontainers (PostgreSQL), MockK |

---

## Architecture Decisions

### Why Kotlin / Spring Boot instead of Go

The assignment lists Go as preferred. I chose Kotlin + Spring Boot deliberately to maximize the depth of decisions I could demonstrate within the time window:

- **Schema-first migrations** — Flyway gives versioned, reviewable SQL with PostgreSQL-specific features (custom enums, partial indexes, triggers). Go's golang-migrate does the same; the migration files would be identical.
- **Module-based structure** — Each domain module (auth, project, task, user) owns its own controller/dto/entity/service packages. This maps 1:1 to Go's `internal/auth`, `internal/project`, etc.
- **Interface + implementation split** — Services expose interfaces; controllers and tests depend on the interface. Same pattern as Go interfaces, but Kotlin enforces it at compile time.
- **Repository pattern with native queries** — All complex queries are hand-written SQL (not ORM-generated), so the query optimization work (split queries per filter, composite indexes, `project_members` join table) transfers directly to any language.

The patterns and decisions are language-agnostic. The README documents *why* each choice was made, not just *what* was used.

### Module-based package structure

```
com.taskflow
  auth/                          — authentication module
    controller/AuthController    — register + login endpoints
    dto/AuthDtos                 — request/response data classes
    service/AuthService          — interface + implementation
    JwtAuthFilter                — Bearer token extraction filter
    JwtService                   — token signing and parsing
    PasswordService              — bcrypt hashing
  project/                       — project module
    controller/ProjectController — CRUD + stats + members endpoints
    dto/ProjectDtos              — request/response data classes
    entity/ProjectEntity         — JPA entity + repository
    service/ProjectService       — interface + implementation
  task/                          — task module
    controller/TaskController    — CRUD + filtered listing endpoints
    dto/TaskDtos                 — request/response data classes
    entity/TaskEntity            — JPA entity + repository
    service/TaskService          — interface + implementation
  user/                          — user module (entity only, no own API)
    entity/UserEntity            — JPA entity + repository
  config/                        — security chain, app properties, Jackson
  common/                        — exceptions, error handler, pagination, UUIDv7
```

Each domain module is self-contained with its own `controller/`, `dto/`, `entity/`, and `service/` subpackages. Service interfaces and implementations live side-by-side in the `service/` package — controllers depend on the interface, tests mock it via MockK. Cross-module references are explicit imports (e.g. `task.entity.TaskRepository` used by `project.service.ProjectServiceImpl`).

### Flyway + JPA validate (no auto-DDL)

Hibernate's `ddl-auto` is set to `validate` — it only checks that entities match the schema, never modifies it. All schema changes go through versioned Flyway SQL migrations, which means:
- Every schema change is reviewable, repeatable, and version-controlled
- PostgreSQL-specific features (custom enums, partial indexes, triggers) work naturally
- No surprises from Hibernate generating unexpected DDL in production

**Tradeoff:** More upfront work per schema change. Worth it for any application that outlives a prototype.

### PostgreSQL enums for status/priority

Task status and priority are `CREATE TYPE ... AS ENUM` in PostgreSQL rather than varchar columns with check constraints. Enums give storage efficiency (4 bytes vs variable-length strings) and database-level validation. The JDBC driver is configured with `stringtype=unspecified` so Hibernate can write String-typed entity fields to enum columns without explicit type converters.

### UUIDv7 for primary keys

All IDs are UUIDv7 (RFC 9562) generated via `com.fasterxml.uuid:java-uuid-generator`. UUIDv7 embeds a millisecond timestamp, so IDs sort chronologically without a separate sequence or `created_at` index. This matters for B-tree index locality — random UUIDv4 causes page splits and fragmentation, while UUIDv7 appends monotonically.

### Explicit project membership (`project_members` table)

A `project_members` join table with a composite PK `(project_id, user_id)` tracks who belongs to each project. Members are added automatically: the owner on project creation, and assignees when tasks are assigned. A secondary index on `user_id` supports the "list my projects" query.

This replaced an earlier implicit model where membership was derived by scanning the tasks table with EXISTS/UNION/OR subqueries on every access check — O(tasks) per check. With the join table, access checks are O(1) PK lookups, and project listing is an indexed JOIN instead of a correlated subquery.

**Tradeoff:** The table must be maintained (inserts on project create / task assign). Uses `INSERT ... ON CONFLICT DO NOTHING` for idempotency, so duplicate calls are harmless. The table also opens the door to invite flows and RBAC (the `role` column already distinguishes owner from member).

### `creator_id` on tasks

Every task records who created it, separate from `assignee_id`. This enables the authorization rule: "you can delete a task if you're the project owner OR the task creator." Without it, only the project owner could delete tasks, or we'd have to guess based on timestamps.

### PATCH null semantics with `Optional<T>?`

PATCH endpoints need to distinguish three states: field omitted (don't change), field explicitly `null` (clear the value), and field present with a value (update). The controller parses raw `Map<String, JsonNode>` and maps each field to:
- `null` (Kotlin null) — field not in request body, skip it
- `Optional.empty()` — field sent as `null`, clear to SQL NULL
- `Optional.of(value)` — field sent with a value, update it

**Tradeoff:** More verbose than Go's pointer approach, but type-safe and avoids ambiguity between "not sent" and "sent as null."

### Query-per-filter-combination (no OR-null patterns)

The task listing endpoint supports optional `status` and `assignee` filters. A common approach is one query with `(:status IS NULL OR t.status = :status)`, but PostgreSQL's planner produces a **generic plan** when parameters are untyped — it cannot use the composite index `(project_id, status)` because the OR prevents the planner from knowing the parameter will be non-null. Instead, the repository has four methods (`findByProject`, `findByProjectAndStatus`, `findByProjectAndAssignee`, `findByProjectAndStatusAndAssignee`), and the service layer dispatches with a `when` block. Each query matches the exact index it needs.

**Tradeoff:** More methods than a single flexible query. But the queries are trivial, and the planner picks the optimal plan every time without needing `PREPARE`/`EXECUTE` cycles or plan-cache invalidation.

### Capped project detail queries

`GET /projects/:id` embeds tasks in the response. Without a limit, a project with 50k tasks would return a multi-megabyte payload and hold a long-lived DB cursor. The repository caps this at 200 rows with a native `LIMIT` — the UI should use the paginated `/tasks` endpoint for full listing.

### Composite indexes for task queries

V1 includes `idx_tasks_status (project_id, status)` for the status filter path. V3 adds `idx_tasks_project_assignee (project_id, assignee_id)` as a partial index (where `assignee_id IS NOT NULL`) for the assignee filter, and `idx_tasks_project_creator (project_id, creator_id)` which supports future queries involving task creators. Without these, PostgreSQL scans `idx_tasks_project` and filters rows in memory.

### Access control: 404 over 403 for non-members

If a user has no access to a project, all endpoints return 404 rather than 403. This prevents information leakage — an attacker cannot enumerate valid project IDs by distinguishing "exists but forbidden" from "doesn't exist."

---

## Running Locally

**Prerequisites:** Docker and Docker Compose. Nothing else required.

```bash
git clone <repo-url> && cd <repo-dir>
cp .env.example .env          # defaults work out of the box
docker compose up --build      # starts PostgreSQL + API
```

The API is available at `http://localhost:8080`. Flyway migrations run automatically on startup. The seed migration (`V2__seed_data.sql`) creates a test user and sample data — see [Test Credentials](#test-credentials).

To explore the API, import `taskflow.postman_collection.json` into Postman and run requests top-to-bottom.

### Without Docker (development)

Requires Java 21 and a running PostgreSQL instance.

```bash
cd backend

export DATABASE_URL="jdbc:postgresql://localhost:5432/taskflow?stringtype=unspecified"
export DATABASE_USER=taskflow
export DATABASE_PASSWORD=taskflow
export JWT_SECRET="your-secret-key-at-least-32-characters"

./gradlew bootRun
```

### Running Tests

Tests use Testcontainers — Docker must be running, but no manual database setup is needed.

```bash
cd backend
./gradlew test
```

38 tests total:
- **11 integration tests** (Testcontainers + MockMvc) — auth flow, project/task lifecycle, authorization, assignee filtering, membership propagation
- **27 unit tests** (MockK) — AuthServiceImpl (5), ProjectServiceImpl (9), TaskServiceImpl (13)

---

## Running Migrations

Migrations run **automatically** on application startup via Flyway. No manual step required.

Migration files live in `backend/src/main/resources/db/migration/`:
- `V1__init_schema.sql` — tables, indexes, enums, `updated_at` trigger
- `V2__seed_data.sql` — test user, sample project, 3 sample tasks
- `V3__add_composite_indexes.sql` — composite indexes for task filter queries
- `V4__add_project_members.sql` — explicit membership table with backfill

A rollback script is available at `backend/src/main/resources/db/rollback/V1__init_schema_rollback.sql` (not auto-executed).

---

## Test Credentials

| Email             | Password      |
|-------------------|---------------|
| test@example.com  | password123   |

The seed also creates one project ("Sample product launch") with three tasks in different statuses.

---

## API Reference

**Postman collection:** Import `taskflow.postman_collection.json` from the repo root. Run requests top-to-bottom — Register/Login auto-set the auth token, Create Project/Task auto-set IDs for subsequent requests. All requests include test assertions.

All endpoints return JSON. Protected endpoints require `Authorization: Bearer <token>`.

### Auth

#### `POST /auth/register`
```json
// Request
{ "name": "Alice", "email": "alice@example.com", "password": "securepass" }

// Response 201
{ "token": "eyJ...", "user": { "id": "...", "name": "Alice", "email": "alice@example.com" } }
```

#### `POST /auth/login`
```json
// Request
{ "email": "alice@example.com", "password": "securepass" }

// Response 200
{ "token": "eyJ...", "user": { "id": "...", "name": "Alice", "email": "alice@example.com" } }
```

### Projects

#### `GET /projects?page=1&limit=20`
Lists projects the authenticated user is a member of (added automatically as owner on create, or as member when assigned a task).
```json
// Response 200
{
  "projects": [{ "id": "...", "name": "...", "description": "...", "owner_id": "...", "created_at": "..." }],
  "meta": { "page": 1, "limit": 20, "total_items": 5, "total_pages": 1 }
}
```

#### `POST /projects`
```json
// Request
{ "name": "Q3 Launch", "description": "Optional description" }
// Response 201
{ "id": "...", "name": "Q3 Launch", "description": "Optional description", "owner_id": "...", "created_at": "..." }
```

#### `GET /projects/:id`
Returns project details with all tasks embedded.
```json
// Response 200
{ "id": "...", "name": "...", "description": "...", "owner_id": "...", "created_at": "...", "tasks": [...] }
```

#### `PATCH /projects/:id`
Partial update. Only owner can modify. Send `null` to clear description.
```json
// Request
{ "name": "New Name", "description": null }
// Response 200
{ "id": "...", "name": "New Name", "description": null, "owner_id": "...", "created_at": "..." }
```

#### `DELETE /projects/:id`
Owner only. Cascades to all tasks. Returns `204 No Content`.

#### `GET /projects/:id/stats`
```json
// Response 200
{
  "by_status": { "todo": 2, "in_progress": 1, "done": 3 },
  "by_assignee": [{ "user_id": "...", "name": "Alice", "email": "...", "count": 4 }]
}
```

#### `GET /projects/:id/members`
```json
// Response 200
{ "members": [{ "id": "...", "name": "Alice", "email": "alice@example.com" }] }
```

### Tasks

#### `GET /projects/:id/tasks?status=todo&assignee=<uuid>&page=1&limit=20`
Filterable, paginated task listing.

#### `POST /projects/:id/tasks`
```json
// Request
{ "title": "Write docs", "description": "Optional", "priority": "high", "assignee_id": "...", "due_date": "2025-06-01" }
// Response 201
{ "id": "...", "title": "Write docs", "status": "todo", "priority": "high", ... }
```

#### `PATCH /tasks/:id`
Partial update. Any project member can modify. Status values: `todo`, `in_progress`, `done`. Priority values: `low`, `medium`, `high`.
```json
// Request
{ "status": "done", "assignee_id": null }
// Response 200
{ "id": "...", "status": "done", "assignee_id": null, ... }
```

#### `DELETE /tasks/:id`
Project owner or task creator only. Returns `204 No Content`.

### Error Responses

```json
// 400 — Validation
{ "error": "validation failed", "fields": { "email": "is invalid", "password": "must be at least 8 characters" } }

// 401 — Unauthorized
{ "error": "unauthorized" }

// 404 — Not found (or no access)
{ "error": "not found" }

// 403 — Forbidden (has access but wrong role)
{ "error": "forbidden" }
```

---

## What I'd Do With More Time

1. **Optimistic locking** — Add a `version` column to tasks and use `If-Match` / ETag headers on PATCH. Currently, concurrent edits are last-write-wins.

2. **Rate limiting** — Spring Boot's built-in rate limiting or a Bucket4j integration on auth endpoints to prevent credential stuffing.

3. **Refresh tokens** — The current 24h access token is a single point of failure. A short-lived access token + longer-lived refresh token with rotation would be more secure.

4. **Granular RBAC** — The `project_members` table already has a `role` column (owner/member). Extending it with viewer/editor/admin roles would unlock fine-grained permissions and invite flows.

5. **Audit log** — An append-only `activity` table recording who changed what and when. Useful for compliance and debugging, and the `creator_id` pattern already points in this direction.

6. **Search** — Full-text search on task titles/descriptions using PostgreSQL's `tsvector` + GIN index rather than LIKE queries.

7. **WebSocket notifications** — Real-time task updates for board views using Spring WebSocket + STOMP, so users see changes without polling.

# TaskFlow

A task management REST API: users register/login with JWT, create projects, manage tasks with status/priority/assignee tracking, and view stats. Backend only — a Postman collection (`taskflow.postman_collection.json`) is included for exercising every endpoint.

| Layer | Choice |
|-------|--------|
| **Runtime** | Kotlin 1.9 on Java 21 (Eclipse Temurin) |
| **Framework** | Spring Boot 3.3 (Web, Security, Data JPA) |
| **Database** | PostgreSQL 16 with Flyway migrations |
| **Auth** | JWT (HS256) via jjwt, bcrypt (cost 12) |
| **Build** | Gradle 8.8 (Kotlin DSL), multi-stage Docker |
| **Testing** | JUnit 5, MockMvc, Testcontainers (PostgreSQL), MockK |

---

## Data Model

```
users
  id          UUID (PK, UUIDv7)
  name        TEXT
  email       TEXT (unique)
  password_hash TEXT
  created_at  TIMESTAMPTZ

projects
  id          UUID (PK, UUIDv7)
  name        TEXT
  description TEXT (nullable)
  owner_id    UUID → users
  created_at  TIMESTAMPTZ

tasks
  id          UUID (PK, UUIDv7)
  title       TEXT
  description TEXT (nullable)
  status      ENUM (todo, in_progress, done)
  priority    ENUM (low, medium, high)
  project_id  UUID → projects (CASCADE)
  assignee_id UUID → users (SET NULL, nullable)
  creator_id  UUID → users (RESTRICT)
  due_date    DATE (nullable)
  created_at  TIMESTAMPTZ
  updated_at  TIMESTAMPTZ (trigger-maintained)

project_members
  project_id  UUID → projects (CASCADE)  ⎫
  user_id     UUID → users (CASCADE)     ⎭ composite PK
  role        TEXT (owner | member)
  created_at  TIMESTAMPTZ
```

A user owns projects (1:N). A project has tasks (1:N, cascade delete). Tasks have a creator (required) and optional assignee. `project_members` tracks who can access a project — maintained automatically via `INSERT ... ON CONFLICT DO NOTHING` when projects are created or tasks assigned.

---

## Architecture Decisions

### Why Kotlin instead of Go

The assignment lists Go as preferred. I went with Kotlin + Spring Boot because it let me demonstrate more within the time I had — Flyway for schema-first migrations, Spring Security for the JWT filter chain, JPA/native queries for the data layer. The patterns (module-per-domain, interface-based services, hand-written SQL) map directly to Go equivalents.

### Key decisions

**Explicit project membership** — Initially membership was implicit: "who has tasks in this project?" checked via EXISTS/UNION subqueries on the tasks table (O(n) per check). I pulled that into a `project_members` join table so access checks are O(1) PK lookups. The table also sets up future invite flows and RBAC (the `role` column is already there).

**Split queries per filter** — Task listing supports optional `status` and `assignee` filters. Rather than one query with `(:status IS NULL OR t.status = :status)` — which defeats PostgreSQL's planner and skips composite indexes — there are four query methods dispatched by a `when` block. More methods, but each one hits the right index.

**PATCH null semantics** — PATCH needs three states: field omitted (don't change), field explicitly `null` (clear it), field present (update it). The controller parses raw `Map<String, JsonNode>` and maps to `Optional<T>?` — `null` means omitted, `Optional.empty()` means clear, `Optional.of(value)` means update.

**404 over 403 for non-members** — If a user requests a project they're not a member of, they get 404, not 403. This prevents project ID enumeration.

### Package structure

```
com.taskflow
  auth/        — register, login, JWT filter, password hashing
  project/     — CRUD, stats, members, membership entity
  task/        — CRUD with filtering, assignee management
  user/        — entity only (no standalone API)
  config/      — security chain, app properties, Jackson
  common/      — exceptions, error handler, pagination, UUIDv7
```

Each module owns its controller, DTOs, entities, and service interface + implementation.

---

## Running Locally

**Prerequisites:** Docker and Docker Compose.

```bash
git clone <repo-url> && cd <repo-dir>
cp .env.example .env
# Optional: replace JWT_SECRET with a real secret (openssl rand -base64 48)
docker compose up --build
```

Verify:

```bash
curl http://localhost:8080/actuator/health
# → {"status":"UP"}

curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'
# → {"token":"eyJ...","user":{...}}
```

To explore the full API, import `taskflow.postman_collection.json` into Postman and run requests top-to-bottom.

### Without Docker

Requires Java 21 and a running PostgreSQL instance.

```bash
cd backend

export DATABASE_URL="jdbc:postgresql://localhost:5432/taskflow?stringtype=unspecified"
export DATABASE_USER=taskflow
export DATABASE_PASSWORD=taskflow
export JWT_SECRET="your-secret-key-at-least-32-characters"

./gradlew bootRun
```

---

## Running Tests

Tests use Testcontainers — Docker must be running, but no manual DB setup needed.

```bash
cd backend
./gradlew test
```

38 tests total:
- **11 integration tests** (Testcontainers + MockMvc) — full auth flow, project/task lifecycle, authorization, filtering, membership propagation
- **27 unit tests** (MockK) — service layer logic for auth, projects, and tasks

Unit tests mock repositories to isolate service logic. Integration tests boot Spring against a real PostgreSQL container (not H2) so PG-specific behavior (enum casting, partial indexes, `ON CONFLICT`) is tested end-to-end.

---

## Running Migrations

Migrations run automatically on startup via Flyway. No manual step needed.

Files in `backend/src/main/resources/db/migration/`:
- `V1__init_schema.sql` — tables, indexes, enums, `updated_at` trigger
- `V2__seed_data.sql` — test user + sample project with tasks
- `V3__add_composite_indexes.sql` — composite indexes for task filter queries
- `V4__add_project_members.sql` — membership table with backfill from existing data

---

## Test Credentials

| Email             | Password      |
|-------------------|---------------|
| test@example.com  | password123   |

The seed also creates one project ("Sample product launch") with three tasks.

---

## API Reference

Import `taskflow.postman_collection.json` for a ready-to-run request suite. All endpoints return JSON. Protected routes require `Authorization: Bearer <token>`.

### Auth

**`POST /auth/register`** — Create account. Returns JWT + user.
```
Body: { "name": "Alice", "email": "alice@example.com", "password": "securepass" }
201:  { "token": "eyJ...", "user": { "id": "...", "name": "Alice", "email": "alice@example.com" } }
```

**`POST /auth/login`** — Returns JWT + user.
```
Body: { "email": "alice@example.com", "password": "securepass" }
200:  { "token": "eyJ...", "user": { "id": "...", "name": "Alice", "email": "alice@example.com" } }
```

### Projects

**`GET /projects?page=1&limit=20`** — List projects you're a member of.

**`POST /projects`** — Create project. You become the owner.
```
Body: { "name": "Q3 Launch", "description": "Optional" }
```

**`GET /projects/:id`** — Project detail with tasks (capped at 200).

**`PATCH /projects/:id`** — Owner only. Partial update. Send `null` to clear a field.

**`DELETE /projects/:id`** — Owner only. Cascades to tasks. `204 No Content`.

**`GET /projects/:id/stats`** — Task counts by status and by assignee.
```
200:  {
    "by_status": { "todo": 2, "in_progress": 1, "done": 3 },
    "by_assignee": [{ "user_id": "...", "name": "Alice", "email": "...", "count": 4 }]
  }
```

**`GET /projects/:id/members`** — List project members.

### Tasks

**`GET /projects/:id/tasks?status=todo&assignee=<uuid>&page=1&limit=20`** — Filterable, paginated.

**`POST /projects/:id/tasks`** — Create task. Assigning someone auto-adds them to the project.
```
Body: { "title": "Write docs", "priority": "high", "assignee_id": "...", "due_date": "2025-06-01" }
```

**`PATCH /tasks/:id`** — Any project member. Status: `todo`, `in_progress`, `done`. Priority: `low`, `medium`, `high`.

**`DELETE /tasks/:id`** — Project owner or task creator only. `204 No Content`.

### Errors

| Status | Body |
|--------|------|
| 400 | `{"error":"validation failed","fields":{"email":"is invalid"}}` |
| 401 | `{"error":"unauthorized"}` |
| 403 | `{"error":"forbidden"}` |
| 404 | `{"error":"not found"}` |

---

## What I'd Do With More Time

1. **Refresh tokens** — Current 24h access token is a single point of failure. Short-lived access (15 min) + refresh token (7 days) with rotation and revocation table.

2. **Optimistic locking** — `version` column on tasks with `If-Match`/ETag on PATCH to prevent last-write-wins on concurrent edits.

3. **Rate limiting** — Bucket4j on auth endpoints with per-IP and per-email limits to prevent credential stuffing.

4. **Granular RBAC** — Extend the `role` column in `project_members` with viewer/editor/admin roles for fine-grained permissions and invite flows.

5. **Full-text search** — `tsvector` + GIN index on task titles/descriptions instead of LIKE queries.

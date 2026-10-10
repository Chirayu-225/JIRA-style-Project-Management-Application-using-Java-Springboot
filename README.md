# Issue Tracker

A full-stack, JIRA-style project management application. Teams create projects, plan work in **sprints**, track **issues** on a drag-and-drop **Kanban board**, discuss them in comments, and see each other's changes **live**, with role-based permissions on every action.

Built with **Java 21 + Spring Boot 3** on the backend, **React 18 + TypeScript** on the frontend, and **PostgreSQL** for storage. It deploys on free cloud tiers (Render, Neon, Vercel).

| Light | Dark |
|---|---|
| ![Board, light theme](docs/screenshots/board-light.png) | ![Board, dark theme](docs/screenshots/board-dark.png) |

---

## Contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Architecture](#architecture)
- [Project structure](#project-structure)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Testing](#testing)
- [Deployment](#deployment)
- [API reference](#api-reference)
- [Data model](#data-model)
- [Engineering decisions](#engineering-decisions)
- [Roadmap](#roadmap)
- [Further reading](#further-reading)

---

## Features

**Projects & people**
- Sign up / sign in with email and password (JWT authentication)
- Create projects with a short key, e.g. `MOB`, so issues are numbered `MOB-1`, `MOB-2`, …
- Four roles per project: **Owner › Admin › Member › Viewer**, enforced on every API call
- Invite members by email, change roles, remove members, leave a project

**Planning**
- **Kanban board**: To Do → In Progress → In Review → Done, with drag-and-drop on desktop and touch
- **Backlog & sprints**: create, start and complete sprints. Only one sprint can be active at a time, and unfinished work returns to the backlog when a sprint ends
- Sprint progress (story points done/total) and days remaining on the board

**Issues**
- Type (Story / Task / Bug), priority (5 levels), story points, assignee, sprint
- Comments and a full per-issue change history
- Ranked **full-text search** with stemming ("crashing" finds "crashes") and jump-to-key (`MOB-12`)
- Shareable issue links (`?issue=42`)

**Collaboration**
- **Live updates** over Server-Sent Events: when a teammate moves a card, your board refreshes and shows a notification
- **Conflict detection**: if two people edit the same issue, the second save is rejected (`409 Conflict`) instead of silently overwriting the first
- Project-wide **activity log** (audit trail)

**Experience**
- Professional, responsive UI that works from phone to desktop
- **Light / dark theme toggle** that remembers your choice and follows the OS setting by default
- Keyboard shortcuts: `/` to search, `C` to create an issue, `Esc` to close dialogs
- One-click **demo workspace** with sample data
- Interactive API docs (Swagger UI)

<details>
<summary>More screenshots</summary>

| Issue detail | Backlog & sprints |
|---|---|
| ![Issue detail](docs/screenshots/issue-detail.png) | ![Backlog](docs/screenshots/backlog-dark.png) |

| Sign in | Mobile |
|---|---|
| ![Sign in](docs/screenshots/sign-in.png) | ![Mobile board](docs/screenshots/mobile.png) |

</details>

---

## Tech stack

| Layer | Technology |
|---|---|
| Language (backend) | Java 21 |
| Framework | Spring Boot 3.4: Web MVC, Data JPA, Security, Validation, Actuator |
| Persistence | Hibernate 6, PostgreSQL 16, Flyway migrations, HikariCP connection pool |
| Auth | Stateless JWT (jjwt), BCrypt password hashing |
| Real-time | Server-Sent Events (`SseEmitter`) |
| API docs | springdoc-openapi (Swagger UI) |
| Language (frontend) | TypeScript 5 |
| UI | React 18, React Router 6, dnd-kit (drag-and-drop), hand-written CSS design system |
| Build tools | Maven (backend), Vite 5 (frontend) |
| Testing | JUnit 5, Spring MockMvc, Testcontainers (real PostgreSQL in Docker) |
| DevOps | Docker multi-stage builds, Docker Compose, GitHub Actions CI |
| Hosting (free) | Render (API), Neon (PostgreSQL), Vercel (frontend) |

---

## Architecture

```mermaid
flowchart LR
  B["Browser<br/>React SPA (Vercel)"] -- "REST + JWT" --> A["Spring Boot API<br/>(Render, Docker)"]
  A -- "SSE live events" --> B
  A -- "JDBC / HikariCP" --> D[("PostgreSQL<br/>(Neon)")]
```

The backend follows a classic **layered architecture**:

```
HTTP request
   │
   ▼
JwtAuthFilter ──► SecurityConfig        who are you? are you logged in?
   │
   ▼
Controller                              parse JSON, validate input, return DTOs
   │
   ▼
Service  ──► ProjectAccess              business rules + "may this user do this?"
   │     ──► ActivityService            audit log
   │     ──► ApplicationEventPublisher  ──► EventBroadcaster ──► SSE to browsers (after commit)
   ▼
Repository (Spring Data JPA)            SQL via Hibernate
   │
   ▼
PostgreSQL (schema owned by Flyway)
```

---

## Project structure

```
issue-tracker/
├── backend/                                  Spring Boot REST API
│   ├── pom.xml                               Maven build + dependencies
│   ├── Dockerfile                            multi-stage image (Maven build → slim JRE)
│   └── src/
│       ├── main/java/com/issuetracker/
│       │   ├── IssueTrackerApplication.java  entry point
│       │   ├── config/                       SecurityConfig (JWT, CORS), OpenApiConfig
│       │   ├── security/                     JwtService, JwtAuthFilter, AuthUser
│       │   ├── common/                       ApiException, GlobalExceptionHandler, PageResponse
│       │   ├── user/                         User entity, register / login / me
│       │   ├── project/                      projects, members, roles, ProjectAccess
│       │   ├── sprint/                       sprint lifecycle (planned → active → completed)
│       │   ├── issue/                        issues, board ordering, search, comments
│       │   ├── activity/                     audit log
│       │   ├── realtime/                     Server-Sent Events broadcaster
│       │   └── seed/                         demo data for first start
│       ├── main/resources/
│       │   ├── application.yml               configuration (reads environment variables)
│       │   └── db/migration/V1__init_schema.sql   database schema (Flyway)
│       └── test/java/com/issuetracker/
│           └── IssueTrackerApiIntegrationTest.java  end-to-end API tests
│
├── frontend/                                 React + TypeScript single-page app
│   ├── index.html                            HTML shell (+ theme applied before first paint)
│   ├── vite.config.ts · tsconfig.json        build + type-checking config
│   ├── vercel.json                           SPA routing on Vercel
│   ├── Dockerfile · nginx.conf               container image for docker compose
│   └── src/
│       ├── main.tsx                          app bootstrap (Theme → Router → Auth providers)
│       ├── App.tsx                           routes + auth guard
│       ├── api.ts                            typed API client (one function per endpoint)
│       ├── auth.tsx                          logged-in user context
│       ├── theme.tsx                         light/dark theme context + toggle
│       ├── styles.css                        design system (tokens for both themes)
│       ├── components/
│       │   ├── icons.tsx                     SVG icon set, logo
│       │   ├── bits.tsx                      Avatar, badges, Modal, EmptyState, helpers
│       │   └── IssueModal.tsx                create / view / edit issue, comments, history
│       └── pages/
│           ├── AuthPage.tsx                  sign in / sign up
│           ├── ProjectsPage.tsx              project list + create
│           ├── ProjectLayout.tsx             sidebar, search, live updates, shortcuts, toasts
│           ├── BoardPage.tsx                 Kanban board + drag-and-drop
│           ├── BacklogPage.tsx               sprints + backlog planning
│           ├── ActivityPage.tsx              activity timeline
│           └── SettingsPage.tsx              project details, members, danger zone
│
├── docs/
│   ├── DEPLOYMENT.md                         free deployment, step by step
│   ├── LEARNING_GUIDE.md                     how the code works, request walkthrough, interview Q&A
│   ├── TOPICS.md                             every concept this project uses (study checklist)
│   └── screenshots/
├── docker-compose.yml                        run DB + API + web with one command
├── render.yaml                               Render deployment blueprint
└── .github/workflows/ci.yml                  build + test on every push
```

---

## Getting started

### Prerequisites

| Tool | Version | Check with |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -v` |
| Node.js | 20+ | `node -v` |
| PostgreSQL | 16, local **or** a free [Neon](https://neon.tech) database | |

> `JAVA_HOME` must point to the JDK 21 folder (not its `bin` subfolder), because Maven builds with whichever Java `JAVA_HOME` names.

### 1. Database

**Option A, Neon (online, free):** create a project, click **Connect**, turn off connection pooling, and copy the connection string. It contains three values you'll need:

```
postgresql://USER:PASSWORD@HOST/neondb?sslmode=require
```

**Option B, local PostgreSQL:** create a user and database matching the built-in defaults:

```sql
CREATE USER issuetracker WITH PASSWORD 'issuetracker';
CREATE DATABASE issuetracker OWNER issuetracker;
```

You don't create any tables yourself. **Flyway creates the schema automatically** on first start.

### 2. Backend

Windows (Command Prompt). Skip the `set` lines if you chose local PostgreSQL:

```bat
set DATABASE_URL=jdbc:postgresql://HOST/neondb?sslmode=require
set DB_USERNAME=USER
set DB_PASSWORD=PASSWORD

cd backend
mvn spring-boot:run
```

macOS / Linux: use `export DATABASE_URL=...` instead of `set`.

It's ready when the log shows `Started IssueTrackerApplication`. Then check:

- http://localhost:8080/actuator/health → `{"status":"UP"}`
- http://localhost:8080/swagger-ui.html → interactive API docs

> `set` values only last for that terminal window. To make them permanent on Windows, add them as **User environment variables**.

### 3. Frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173** and click **Explore the demo workspace**, or sign up.

Demo account: `demo@example.com` / `demo12345`.

### Alternative: everything with Docker

If Docker Desktop is installed, one command starts PostgreSQL, the API and the website:

```bash
docker compose up --build
```

---

## Configuration

All backend settings come from environment variables (see `backend/src/main/resources/application.yml`):

| Variable | Default | Purpose |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/issuetracker` | JDBC URL of the database |
| `DB_USERNAME` / `DB_PASSWORD` | `issuetracker` / `issuetracker` | Database credentials |
| `JWT_SECRET` | dev-only value | Signing key for tokens, **32+ characters**, must be set in production |
| `JWT_TTL_HOURS` | `24` | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated frontend URLs allowed to call the API (wildcards OK, e.g. `https://*.vercel.app`) |
| `SEED_DEMO` | `true` | Create the demo workspace on first start |
| `DB_POOL_SIZE` | `5` | Max database connections |
| `PORT` | `8080` | HTTP port (set automatically by Render) |

Frontend (`frontend/.env.local`, see `.env.example`):

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_URL` | `http://localhost:8080` | Base URL of the API (read at **build** time) |

---

## Testing

```bash
cd backend
mvn verify        # needs Docker running (Testcontainers starts a real PostgreSQL)
```

`IssueTrackerApiIntegrationTest` boots the whole application and tests it through HTTP:

- the full issue lifecycle: create → number → move → search → comment → activity
- optimistic locking returns `409` on a stale edit
- permissions: no token → `401`, non-member → `404`, viewer edit → `403`
- sprint rules: one active sprint, unfinished work returns to the backlog
- validation errors list the offending fields

Frontend type-check and production build:

```bash
cd frontend
npm run build
```

Both run automatically on every push through **GitHub Actions** (`.github/workflows/ci.yml`).

---

## Deployment

Free hosting, no credit card: **GitHub → Neon (DB) → Render (API, Docker) → Vercel (web)**.
Step-by-step instructions are in **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**.

> Render's free tier sleeps after ~15 min without traffic, so the first request then takes ~30–60 s. The UI shows a "waking up the server" banner while it happens.

---

## API reference

All endpoints except auth and health need `Authorization: Bearer <token>`. Full interactive docs: `/swagger-ui.html`.

| Method | Path | Minimum role |
|---|---|---|
| `POST` | `/api/auth/register` · `/api/auth/login` | public |
| `GET` | `/api/users/me` | signed in |
| `GET` `POST` | `/api/projects` | signed in |
| `GET` · `PUT` · `DELETE` | `/api/projects/{id}` | viewer · admin · owner |
| `GET` `POST` | `/api/projects/{id}/members` | viewer · admin |
| `PUT` `DELETE` | `/api/projects/{id}/members/{userId}` | admin (or self, to leave) |
| `GET` `POST` | `/api/projects/{id}/sprints` | viewer · member |
| `POST` | `/api/projects/{id}/sprints/{sid}/start` · `/complete` | admin |
| `DELETE` | `/api/projects/{id}/sprints/{sid}` | admin |
| `GET` | `/api/projects/{id}/issues?status=&type=&assigneeId=&sprintId=&backlog=&q=` | viewer |
| `GET` | `/api/projects/{id}/issues/search?q=` | viewer |
| `POST` | `/api/projects/{id}/issues` | member |
| `GET` · `PUT` · `DELETE` | `/api/projects/{id}/issues/{issueId}` | viewer · member · reporter/admin |
| `POST` | `/api/projects/{id}/issues/{issueId}/move` | member |
| `GET` `POST` `DELETE` | `/api/projects/{id}/issues/{issueId}/comments[/{cid}]` | viewer · member · author/admin |
| `GET` | `/api/projects/{id}/activity` · `/issues/{issueId}/activity` | viewer |
| `GET` | `/api/projects/{id}/events` (Server-Sent Events) | viewer |

Errors follow **RFC 7807 Problem Details**:

```json
{ "status": 409, "title": "Conflict", "detail": "This issue was changed by someone else. Reload to see the latest version." }
```

---

## Data model

```mermaid
erDiagram
  users ||--o{ project_members : "belongs to"
  projects ||--o{ project_members : has
  projects ||--o{ sprints : has
  projects ||--o{ issues : has
  sprints |o--o{ issues : contains
  users |o--o{ issues : "assignee / reporter"
  issues ||--o{ comments : has
  projects ||--o{ activity_log : records
```

Schema highlights (`V1__init_schema.sql`): `UNIQUE (project_id, number)` for issue keys, `CHECK` constraints on enums, `ON DELETE CASCADE` cleanup, a **partial unique index** allowing only one active sprint per project, and a generated `tsvector` column with a **GIN index** for full-text search.

---

## Engineering decisions

| Problem | Solution |
|---|---|
| Two requests creating issues at the same moment must not both get `MOB-7` | Row lock on the project (`SELECT … FOR UPDATE`) while incrementing its counter, plus a unique constraint as a safety net |
| Two people editing the same issue | **Optimistic locking** with `@Version` → `409 Conflict` instead of a lost update |
| Reordering cards without rewriting whole columns | **Fractional indexing**: a card dropped between 1000 and 2000 gets 1500, so one row is updated per move. The column is respaced only when gaps get tiny |
| "Only one active sprint" must hold under concurrency | Enforced by the **database** (partial unique index), not just Java code |
| Fast, relevant search without extra infrastructure | PostgreSQL full-text search (`tsvector`, `websearch_to_tsquery`, `ts_rank`, GIN index) |
| Browsers must never see changes that roll back | Events published with `@TransactionalEventListener(AFTER_COMMIT)` |
| Loading 50 cards shouldn't run 51 queries (N+1) | `@EntityGraph` / `join fetch` load related rows in one query |
| Outsiders probing project IDs | Non-members get `404`, not `403` |
| Schema changes across environments | Versioned **Flyway** migrations; Hibernate never alters tables |
| Scaling the API horizontally | Stateless JWT auth. With several instances, live events would move to Redis Pub/Sub or Postgres `LISTEN/NOTIFY` |

---

## Roadmap

- Refresh tokens and login rate limiting
- @mentions and email notifications
- File attachments (S3-compatible storage)
- Burndown chart per sprint
- Labels and epics
- AI-assisted triage: suggest type, priority and assignee from the description

---

## Further reading

- **[docs/LEARNING_GUIDE.md](docs/LEARNING_GUIDE.md)**: how the code works, a full request walkthrough, interview questions
- **[docs/TOPICS.md](docs/TOPICS.md)**: every concept this project uses, as a study checklist
- **[docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)**: deploy for free, step by step

## License

MIT

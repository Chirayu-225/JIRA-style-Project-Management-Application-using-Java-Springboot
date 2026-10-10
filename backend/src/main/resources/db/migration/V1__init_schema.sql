-- =====================================================================
-- TaskForge schema v1
-- Flyway runs this once, in order, and records it in flyway_schema_history.
-- Never edit an applied migration: add V2__..., V3__... instead.
-- =====================================================================

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,          -- BCrypt hash, never the raw password
    full_name     VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Case-insensitive uniqueness: "A@x.com" and "a@x.com" are the same account
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE projects (
    id            BIGSERIAL PRIMARY KEY,
    project_key   VARCHAR(10)  NOT NULL UNIQUE,   -- e.g. "TF" -> issues TF-1, TF-2 ...
    name          VARCHAR(100) NOT NULL,
    description   TEXT,
    owner_id      BIGINT       NOT NULL REFERENCES users (id),
    issue_counter INT          NOT NULL DEFAULT 0, -- last issue number handed out
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE project_members (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT      NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role       VARCHAR(20) NOT NULL CHECK (role IN ('VIEWER', 'MEMBER', 'ADMIN', 'OWNER')),
    joined_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_member UNIQUE (project_id, user_id)
);
CREATE INDEX ix_members_user ON project_members (user_id);

CREATE TABLE sprints (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT       NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    goal       TEXT,
    status     VARCHAR(20)  NOT NULL CHECK (status IN ('PLANNED', 'ACTIVE', 'COMPLETED')),
    start_date DATE,
    end_date   DATE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_sprints_project ON sprints (project_id);
-- Business rule enforced by the DATABASE, not just Java code:
-- a project can have at most ONE active sprint, even under concurrent requests.
CREATE UNIQUE INDEX ux_one_active_sprint ON sprints (project_id) WHERE status = 'ACTIVE';

CREATE TABLE issues (
    id           BIGSERIAL PRIMARY KEY,
    project_id   BIGINT           NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    number       INT              NOT NULL,
    title        VARCHAR(200)     NOT NULL,
    description  TEXT,
    type         VARCHAR(20)      NOT NULL CHECK (type IN ('STORY', 'TASK', 'BUG')),
    status       VARCHAR(20)      NOT NULL CHECK (status IN ('TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE')),
    priority     VARCHAR(20)      NOT NULL CHECK (priority IN ('LOWEST', 'LOW', 'MEDIUM', 'HIGH', 'HIGHEST')),
    story_points INT CHECK (story_points BETWEEN 0 AND 100),
    assignee_id  BIGINT REFERENCES users (id) ON DELETE SET NULL,
    reporter_id  BIGINT           NOT NULL REFERENCES users (id),
    sprint_id    BIGINT REFERENCES sprints (id) ON DELETE SET NULL, -- NULL = backlog
    position     DOUBLE PRECISION NOT NULL,       -- fractional index for board ordering
    version      BIGINT           NOT NULL DEFAULT 0, -- optimistic locking
    created_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ      NOT NULL DEFAULT now(),
    -- Full-text search vector, kept up to date automatically by PostgreSQL
    search_vector TSVECTOR GENERATED ALWAYS AS (
        setweight(to_tsvector('english'::regconfig, coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english'::regconfig, coalesce(description, '')), 'B')
    ) STORED,
    CONSTRAINT ux_issue_number UNIQUE (project_id, number)
);
CREATE INDEX ix_issues_board    ON issues (project_id, status, position);
CREATE INDEX ix_issues_sprint   ON issues (sprint_id);
CREATE INDEX ix_issues_assignee ON issues (assignee_id);
CREATE INDEX ix_issues_search   ON issues USING GIN (search_vector);

CREATE TABLE comments (
    id         BIGSERIAL PRIMARY KEY,
    issue_id   BIGINT      NOT NULL REFERENCES issues (id) ON DELETE CASCADE,
    author_id  BIGINT      NOT NULL REFERENCES users (id),
    body       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_comments_issue ON comments (issue_id, created_at);

-- Append-only audit trail: who did what, when
CREATE TABLE activity_log (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT      NOT NULL REFERENCES projects (id) ON DELETE CASCADE,
    issue_id   BIGINT REFERENCES issues (id) ON DELETE SET NULL,
    actor_id   BIGINT      NOT NULL REFERENCES users (id),
    action     VARCHAR(50) NOT NULL,
    details    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_activity_project ON activity_log (project_id, created_at DESC);
CREATE INDEX ix_activity_issue   ON activity_log (issue_id, created_at DESC);

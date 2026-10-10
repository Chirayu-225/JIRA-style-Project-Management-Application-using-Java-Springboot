# Topics This Project Uses: A Study Checklist

Every concept the Issue Tracker relies on, grouped by area. For each one: **what it is** in a line, and **where to see it** in this codebase, so you can learn the idea and then read real code that uses it.

- ★ = asked about often in SDE interviews; learn these first
- Tick the boxes `[x]` as you go (GitHub renders them as checkboxes)

---

## 1. Java fundamentals

- [ ] ★ **Classes, objects, encapsulation**: private fields + getters. *See:* `issue/Issue.java`
- [ ] ★ **Interfaces**: a contract without implementation. Every repository is one. *See:* `issue/IssueRepository.java`
- [ ] **Enums with behaviour**: an enum that has methods. *See:* `project/ProjectRole.java` (`atLeast()`)
- [ ] **Records (Java 16+)**: compact, immutable data classes. *See:* all DTOs in `issue/IssueDtos.java`
- [ ] ★ **Collections**: `List`, `Map`, `Set`, `HashMap`, `EnumMap`. *See:* `project/ProjectService.listMine`, `seed/DemoDataSeeder`
- [ ] **`Optional`**: a value that may be missing, instead of `null`. *See:* `findById(...).orElseThrow(...)` everywhere
- [ ] ★ **Streams & lambdas**: `list.stream().map(...).toList()`. *See:* `issue/IssueService.search`
- [ ] **Method references**: `IssueResponse::from`
- [ ] ★ **Exceptions**: checked vs unchecked, custom exceptions. *See:* `common/ApiException.java`
- [ ] **Generics**: `PageResponse<T>`. *See:* `common/PageResponse.java`
- [ ] **Annotations**: metadata Spring reads at startup (`@Service`, `@Entity`…)
- [ ] **Text blocks**: multi-line strings (`"""`). *See:* the native SQL query in `IssueRepository`
- [ ] **Regular expressions**: *See:* `ISSUE_KEY` pattern in `IssueService`
- [ ] **Packages & package-by-feature structure**: `user/`, `project/`, `issue/`…

## 2. Spring Boot & backend development

- [ ] ★ **Inversion of Control & Dependency Injection**: Spring creates objects (beans) and passes them into constructors. *See:* any service constructor
- [ ] **Beans & component scanning**: `@Service`, `@Component`, `@RestController`, `@Configuration`, `@Bean`
- [ ] **Auto-configuration & starters**: why adding `spring-boot-starter-web` gives you a web server. *See:* `pom.xml`
- [ ] ★ **Layered architecture**: Controller → Service → Repository. *See:* the `issue/` package
- [ ] ★ **REST controllers**: `@GetMapping`, `@PathVariable`, `@RequestParam`, `@RequestBody`. *See:* `issue/IssueController.java`
- [ ] ★ **DTO pattern**: never expose entities directly over the API. *See:* `IssueDtos`, `UserSummary`
- [ ] **Bean Validation**: `@Valid`, `@NotBlank`, `@Size`, `@Email`. *See:* `user/AuthDtos.java`
- [ ] ★ **Centralised error handling**: `@RestControllerAdvice`. *See:* `common/GlobalExceptionHandler.java`
- [ ] **Externalised configuration**: `application.yml` + environment variables. *See:* `application.yml`
- [ ] **Application events**: `ApplicationEventPublisher` + listeners. *See:* `realtime/EventBroadcaster`
- [ ] **Scheduled tasks**: `@Scheduled`. *See:* SSE heartbeat in `EventBroadcaster`
- [ ] **Startup runners**: `ApplicationRunner`, conditional beans. *See:* `seed/DemoDataSeeder`
- [ ] **Health checks & monitoring**: Spring Boot Actuator `/actuator/health`
- [ ] **API documentation**: OpenAPI / Swagger UI. *See:* `config/OpenApiConfig.java`
- [ ] **Logging**: SLF4J loggers and log levels

## 3. Databases & SQL

- [ ] ★ **Relational modelling**: tables, rows, primary keys, foreign keys. *See:* `V1__init_schema.sql`
- [ ] ★ **Relationships**: one-to-many (project → issues), many-to-many through a join table (`project_members`)
- [ ] **Normalisation**: why `users` are stored once and referenced by id
- [ ] ★ **Constraints**: `NOT NULL`, `UNIQUE`, `CHECK`, `FOREIGN KEY`. *See:* schema
- [ ] **Referential actions**: `ON DELETE CASCADE` vs `ON DELETE SET NULL`
- [ ] ★ **Indexes**: B-tree, composite `(project_id, status, position)`, and why column order matters
- [ ] **Partial (filtered) unique index**: one active sprint per project. *See:* `ux_one_active_sprint`
- [ ] **Expression index**: case-insensitive unique email `lower(email)`
- [ ] ★ **Joins**: inner join, `GROUP BY`, aggregate functions (`count`, `max`, `coalesce`)
- [ ] ★ **Transactions & ACID**: all-or-nothing changes. *See:* `@Transactional` on service methods
- [ ] ★ **Pessimistic locking**: `SELECT … FOR UPDATE`. *See:* `ProjectRepository.findByIdForUpdate`
- [ ] ★ **Optimistic locking**: version column, lost-update problem. *See:* `@Version` in `Issue.java`
- [ ] **Isolation levels & race conditions**: what can go wrong with concurrent writes
- [ ] **Full-text search**: `tsvector`, `tsquery`, stemming, ranking, GIN index. *See:* `search_vector` column + `IssueRepository.searchIds`
- [ ] **Generated columns**: Postgres computes `search_vector` automatically
- [ ] ★ **Schema migrations**: versioned, immutable migration files (Flyway). *See:* `db/migration/`
- [ ] **Connection pooling**: why apps reuse DB connections (HikariCP). *See:* `hikari.maximum-pool-size`
- [ ] **Managed / serverless databases**: Neon, scale-to-zero, SSL connections (`sslmode=require`)

## 4. ORM: JPA & Hibernate

- [ ] ★ **What an ORM does**: maps classes ↔ tables, objects ↔ rows
- [ ] **Entity mapping**: `@Entity`, `@Table`, `@Column`, `@Id`, `@GeneratedValue`
- [ ] **Associations**: `@ManyToOne`, `@JoinColumn`
- [ ] ★ **Lazy vs eager loading**: `FetchType.LAZY`
- [ ] ★ **The N+1 query problem** and fixes: `@EntityGraph`, `join fetch`. *See:* `IssueRepository`, `ProjectMemberRepository`
- [ ] **Persistence context & dirty checking**: changing a loaded entity saves automatically on commit. *See:* `IssueService.update`
- [ ] **Spring Data derived queries**: `findByProjectIdAndNumber(...)`
- [ ] **JPQL vs native SQL**: `@Query` both ways. *See:* `IssueRepository`
- [ ] **Dynamic queries with Specifications**: combinable filters. *See:* `issue/IssueSpecs.java`
- [ ] **Bulk updates**: `@Modifying` queries. *See:* `moveUnfinishedToBacklog`
- [ ] **Pagination & sorting**: `Pageable`, `PageRequest`, `Sort`
- [ ] **Open Session in View**: why it's turned off (`open-in-view: false`)

## 5. Security

- [ ] ★ **Authentication vs authorisation**: who you are vs what you may do
- [ ] ★ **Password hashing**: BCrypt, salts, why never to store plain passwords. *See:* `SecurityConfig.passwordEncoder`
- [ ] ★ **JWT (JSON Web Tokens)**: header.payload.signature, expiry, signing keys. *See:* `security/JwtService.java`
- [ ] **Stateless vs session-based auth**: trade-offs (JWTs can't be revoked early)
- [ ] **Servlet filters & the security filter chain**: *See:* `JwtAuthFilter`, `SecurityConfig`
- [ ] ★ **Role-based access control (RBAC)**: *See:* `ProjectRole`, `ProjectAccess.require`
- [ ] ★ **CORS**: why browsers block cross-origin calls and how the server allows them. *See:* `corsConfigurationSource`
- [ ] **CSRF**: what it is and why token-based APIs can disable it
- [ ] **Secrets management**: environment variables, never committing secrets, rotating leaked credentials
- [ ] **Information leakage**: returning 404 instead of 403 to non-members
- [ ] **Input validation** as a security layer
- [ ] **Tokens in URLs**: why it's a trade-off for EventSource (they can appear in logs)

## 6. Real-time systems & concurrency

- [ ] ★ **Polling vs long-polling vs SSE vs WebSockets**: when to use each
- [ ] **Server-Sent Events**: one-way server push over HTTP. *See:* `realtime/` + `ProjectLayout.tsx`
- [ ] **Publish/subscribe pattern**: services publish events, broadcaster fans out
- [ ] **Transactional events**: publishing only after commit (`AFTER_COMMIT`)
- [ ] **Heartbeats / keep-alives**: keeping idle connections open through proxies
- [ ] **Thread-safe collections**: `ConcurrentHashMap`, concurrent sets
- [ ] ★ **Race conditions**: duplicate issue numbers, double sprint start, and how each is prevented
- [ ] **Scaling real-time across servers**: Redis Pub/Sub, Postgres `LISTEN/NOTIFY` (design discussion)

## 7. Algorithms & data-structure ideas

- [ ] **Fractional indexing**: O(1)-write ordering for drag-and-drop, plus occasional rebalancing. *See:* `IssueService.move`
- [ ] **Search ranking**: weighting title above description (`setweight`, `ts_rank`)
- [ ] **Debouncing**: wait until typing stops before calling the API. *See:* `GlobalSearch` in `ProjectLayout.tsx`
- [ ] **Hashing for stable choices**: same user → same avatar colour. *See:* `colorFor` in `bits.tsx`
- [ ] **Grouping & aggregation in memory**: `Map` of sprint → issues. *See:* `BacklogPage.tsx`

## 8. API design

- [ ] ★ **REST principles**: resources, nouns in URLs, HTTP verbs
- [ ] **Nested resources**: `/projects/{id}/issues/{issueId}/comments`
- [ ] ★ **HTTP status codes**: 200, 201, 204, 400, 401, 403, 404, 409, 500, and when to use each
- [ ] **Error format standard**: RFC 7807 Problem Details
- [ ] **Filtering, pagination & search parameters**: `?status=&sprintId=&page=&size=`
- [ ] **PUT vs PATCH vs POST** semantics (this API uses PUT for full updates, POST for actions like `/move`)
- [ ] **API documentation** with OpenAPI

## 9. Frontend development

**Web fundamentals**
- [ ] ★ **HTML semantics & accessibility**: buttons vs links, `aria-*` labels, keyboard navigation, focus states
- [ ] ★ **CSS layout**: Flexbox and CSS Grid. *See:* `.board`, `.app-shell` in `styles.css`
- [ ] **CSS custom properties (variables)** as a design-token system
- [ ] ★ **Responsive design**: media queries, mobile-first thinking, `scroll-snap`
- [ ] **Theming / dark mode**: `[data-theme]`, `prefers-color-scheme`, avoiding a flash on load. *See:* `theme.tsx`, `index.html`
- [ ] **Animations & `prefers-reduced-motion`**
- [ ] **Browser storage**: `localStorage` for tokens and preferences

**TypeScript**
- [ ] ★ **Static typing**: interfaces, type aliases, union types (`'TODO' | 'DONE'`). *See:* `api.ts`
- [ ] **Generics & utility types**: `Partial<T>`, `Record<K, V>`
- [ ] **Type narrowing**: `instanceof ApiError`

**React**
- [ ] ★ **Components, props, JSX**
- [ ] ★ **State & hooks**: `useState`, `useEffect`, `useMemo`, `useCallback`, `useRef`
- [ ] **Context for global state**: auth, theme, project. *See:* `auth.tsx`, `theme.tsx`
- [ ] **Controlled forms**: inputs bound to state. *See:* `IssueModal.tsx`
- [ ] **Client-side routing**: nested routes, `<Outlet>`, route guards, URL state (`?issue=`). *See:* `App.tsx`
- [ ] ★ **Async data fetching**: `fetch`, `async/await`, loading and error states
- [ ] ★ **Optimistic UI updates with rollback**: *See:* `BoardPage.onDragEnd`
- [ ] **Drag and drop**: sensors, droppables, overlays (dnd-kit)
- [ ] **Keyboard shortcuts & global event listeners**: *See:* `ProjectLayout.tsx`
- [ ] **Single-Page Application (SPA) hosting**: why every URL must serve `index.html` (`vercel.json`, `nginx.conf`)

## 10. Testing

- [ ] ★ **Test pyramid**: unit vs integration vs end-to-end tests
- [ ] **JUnit 5**: test structure and assertions (AssertJ)
- [ ] **Spring MockMvc**: calling the API without a real server
- [ ] **Testcontainers**: tests against a real PostgreSQL in Docker
- [ ] **Testing security & concurrency rules**: 401/403/404/409 cases. *See:* `IssueTrackerApiIntegrationTest`
- [ ] **Type-checking as a test**: `tsc` in the frontend build

## 11. Tools, DevOps & deployment

- [ ] ★ **Git & GitHub**: commits, branches, pull requests, `.gitignore`
- [ ] **Build tools & dependency management**: Maven (`pom.xml`), npm (`package.json`, lockfiles)
- [ ] **Environment variables & PATH**: `JAVA_HOME`, `PATH`, per-terminal vs permanent
- [ ] **The 12-Factor App**: config in the environment, stateless processes
- [ ] ★ **Docker**: images, containers, layers, multi-stage builds, `.dockerignore`. *See:* both `Dockerfile`s
- [ ] **Docker Compose**: multi-container local environments. *See:* `docker-compose.yml`
- [ ] ★ **Continuous Integration**: GitHub Actions running build + tests on every push. *See:* `.github/workflows/ci.yml`
- [ ] **Platform-as-a-Service deployment**: Render (API), Vercel (static site), Neon (DB)
- [ ] **Health checks, cold starts & free-tier sleep**
- [ ] **JVM memory in containers**: `-XX:MaxRAMPercentage`
- [ ] **Reverse proxies & static serving**: nginx config
- [ ] **HTTPS, domains & CORS in production**

## 12. System design concepts

- [ ] ★ **Client–server architecture & three-tier apps**: browser, API, database
- [ ] ★ **Stateless services & horizontal scaling**: why JWT helps
- [ ] **Load balancers** and what changes with multiple instances
- [ ] **Multi-tenancy**: many teams' data in one database, isolated by `project_id` + membership checks
- [ ] **Audit logging**: append-only activity history
- [ ] **Consistency trade-offs**: DB constraints vs application checks
- [ ] **Caching**: not used yet. Where would Redis help? (a good interview discussion)
- [ ] **Search at scale**: Postgres FTS → Elasticsearch/OpenSearch as data grows

## 13. Software engineering practices

- [ ] ★ **Separation of concerns & single responsibility**
- [ ] **Design patterns used here**: Repository, DTO, Service layer, Specification, Observer/Pub-Sub, Builder (JWT), Strategy-like enums
- [ ] **SOLID principles** (especially Single Responsibility and Dependency Inversion)
- [ ] **Clean, readable code**: naming, small methods, comments that explain *why*
- [ ] **Documentation**: README, setup guides, API docs
- [ ] **Debugging from logs and stack traces**: reading the *first* `Caused by:` line (you did this already!)

---

## Suggested study order

| Week | Focus | Sections |
|---|---|---|
| 1 | Java + Spring basics, run and trace one request | 1, 2 |
| 2 | Databases, SQL and JPA, the core of most interviews | 3, 4 |
| 3 | Security + API design | 5, 8 |
| 4 | Frontend + testing | 9, 10 |
| 5 | Docker, CI, deployment, then system design talking points | 11, 12, 6 |

**How to study each topic:** read the one-line idea → open the file listed → change something small and watch what breaks → explain it out loud in two sentences, as if to an interviewer.

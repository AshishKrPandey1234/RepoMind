# Interview Notes

**Status:** Milestone 2 implements a React/Vite dashboard, Spring Boot API foundation, GitHub OAuth session, current-user endpoint, and paginated public repository listing. Indexing and AI features remain planned; OAuth end-to-end use requires a locally registered GitHub OAuth App.

## 30-second project explanation

RepoMind is a full-stack repository assistant in progress. Implemented today: a React/Vite client talks to a Java 21/Spring Boot modular monolith, users sign in through GitHub OAuth, and the server lists public repositories visible to that authorization. The GitHub authorized client stays in the backend session; the browser sees only a session cookie and a limited profile/repository DTO. Repository selection, indexing, line-aware chunking, vector retrieval, grounded answers, and MySQL workflow persistence are future work.

## Why a modular monolith?

The project has separable concerns—identity, repository access, indexing, retrieval/chat, and persistence—but they share a small team, domain model, and release cadence. Internal module boundaries make the code explainable and testable without introducing network deployments, distributed transactions, or service discovery. A worker process can be separated later if indexing volume justifies it; it need not become an independently designed microservice now.

## Why relational plus vector storage?

MySQL is suitable for users, repository connections, job states, conversations, and messages because those records need durable constraints and predictable queries. Vector similarity search serves a different access pattern: finding semantically relevant source chunks. The vector data includes provenance and is rebuildable from a GitHub commit. MySQL authorization checks happen before vector queries; vector metadata filters add scope but do not enforce access control on their own.

## Explain indexing end to end

1. The user selects a repository and branch returned for their GitHub identity.
2. The server revalidates access and resolves the branch to a commit SHA.
3. It persists a queued job and uses bounded asynchronous work.
4. The GitHub adapter retrieves the tree and eligible text files.
5. A policy filter excludes binaries, generated/dependency output, oversized files, and likely secrets.
6. A chunker keeps overlap and 1-based source line ranges.
7. Configured embeddings are written with repository, branch, commit, path, line, chunk, and job metadata.
8. The job reaches `SUCCEEDED` only when the snapshot is complete; failures keep a safe error code and do not masquerade as a complete index.

## Explain RAG and citations

The question is embedded and searched against the active repository/commit snapshot. The prompt contains a bounded set of retrieved chunks and treats repository content as untrusted data. The model is asked to return answer text plus retrieved chunk IDs. The server validates those IDs and constructs citation path/line data from stored metadata. If retrieval provides no useful evidence, the answer should say so. This makes sources inspectable, but it does not prove every generated statement is correct; users still review evidence.

## Authentication and authorization

The implemented flow uses Spring Security's OAuth2 login support. The browser starts at `/oauth2/authorization/github`; Spring Security stores state in the server session, handles the code exchange, and saves the GitHub authorized client in `HttpSessionOAuth2AuthorizedClientRepository`. The callback passes through Vite at `localhost:5173/login/oauth2/code/github`. `/api/auth/me` projects only GitHub ID, login, display name, and avatar URL. The session cookie is HttpOnly and SameSite=Lax; unsafe requests use a CSRF token fetched from `/api/auth/csrf`. The React app keeps that CSRF token in memory and sends it with logout. OAuth client credentials and access tokens remain on the backend. No local user row is persisted yet.

This milestone requests `read:user` and `public_repo`, then filters repository results to `visibility=public`. The `public_repo` OAuth scope permits full access to public repositories, including hooks, despite this implementation only issuing GET requests. That permission scope is broader than the code's behavior; moving to a GitHub App with read-only permissions is a design decision before private/content access.

## Failure handling

- GitHub failure/rate limiting: do not report a successful index; record safe job code and retry only within bounded policy.
- AI/vector outage: return a stable dependency error or mark indexing failed; do not invent grounded output.
- Invalid input: bean/request validation and stable field errors.
- Unauthorized object ID: verify owner on each access and consider `404` to avoid revealing existence.
- Partial indexing: distinguish incomplete job state and activate only a completed snapshot.
- Secret filtering: defense-in-depth file/path and pattern checks, size limits, redacted logs. Scanners can miss secrets, so filtering is risk reduction, not a guarantee.

## Trade-offs and open choices to be ready to discuss

- OAuth App simplicity versus GitHub App fine-grained, read-only repository permissions.
- Session cookie versus token-based browser auth; cookie avoids exposing bearer tokens but requires CSRF defenses.
- Chroma versus another compatible vector store: decide based on Spring AI adapter support, deployment, filtering, persistence, and operational needs.
- Chunk size/overlap and retrieval top-k affect context quality, latency, and model cost; tune with representative repositories and tests.
- Snapshot isolation improves reproducibility but consumes storage; define cleanup/retention.
- Keep model/embedding adapters configurable; changing embedding dimensions requires reindexing.
- A bounded executor keeps first deployment simple; larger workloads may justify a durable queue, with job idempotency maintained.
- Generated answers are probabilistic; citations improve traceability but do not eliminate hallucination.

## Evidence to add during implementation

For interview readiness, retain architecture decisions, migration history, test output, and a small demo repository with known Q&A answers. Explain only behavior supported by code and tests. Document provider/model versions, prompt/citation validation, file filter behavior, authorization tests, failure tests, and a deployment walkthrough as those milestones are completed.

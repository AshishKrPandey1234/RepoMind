# Interview Notes

**Status:** RepoMind implements GitHub OAuth, public repository and branch browsing, bounded asynchronous indexing, MySQL source chunks, Spring AI Ollama embeddings, Chroma semantic search, and stateless repository Q&A. Q&A additionally requires a locally installed chat-capable Ollama model configured with `REPOMIND_CHAT_MODEL`. Summaries and interview generation remain planned.

## 30-second project explanation

RepoMind is a full-stack repository assistant in progress. The React/Vite client talks to a Java 21/Spring Boot modular monolith. Users sign in through GitHub, browse public repositories and branches, queue indexing, search a completed snapshot, and ask natural-language questions about the code. The GitHub authorized client stays in the backend session. A bounded worker filters files, pins content to a commit, creates overlapping line-aware chunks, and stores completed chunks and job metadata in MySQL. Spring AI uses local Ollama models and Chroma to retrieve relevant chunks and generate answers with server-validated file/line citations.

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
7. Eligible chunks and the `SUCCEEDED` job transition are committed together to MySQL; failures store only a safe code and no partial chunks.
8. After a process restart, queued/running jobs become `FAILED` with `SERVER_RESTARTED`; the user retries by creating a new job.
9. The first semantic search lazily embeds pending chunks in bounded batches and marks completed work in MySQL; stable chunk IDs make retries idempotent.

## Explain RAG and citations

Semantic retrieval embeds the question and searches the authenticated user's latest completed repository/commit snapshot. Q&A reuses those results and sends a bounded context to the local chat model. Repository text is untrusted input. The model returns application-issued source IDs; the API maps only valid IDs back to retrieved path, line range, branch, and commit metadata. If evidence or valid citations are absent, the service abstains. This constrains fabricated citations, though model answers still need human judgment.

## Authentication and authorization

The implemented flow uses Spring Security's OAuth2 login support. The browser starts at `/oauth2/authorization/github`; Spring Security stores state in the server session, handles the code exchange, and saves the GitHub authorized client in `HttpSessionOAuth2AuthorizedClientRepository`. The callback passes through Vite at `localhost:5173/login/oauth2/code/github`. `/api/auth/me` projects only GitHub ID, login, display name, and avatar URL. The session cookie is HttpOnly and SameSite=Lax; unsafe requests use a CSRF token fetched from `/api/auth/csrf`. The React app keeps that CSRF token in memory and sends it with logout. OAuth client credentials and access tokens remain on the backend. No local user row is persisted yet.

This milestone requests `read:user` and `public_repo`, then filters repository results to `visibility=public`. The `public_repo` OAuth scope permits full access to public repositories, including hooks, despite this implementation only issuing GET requests. That permission scope is broader than the code's behavior; moving to a GitHub App with read-only permissions is a design decision before private/content access.

## Failure handling

- GitHub failure/rate limiting: do not report a successful index; record safe job code and retry by starting a new job.
- Ollama/Chroma outage: return a stable dependency error; successfully embedded batches remain reusable and pending chunks are retried on a later request.
- Invalid input: bean/request validation and stable field errors.
- Unauthorized job ID: query by both job ID and authenticated GitHub user ID and return `404` for another user's job.
- Partial indexing: database chunks are written only in the final success transaction; failed work is not exposed as an index.
- Secret filtering: defense-in-depth file/path and pattern checks, size limits, redacted logs. Scanners can miss secrets, so filtering is risk reduction, not a guarantee.

## Trade-offs and open choices to be ready to discuss

- OAuth App simplicity versus GitHub App fine-grained, read-only repository permissions.
- Session cookie versus token-based browser auth; cookie avoids exposing bearer tokens but requires CSRF defenses.
- Chroma was selected for this milestone through Spring AI's vector-store interface; a persistent service and Ollama model must be running locally.
- Chunk size/overlap and retrieval top-k affect context quality, latency, and model cost; tune with representative repositories and tests.
- Snapshot isolation improves reproducibility but consumes storage; define cleanup/retention.
- Keep model settings externalized. Model changes re-embed the selected snapshot from MySQL; dimension changes need a new Chroma collection before re-embedding.
- A bounded executor keeps first deployment simple; larger workloads may justify a durable queue, with job idempotency maintained.
- Generated answers are probabilistic; citations improve traceability but do not eliminate hallucination.

## Evidence to add during implementation

For interview readiness, retain architecture decisions, migration history, test output, and a small demo repository with known retrieval questions. Explain only behavior supported by code and tests. Document provider/model versions, retrieval filters, file policy, authorization tests, failure tests, and a deployment walkthrough as milestones are completed.

# RepoMind Project Specification

**Status:** Milestone 2 implements the React/Vite dashboard, Spring Boot API foundation, GitHub OAuth session, current-user endpoint, and paginated public repository listing. Local user/repository persistence, indexing, and AI are not implemented.

## 1. Problem statement

Developers joining an unfamiliar project spend time locating relevant code, tracing relationships between files, and forming a reliable mental model of the system. RepoMind will let a user connect a GitHub identity, select an accessible repository and branch, index eligible code, and ask natural-language questions answered with verifiable file and line references. It will also generate repository summaries and interview questions with suggested, repository-grounded answers.

## 2. Goals and boundaries

### Goals

- Authenticate users through GitHub and list only repositories GitHub authorizes them to see. **Milestone 2 scope:** public repositories only.
- Allow repository and branch selection and provide durable indexing status.
- Fetch, filter, chunk, embed, and index source files with provenance metadata.
- Answer questions using scoped retrieval and cite relevant file paths and line ranges.
- Generate a repository overview and project-specific interview practice.
- Store user profiles, repository records, indexing jobs, and chat history in MySQL.
- Handle invalid input, authorization failures, GitHub/API limits, indexing errors, and unavailable AI/vector services with useful, non-sensitive errors.

### Non-goals for the first version

- Writing, committing, or modifying source code in GitHub repositories.
- Organization-wide administration or background synchronization of every repository.
- Training or fine-tuning a model on repository content.
- Multi-service deployment; the backend remains a modular monolith.
- Guarantees that generated answers are correct; citations make evidence inspectable.

## 3. User stories

1. As a developer, I sign in with GitHub and see repositories available to my identity.
2. I choose a repository and branch, start indexing, and can see progress or a useful failure state.
3. I ask a question and receive an answer with file paths and line ranges that I can inspect.
4. I request a repository summary or interview questions with suggested answers and sources.
5. I can return to my own conversations, while another user cannot read them or use their repository records.

## 4. Functional requirements and acceptance intent

| ID | Requirement | Acceptance intent |
|---|---|---|
| FR-01 | GitHub sign-in | **Implemented in Milestone 2:** Spring Security validates OAuth state, returns the GitHub profile through `/api/auth/me`, and retains the authorized client in a server-side session. Local user persistence remains planned. |
| FR-02 | Repository listing and later branch selection | **Partially implemented in Milestone 2:** authenticated, paginated listing of public repositories. Branch selection and locally connected repository records remain planned. |
| FR-03 | Indexing | Each job records repository, branch, resolved commit, state, timestamps, and safe failure details. |
| FR-04 | File policy | Binary, generated, oversized, and likely secret-bearing files are excluded before embedding. |
| FR-05 | Provenance | Each indexed chunk keeps path, line start/end, commit SHA, branch/repository scope, and stable chunk ID. |
| FR-06 | Q&A | Retrieval is scoped to an authorized indexed repository/branch; returned citations refer to retrieved chunks. |
| FR-07 | Summary and interview practice | These use retrieved repository evidence and return citations where appropriate. |
| FR-08 | History | Conversations and messages are persisted and owner-checked. |
| FR-09 | Resilience | External provider failures, rate limits, invalid input, and job failures have stable API errors and actionable job state. |

## 5. Quality and security requirements

- Modular monolith with controller, DTO, service, repository, and configuration boundaries.
- Validate request bodies, identifiers, branch names, file sizes, and paging limits.
- Never expose OAuth tokens, provider keys, raw exception details, or secret-like file contents in responses/logs.
- Authorize repository, job, and conversation reads and mutations using the authenticated owner, not a client-supplied user ID.
- Use least-privilege GitHub scopes and only request repository content access when required. Evaluate a GitHub App if finer-grained access is preferred.
- Use CSRF protection for cookie-authenticated state-changing requests, secure cookie attributes, strict CORS, and rate limiting at a suitable layer.
- Avoid logging prompts or repository source by default; define retention and deletion behavior before production.
- Ensure answer citations are derived from retrieval records; treat repository content as untrusted prompt input.
- Add unit and integration tests in later implementation milestones; documentation alone does not establish these properties.

Milestone 2 uses the OAuth App scopes `read:user` and `public_repo`. The latter permits full access to public repositories, including hooks, even though the current service only reads repository metadata. Private repository access is not requested. GitHub Apps provide finer-grained read-only permissions and remain a future consideration.

## 6. Planned architecture

```mermaid
flowchart TB
  Browser[React client]
  subgraph Monolith[Spring Boot modular monolith]
    Web[Controllers + request/response DTOs]
    Security[Spring Security + OAuth/session]
    Repos[Repository catalog service]
    Index[Indexing job + file pipeline]
    Chat[Chat, summary, interview services]
    Ports[GitHub, AI, vector-store adapters]
    Persistence[Spring Data repositories]
    Web --> Security
    Web --> Repos
    Web --> Index
    Web --> Chat
    Repos --> Ports
    Index --> Ports
    Chat --> Ports
    Repos --> Persistence
    Index --> Persistence
    Chat --> Persistence
  end
  Browser --> Web
  Ports --> GH[GitHub REST API]
  Persistence --> MySQL[(MySQL)]
  Ports --> VDB[(Vector store)]
  Ports --> Model[Configurable model provider]
```

### Module responsibilities

- **security/auth:** OAuth initiation/callback, local user mapping, session lifecycle, current-user access.
- **repository:** GitHub repository/branch listing and local repository metadata.
- **indexing:** job orchestration, file policy, chunking, embedding, vector writes/deletes, retries and status.
- **chat:** conversation ownership, retrieval, prompt assembly, grounded answer generation, summary/interview use cases.
- **persistence:** relational mappings and migrations for application records.
- **integrations:** GitHub client, Spring AI model/embedding adapters, vector-store adapter, configuration and health checks.

## 7. Indexing workflow

1. Authenticate the user and verify that the repository is returned by GitHub for that user's authorization.
2. Validate the selected branch; resolve it to a commit SHA so one job indexes an immutable snapshot.
3. Create a `QUEUED` job. A bounded asynchronous worker transitions through `RUNNING` to `SUCCEEDED` or `FAILED`; persist counts and safe error codes.
4. Retrieve a bounded recursive tree or paginated content listing and fetch only supported text files. Respect GitHub rate limits and cancellation where feasible.
5. Apply allow/deny rules: reject binary encodings, known generated/vendor/build directories, oversized files, and likely secrets (credential patterns and sensitive filenames). Rules should be configurable and reviewed; filtering reduces exposure but cannot guarantee detection.
6. Normalize line endings without losing line mapping; split source into token/character-bounded overlapping chunks and preserve 1-based line ranges.
7. Generate embeddings via a configured provider and write vectors with repository ID, owner ID, branch, commit SHA, path, line range, language, chunk ID, and indexing job ID.
8. Mark the snapshot active only after successful writes. On failure, report the job failure and avoid presenting a partial snapshot as complete. An explicit retry creates a new job attempt; it does not reset the failed job. Cleanup/replacement strategy is a detailed implementation decision.

```mermaid
stateDiagram-v2
  [*] --> QUEUED
  QUEUED --> RUNNING
  RUNNING --> SUCCEEDED
  RUNNING --> FAILED
  RUNNING --> CANCELLED
  QUEUED --> CANCELLED
  FAILED --> [*]
```

## 8. RAG workflow

The API first authorizes the user against the requested local repository and conversation. It embeds the question and retrieves a bounded set of matching chunks filtered by repository, branch, and active commit. The prompt instructs the model to answer only from supplied evidence, state when evidence is insufficient, and return chunk IDs as citations. The server resolves IDs to trusted metadata and emits paths and line ranges. Chat history is bounded and treated as context, not as authority. Repository text is untrusted input and must not override system instructions or trigger tools. The initial version does not require reranking or model tool-calling.

## 9. Failure behavior

- Invalid JSON, missing values, invalid branch/repository selection: `400` with field-level details.
- Unauthenticated request: `401`; authenticated user lacks ownership/access: `403` or deliberately non-disclosing `404`.
- GitHub rate limit/unavailable: stable integration error; indexing job records retryable/non-retryable safe code.
- AI/vector unavailable: `503` for synchronous request or `FAILED` job state; do not fabricate an answer or mark an index complete.
- Empty retrieval: return a clear insufficient-context answer with no unsupported citations.
- Internal failures: correlation ID and generic response; protected logs hold diagnostic context without credentials/source content.

## 10. Open decisions

- Whether to migrate from the implemented OAuth App to a GitHub App for finer-grained, read-only repository permissions before adding repository content access.
- Whether to retain GitHub tokens after login and how to encrypt/refresh/revoke them. Needed for private-repository indexing; do not persist plaintext.
- Spring Boot and Spring AI compatible release versions, embedding/chat provider, model dimensions, and cost limits.
- Vector store deployment and isolation strategy; exact Chroma/compatible adapter version.
- Indexing queue/executor strategy, job concurrency, retry limits, and maximum repository/file/chunk quotas.
- Schema migration tool, vector data retention/deletion, conversation retention, and account deletion behavior.
- Deployment host, TLS/reverse proxy, secrets manager, backups, monitoring, and public demo constraints.

## 11. Delivery plan

1. Documentation and architecture review (complete).
2. Local project foundation, environment-based database configuration, health endpoint, and starter verification (complete).
3. GitHub OAuth/session, authenticated identity, and public repository listing (implemented; end-to-end OAuth configuration remains a local verification step).
4. Repository persistence, branch listing/selection, and ownership checks.
5. Index job model and secure source retrieval/filtering/chunk metadata.
6. Embeddings/vector adapter and indexing lifecycle tests.
7. Scoped RAG endpoints, citation validation, chat history, and failure handling.
8. Summary/interview use cases.
9. React client, accessibility/error states, and API integration.
10. Deployment configuration, operational documentation, end-to-end verification, and interview walkthrough.

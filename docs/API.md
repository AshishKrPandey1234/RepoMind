# API Contract

**Status:** Health, OAuth/session, public repository and branch listing, indexing jobs, semantic code search, and repository Q&A are implemented. Summaries and interview-generation routes remain planned. All `/api` routes use HTTPS in deployed environments. Authentication uses a backend-managed session cookie; the client does not submit a user ID or GitHub token.

The repository listing is limited to public repositories in this milestone. It uses the current user's GitHub OAuth authorization and is paginated using GitHub API pages.

| Method | Path | Purpose | Expected response |
|---|---|---|---|
| GET | `/api/health` | Confirm the API process is responding; does not verify database connectivity | `200` with `{"status":"ok","service":"repomind-api"}` |
| GET | `/api/auth/me` | Read current GitHub profile/session state | `200` |
| GET | `/api/auth/csrf` | Bootstrap a CSRF token for unsafe browser requests | `200` |
| POST | `/api/auth/logout` | Invalidate the current server session | `204` |
| GET | `/api/repositories?page=0&size=30` | List public repositories accessible to the authenticated GitHub user | `200` |
| GET | `/api/repositories/{repositoryId}/branches?page=0&size=50` | List accessible repository branches | `200` |
| POST | `/api/index-jobs` | Validate and queue indexing for a repository ID and branch | `202` |
| GET | `/api/index-jobs/{jobId}` | Read current user's job status, counts, and safe error code | `200` |
| POST | `/api/repositories/{repositoryId}/search` | Embed pending chunks for the latest completed snapshot and retrieve relevant code | `200` |
| POST | `/api/repositories/{repositoryId}/ask` | Generate a local-model answer from retrieved code with verified source references | `200` |

## Conventions

- Base path: `/api`; JSON request and response bodies unless stated otherwise.
- IDs are opaque strings in the public API even if stored as numeric database keys.
- Paginate list endpoints with zero-based `page` and bounded `size`; future local lists default to 20. The GitHub repository endpoint defaults to 30 and returns `items`, `page`, `size`, and `hasNext` from GitHub pagination.
- Validate request fields and enforce per-user resource ownership on every call.
- Cookie-authenticated state-changing requests require the configured CSRF token/header.
- Planned error envelope for future domain endpoints:

```json
{
  "type": "about:blank",
  "title": "Invalid request",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "detail": "One or more fields are invalid.",
  "fieldErrors": [{"field": "branch", "message": "must not be blank"}],
  "requestId": "opaque-correlation-id"
}
```

The current GitHub integration instead returns compact `{ "code", "message" }` JSON errors and a `Retry-After` header where available. An unauthenticated protected API request returns `401`; a failed CSRF check returns `403`. Responses do not include stack traces, tokens, provider secrets, or raw upstream bodies.

## Authentication

| Method | Path | Purpose | Expected response |
|---|---|---|---|
| GET | `/oauth2/authorization/github` | Start Spring Security OAuth authorization; redirects to GitHub | `302` |
| GET | `/login/oauth2/code/github` | Default Spring Security callback; validates state, exchanges code, creates session | `302` to the configured frontend URL |
| GET | `/api/auth/me` | Return current GitHub profile and session status; available unauthenticated for session checks | `200` |
| GET | `/api/auth/csrf` | Return `{ "headerName", "token" }` for the current session's CSRF protection | `200` |
| POST | `/api/auth/logout` | Invalidate the current server session; requires the CSRF header returned above | `204` |

`/api/auth/me` examples:

```json
{"authenticated":false,"user":null}
```

```json
{"authenticated":true,"user":{"id":"583231","githubLogin":"octocat","displayName":"The Octocat","avatarUrl":"https://avatars.githubusercontent.com/u/583231"}}
```

The response contains only a small profile projection. The GitHub access token remains in the server-side authorized-client session. OAuth state is managed by Spring Security. The session cookie is `HttpOnly` and `SameSite=Lax`; it is `Secure` when `SESSION_COOKIE_SECURE=true`. The frontend keeps the CSRF token in memory and sends it in the returned header for logout. OAuth callback errors redirect to the configured frontend with the fixed code `authError=oauth_failed`; provider details and tokens are not placed in the URL.

## GitHub repositories and branches

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/repositories?page=0&size=30` | **Implemented.** List public repositories visible to the current GitHub authorization |
| GET | `/api/repositories/{repositoryId}/branches?page=0&size=50` | **Implemented.** List branches for a repository visible to the authenticated GitHub user |
| POST | `/api/repositories` | Planned repository connection persistence |

Implemented repository page response:

```json
{
  "items": [{
    "githubRepositoryId": 42,
    "fullName": "octocat/Hello-World",
    "description": "Example project",
    "visibility": "public",
    "primaryLanguage": "Java",
    "url": "https://github.com/octocat/Hello-World",
    "defaultBranch": "main"
  }],
  "page": 0,
  "size": 30,
  "hasNext": true
}
```

`page` is zero-based; `size` defaults to 30 and is bounded to 1–100. The service maps each page to GitHub's `/user/repos` endpoint and derives `hasNext` from the `Link` response header. The OAuth request uses `read:user` and `public_repo`, and the GitHub request filters `visibility=public`; private repositories are not returned. GitHub's `public_repo` scope gives the OAuth token full access to public repositories (including repository hooks), despite RepoMind only reading repository metadata. This is a known OAuth App permission limitation; GitHub Apps provide more granular permissions but are outside this milestone. Repository records are not persisted locally yet.

The future `POST /api/repositories` request should contain only a `githubRepositoryId` selected from the authenticated user's GitHub repository results. The backend resolves and validates the owner/name and other metadata from GitHub; it must not trust client-submitted ownership or visibility fields.

## Indexing jobs (implemented foundation)

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/index-jobs` | Start indexing selected public repository branch |
| GET | `/api/index-jobs/{jobId}` | Read own job status and safe progress fields |
| GET | `/api/repositories/{repositoryId}/index-status` | Read latest attempt and latest successful snapshot for this user and repository |
| GET | `/api/repositories/{repositoryId}/index-jobs` | Not implemented (job history listing) |
| POST | `/api/index-jobs/{jobId}/cancel` | Not implemented (cancellation) |

Each request creates a distinct indexing attempt. A retry after failure creates a new job rather than changing a terminal job back to `QUEUED`.

The repository status endpoint is authenticated and owner-scoped. It returns `200` with `latestJob` and `latestSuccessfulSnapshot`; either value is `null` when no matching job exists. Keeping these fields separate lets the UI report a recent failure while explaining that an older successful snapshot remains searchable. The response contains status, safe counts, timestamps, branch, and commit only; it never includes source text. A repository with no saved job returns both values as `null` without revealing whether another user indexed it.

```json
{
  "latestJob": {
    "id": "job-id",
    "status": "RUNNING",
    "repositoryFullName": "octocat/Hello-World",
    "branch": "feature",
    "commitSha": "def456...",
    "filesSeen": 12,
    "filesIndexed": 5,
    "filesSkipped": 2,
    "chunksWritten": 0,
    "indexedBytes": 4096,
    "errorCode": null,
    "createdAt": "2026-10-20T10:00:00Z",
    "startedAt": "2026-10-20T10:00:02Z",
    "finishedAt": null
  },
  "latestSuccessfulSnapshot": null
}
```

Start request:

```json
{"githubRepositoryId":42,"branch":"main"}
```

Accepted response (`202`):

```json
{"id":"550e8400-e29b-41d4-a716-446655440000","status":"QUEUED","filesSeen":0,"filesIndexed":0,"filesSkipped":0,"chunksWritten":0,"indexedBytes":0,"errorCode":null,"createdAt":"2026-10-20T10:00:00Z","startedAt":null,"finishedAt":null}
```

The request is authenticated and CSRF-protected. `githubRepositoryId` must be positive; `branch` must be nonblank and at most 255 characters. Before queueing, the server confirms the ID occurs in the authenticated user's public `/user/repos` pages (up to a configured cap), loads repository metadata by numeric GitHub ID, and resolves the branch to a commit SHA. Owner and repository names are never accepted from the browser. Only public repositories are indexed.

## Semantic code search (implemented)

`POST /api/repositories/{repositoryId}/search` requires an authenticated session and valid CSRF header. The repository ID is resolved against the latest completed indexing job owned by the authenticated GitHub user. A missing or other-user index returns the same non-disclosing `404 REPOSITORY_NOT_INDEXED` response.

Request fields: `question` is required, nonblank, and at most 1000 characters; `limit` is optional and bounded from 1 to 20 (default 8).

```json
{"question":"Where is GitHub OAuth configured?","limit":5}
```

Search embeds pending or outdated-format MySQL chunks in bounded batches. The embedded text combines each source chunk with its path, language, and detected Java declarations; Chroma keeps deterministic IDs and owner, repository, branch, commit, path, line, blob, and content-hash metadata. The `embedding_model` marker includes the context format version, so existing chunks are safely re-embedded after this change. Successful batches are marked `INDEXED` in MySQL; an interrupted vector write can be retried using the same IDs. A model/vector-store failure returns safe `503 EMBEDDING_SERVICE_UNAVAILABLE`.

For retrieval, the backend asks Chroma for up to 100 candidates filtered by owner, repository, and commit, and independently queries up to 100 lexical candidates from MySQL using the same job/owner/repository/commit scope. It combines and reranks candidates using Chroma similarity, source-term coverage, and filename/path matches, with a small source-file preference. The response `score` is a bounded hybrid relevance score, not a raw Chroma distance. Returned IDs are checked against the selected owned MySQL job, and snippets/path/lines come from MySQL. Only source snippets from the caller's latest completed commit are returned.

Successful response (`200`):

```json
{
  "repositoryId": 42,
  "repositoryFullName": "octocat/Hello-World",
  "branch": "main",
  "commitSha": "abc123...",
  "question": "Where is GitHub OAuth configured?",
  "results": [{
    "filePath": "backend/src/main/java/com/repomind/config/SecurityConfig.java",
    "lineStart": 20,
    "lineEnd": 41,
    "branch": "main",
    "commitSha": "abc123...",
    "snippet": "...",
    "score": 0.87
  }]
}
```

No matches returns an empty `results` array. This endpoint is retrieval only; it does not call a chat model or generate an answer. Safe errors include `400 INVALID_REQUEST`, `401 AUTHENTICATION_REQUIRED`, `404 REPOSITORY_NOT_INDEXED`, and `503 EMBEDDING_SERVICE_UNAVAILABLE` / `SEMANTIC_SEARCH_UNAVAILABLE`. An unavailable Ollama or Chroma service never returns upstream bodies, source logs, stack traces, or credentials.

## Repository question answering (implemented)

`POST /api/repositories/{repositoryId}/ask` requires an authenticated session and valid CSRF header. Repository ownership and snapshot selection are inherited from the existing semantic search service, which scopes the latest completed index by authenticated GitHub user ID and repository ID. Missing or another user's index returns the same non-disclosing `404 REPOSITORY_NOT_INDEXED` response.

Request fields: `question` is required, nonblank, and at most 1000 characters; `limit` is optional and bounded from 1 to 8 (default 6). The server caps retrieved prompt context at 12,000 characters.

```json
{"question":"How does GitHub OAuth login get configured?","limit":6}
```

The backend calls existing semantic/hybrid retrieval, then sends only the question and selected snippets to the configured local Ollama chat model. Retrieved code and documentation are treated as untrusted data; instructions in those files are not followed. The model returns answer text and source IDs. The API accepts only IDs it assigned to retrieved results and derives each citation's path, line range, branch, commit, snippet, and score from the existing search response. Unknown/empty source IDs or empty answers produce an explicit insufficient-evidence response with no citations. The browser does not receive GitHub tokens and the server does not log source text or prompts.

Successful response (`200`):

```json
{
  "repositoryId": 42,
  "repositoryFullName": "octocat/Hello-World",
  "branch": "main",
  "commitSha": "abc123...",
  "question": "How does GitHub OAuth login get configured?",
  "answer": "The security filter chain enables OAuth2 login and stores the authorized client in the server session.",
  "insufficientEvidence": false,
  "sources": [{
    "sourceId": "S1",
    "filePath": "backend/src/main/java/com/repomind/config/SecurityConfig.java",
    "lineStart": 20,
    "lineEnd": 41,
    "branch": "main",
    "commitSha": "abc123...",
    "snippet": "...",
    "score": 0.87
  }]
}
```

When retrieval is empty or the model produces no valid supporting citation, the same response shape uses `"insufficientEvidence": true`, an explicit abstention in `answer`, and an empty `sources` list. A chat model is not selected by default: set `REPOMIND_CHAT_MODEL` to an installed, chat-capable Ollama model tag. Missing configuration returns `503 LOCAL_CHAT_MODEL_NOT_CONFIGURED`; Ollama/model errors return `503 LOCAL_CHAT_MODEL_UNAVAILABLE`. Startup rejects non-loopback Ollama and Chroma endpoints because both services receive repository source text. Embedding and Chroma failures use the semantic-search error codes above.

`GET /api/repositories/{repositoryId}/branches?page=0&size=50` returns `items` with branch `name`, `commitSha`, and `defaultBranch`, plus `page`, `size`, and `hasNext`; page size is bounded to 100. Job responses include `filesSeen`, `filesIndexed`, `filesSkipped`, `chunksWritten`, `indexedBytes`, timestamps, and a safe `errorCode`. They never include file contents or raw exception messages. Successful chunks and job status are committed atomically; failures do not expose partial chunks. A job ID belonging to another GitHub user returns a non-disclosing `404`.

Safe failure codes include `GITHUB_AUTHORIZATION_EXPIRED`, `GITHUB_RESOURCE_NOT_FOUND`, `GITHUB_ACCESS_DENIED`, `GITHUB_RATE_LIMITED`, `GITHUB_UNAVAILABLE`, `GITHUB_ACCESS_CHECK_LIMIT`, `REPOSITORY_TREE_TOO_LARGE`, `REPOSITORY_FILE_LIMIT_EXCEEDED`, `REPOSITORY_CONTENT_LIMIT_EXCEEDED`, `INDEXING_TIMEOUT`, `ACTIVE_INDEX_JOB_LIMIT`, `INDEX_QUEUE_FULL`, `SERVER_RESTARTED`, and `INDEXING_FAILED`. The existing error mapper returns validation `400`, auth `401`, denied `403`, hidden/missing `404`, capacity/limits `429` or `422`, and upstream unavailable `503` as appropriate.

Workers and their queue are bounded. A process restart marks queued or running jobs `FAILED` with `SERVER_RESTARTED`; work is not resumed. Retry creates a new job. Per-user admission synchronization is in-process, so the initial deployment assumes one API instance; distributed quotas and shared session storage are future work.

## Conversations and AI features

| Method | Path | Purpose |
|---|---|---|
| POST | `/conversations` | Create conversation bound to an owned repository/branch |
| GET | `/conversations?page=0&size=20` | List current user's conversations |
| GET | `/conversations/{conversationId}` | Read conversation and paged messages |
| DELETE | `/conversations/{conversationId}` | Delete own conversation (retention behavior TBD) |
| POST | `/conversations/{conversationId}/messages` | Ask a repository question and persist answer |
| POST | `/repositories/{repositoryId}/summaries` | Generate grounded repository summary |
| POST | `/repositories/{repositoryId}/interview-questions` | Generate interview questions and suggested answers |

Create conversation request: `{"repositoryId":"repo_...","branch":"main","title":"Authentication flow"}`.

Question request: `{"content":"Where is the OAuth callback handled?"}`. Proposed response (`200`):

```json
{
  "userMessage": {"id":"msg_...","role":"USER","content":"Where is the OAuth callback handled?"},
  "assistantMessage": {
    "id":"msg_...","role":"ASSISTANT","content":"...",
    "citations":[{"path":"src/main/java/.../AuthController.java","lineStart":20,"lineEnd":48,"commitSha":"abc123..."}]
  },
  "retrieval": {"status":"GROUNDED","sources":2}
}
```

The server derives citation fields from retrieved chunk metadata and rejects model citations that do not map to retrieved chunk IDs. Empty/weak evidence returns an explicit insufficient-context outcome. Summary and interview request bodies may support bounded options (e.g. focus/topic, difficulty, count); exact constraints will be finalized with validation and model budgeting. Responses include source citations with generated claims where feasible.

## Status and error semantics

| Status | Meaning |
|---|---|
| `200` | Successful read or synchronous generation |
| `202` | Indexing job accepted |
| `204` | Successful logout/deletion with no body |
| `400` | Malformed or invalid request |
| `401` | No valid session or GitHub authorization expired |
| `403` / `404` | Forbidden or deliberately concealed resource |
| `409` | Conflicting active job/state |
| `429` | GitHub rate limit reached; includes `Retry-After` where GitHub supplies timing information |
| `502` | Invalid/unavailable upstream response |
| `503` | Required AI/vector/GitHub dependency unavailable |

Implemented repository integration error codes are `GITHUB_AUTHORIZATION_EXPIRED` (`401`), `GITHUB_ACCESS_DENIED` (`403`), `GITHUB_RATE_LIMITED` (`429`, with `Retry-After` when available), and `GITHUB_UNAVAILABLE` (`503`). Planned stable codes include `VALIDATION_ERROR`, `INDEX_NOT_READY`, `AI_UNAVAILABLE`, `VECTOR_STORE_UNAVAILABLE`, `QUOTA_EXCEEDED`, and `RESOURCE_NOT_FOUND`. Never claim successful indexing or generation before the corresponding operation completes.

## Authorization matrix

| Resource/action | Rule |
|---|---|
| `/api/auth/me` | Public session check; returns only the current profile or `{ "authenticated": false, "user": null }` |
| `/api/auth/logout` | Current session plus valid CSRF request header |
| `/api/repositories` | Authenticated user's server-held GitHub authorization; current implementation only returns public repositories |
| Local repository and branch access | Repository connected to current local user plus current GitHub permission when needed |
| Job read/cancel | Job's repository belongs to current user |
| Conversation/message read/write/delete | Conversation owner is current user; repository relation also valid |
| Summary/interview/index request | Repository connected to current user and accessible through GitHub |

The authenticated user is always obtained from server security context, never from a request field or query parameter.

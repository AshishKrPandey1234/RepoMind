# API Contract (Planned)

**Status:** Proposed HTTP contract. Except for the foundation health endpoint below, routes are not implemented. JSON examples are illustrative. All `/api` routes use HTTPS in deployed environments. Authentication is planned to use a backend-managed session cookie; the client does not submit a user ID or GitHub token.

The foundation milestone implements only `GET /api/health`. The other routes below remain planned.

| Method | Path | Purpose | Expected response |
|---|---|---|---|
| GET | `/api/health` | Confirm the API process is responding; does not verify database connectivity | `200` with `{"status":"ok","service":"repomind-api"}` |

## Conventions

- Base path: `/api`; JSON request and response bodies unless stated otherwise.
- IDs are opaque strings in the public API even if stored as numeric database keys.
- Paginate list endpoints with `page` (zero-based) and `size` (bounded, default 20); return `items`, `page`, `size`, `totalItems`, `totalPages`.
- Validate request fields and enforce per-user resource ownership on every call.
- Cookie-authenticated state-changing requests require the configured CSRF token/header.
- Error shape:

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

The response must not include stack traces, tokens, provider secrets, or inaccessible resource details.

## Authentication

| Method | Path | Purpose | Expected response |
|---|---|---|---|
| GET | `/auth/github` | Start OAuth authorization; redirects to GitHub | `302` |
| GET | `/auth/github/callback` | Validate state, exchange code, create session | `302` to client route |
| GET | `/auth/me` | Return current local profile and auth status | `200` |
| POST | `/auth/logout` | Invalidate current session | `204` |

`/auth/me` example: `{"authenticated":true,"user":{"id":"usr_...","githubLogin":"octocat","displayName":"Octocat","avatarUrl":"https://..."}}`. No access token or client secret is returned. OAuth callback errors redirect with a safe, non-sensitive error code; do not put tokens in query parameters.

## GitHub repositories and branches

| Method | Path | Purpose |
|---|---|---|
| GET | `/github/repositories?page=0&size=20` | List repositories visible to the current GitHub authorization |
| GET | `/repositories/{repositoryId}/branches?page=0&size=50` | List branches for a repository the user can access |
| POST | `/repositories` | Connect/store selected GitHub repository metadata |
| GET | `/repositories` | List repositories connected by current user |

Repository DTO: `id`, `githubRepositoryId`, `owner`, `name`, `fullName`, `defaultBranch`, `private`, `htmlUrl`. Pagination should account for GitHub API pagination/rate limits. The backend must verify the GitHub repository ID against the current user's GitHub API response before connecting/indexing it.

The `POST /repositories` request should contain only a `githubRepositoryId` selected from the authenticated user's GitHub repository results. The backend resolves and validates the owner/name and other metadata from GitHub; it must not trust client-submitted ownership or visibility fields.

## Indexing jobs

| Method | Path | Purpose |
|---|---|---|
| POST | `/repositories/{repositoryId}/index-jobs` | Start indexing selected branch |
| GET | `/index-jobs/{jobId}` | Read own job status and safe progress fields |
| GET | `/repositories/{repositoryId}/index-jobs` | List jobs for owned repository |
| POST | `/index-jobs/{jobId}/cancel` | Request cancellation if job is still cancellable |

Each request creates a distinct indexing attempt. A retry after failure creates a new job rather than changing a terminal job back to `QUEUED`.

Start request:

```json
{"branch":"main","forceReindex":false}
```

Accepted response (`202`):

```json
{"id":"job_...","repositoryId":"repo_...","branch":"main","commitSha":"abc123...","status":"QUEUED","createdAt":"2026-10-20T10:00:00Z"}
```

Status response includes `filesSeen`, `filesIndexed`, `chunksWritten`, `startedAt`, `finishedAt`, and a safe `errorCode` when failed. Never return source text or raw exception messages in a status response. Proposed conflict (`409`) when an equivalent active job already exists.

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
| `401` | No valid session |
| `403` / `404` | Forbidden or deliberately concealed resource |
| `409` | Conflicting active job/state |
| `429` | Rate/quota limit exceeded |
| `502` | Invalid/unavailable upstream response |
| `503` | Required AI/vector/GitHub dependency unavailable |

Error `code` values should be stable (`VALIDATION_ERROR`, `GITHUB_RATE_LIMITED`, `GITHUB_UNAVAILABLE`, `INDEX_NOT_READY`, `AI_UNAVAILABLE`, `VECTOR_STORE_UNAVAILABLE`, `QUOTA_EXCEEDED`, `RESOURCE_NOT_FOUND`). Include `Retry-After` where meaningful. Never claim successful indexing or generation before the corresponding operation completes.

## Authorization matrix

| Resource/action | Rule |
|---|---|
| `/auth/me`, logout | Current session |
| GitHub repository list | Current user's server-held GitHub authorization |
| Local repository and branch access | Repository connected to current local user plus current GitHub permission when needed |
| Job read/cancel | Job's repository belongs to current user |
| Conversation/message read/write/delete | Conversation owner is current user; repository relation also valid |
| Summary/interview/index request | Repository connected to current user and accessible through GitHub |

The authenticated user is always obtained from server security context, never from a request field or query parameter.

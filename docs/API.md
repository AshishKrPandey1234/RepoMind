# API Contract

**Status:** `GET /api/health`, `GET /api/auth/me`, `GET /api/auth/csrf`, `POST /api/auth/logout`, and `GET /api/repositories` are implemented. Other routes are planned. All `/api` routes use HTTPS in deployed environments. Authentication uses a backend-managed session cookie; the client does not submit a user ID or GitHub token.

The repository listing is limited to public repositories in this milestone. It uses the current user's GitHub OAuth authorization and is paginated using GitHub API pages.

| Method | Path | Purpose | Expected response |
|---|---|---|---|
| GET | `/api/health` | Confirm the API process is responding; does not verify database connectivity | `200` with `{"status":"ok","service":"repomind-api"}` |
| GET | `/api/auth/me` | Read current GitHub profile/session state | `200` |
| GET | `/api/auth/csrf` | Bootstrap a CSRF token for unsafe browser requests | `200` |
| POST | `/api/auth/logout` | Invalidate the current server session | `204` |
| GET | `/api/repositories?page=0&size=30` | List public repositories accessible to the authenticated GitHub user | `200` |

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
| GET | `/api/repositories/{repositoryId}/branches?page=0&size=50` | List branches for a repository the user can access |
| POST | `/api/repositories` | Connect/store selected GitHub repository metadata |

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

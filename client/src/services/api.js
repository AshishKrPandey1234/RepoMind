const API_ROOT = '/api'

export class ApiError extends Error {
  constructor(status, code, message, retryAfter) {
    super(message || 'The request could not be completed.')
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.retryAfter = retryAfter
  }
}

function fallbackMessage(status, code) {
  if (code === 'GITHUB_AUTHORIZATION_EXPIRED' || status === 401) {
    return 'Your RepoMind session has expired. Sign in with GitHub again.'
  }
  if (code === 'GITHUB_RATE_LIMITED' || status === 429) {
    return 'GitHub or the indexing queue is busy. Wait a little and try again.'
  }
  if (status === 403) {
    return 'The request was blocked by a security check. Reload the page and try again.'
  }
  if (status === 404) {
    return 'RepoMind could not find this repository or API route. Restart the backend if you just updated it, then retry.'
  }
  if (status === 400 || status === 422) {
    return 'RepoMind could not accept this request. Reload the branch list and try again.'
  }
  if (status >= 500) {
    return 'RepoMind could not complete the request. Check the backend logs and try again.'
  }
  return 'The request could not be completed.'
}

export async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_ROOT}${path}`, {
    credentials: 'same-origin',
    ...options,
  })

  if (response.status === 204) return null

  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    const code = typeof body.code === 'string' ? body.code : undefined
    const message = typeof body.message === 'string' && body.message.trim()
      ? body.message
      : fallbackMessage(response.status, code)
    throw new ApiError(
      response.status,
      code,
      message,
      response.headers.get('Retry-After'),
    )
  }
  return body
}

export function getCsrfToken() {
  return apiRequest('/auth/csrf')
}

export function getRepositories(page = 0) {
  return apiRequest(`/repositories?page=${page}&size=30`)
}

export function getBranches(repositoryId, page = 0) {
  return apiRequest(`/repositories/${repositoryId}/branches?page=${page}&size=100`)
}

export function startIndexJob(repositoryId, branch, csrf) {
  return apiRequest('/index-jobs', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ githubRepositoryId: repositoryId, branch }),
  })
}

export function getIndexJob(jobId) {
  return apiRequest(`/index-jobs/${encodeURIComponent(jobId)}`)
}

export function getRepositoryIndexStatus(repositoryId) {
  return apiRequest(`/repositories/${repositoryId}/index-status`)
}

export function searchRepository(repositoryId, question, limit, csrf) {
  return apiRequest(`/repositories/${repositoryId}/search`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ question, limit }),
  })
}

export function askRepository(repositoryId, question, limit, csrf) {
  return apiRequest(`/repositories/${repositoryId}/ask`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      [csrf.headerName]: csrf.token,
    },
    body: JSON.stringify({ question, limit }),
  })
}

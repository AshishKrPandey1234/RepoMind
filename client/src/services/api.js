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

export async function apiRequest(path, options = {}) {
  const response = await fetch(`${API_ROOT}${path}`, {
    credentials: 'same-origin',
    ...options,
  })

  if (response.status === 204) return null

  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new ApiError(
      response.status,
      body.code,
      body.message,
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

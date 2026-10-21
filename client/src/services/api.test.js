import assert from 'node:assert/strict'
import { test } from 'node:test'
import { apiRequest, askRepository, getRepositoryIndexStatus, searchRepository } from './api.js'

test('explains when an API route is missing from a stale backend', async () => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => ({
    status: 404,
    ok: false,
    json: async () => ({ title: 'Not Found', status: 404 }),
    headers: { get: () => null },
  })

  try {
    await assert.rejects(apiRequest('/repositories/42/branches'), error => {
      assert.equal(error.status, 404)
      assert.match(error.message, /Restart the backend/i)
      return true
    })
  } finally {
    globalThis.fetch = originalFetch
  }
})

test('uses safe backend messages and falls back for an empty server error', async t => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })

  globalThis.fetch = async () => ({
    status: 503,
    ok: false,
    json: async () => ({ code: 'GITHUB_UNAVAILABLE', message: 'GitHub is temporarily unavailable.' }),
    headers: { get: () => null },
  })
  await assert.rejects(apiRequest('/index-jobs'), /GitHub is temporarily unavailable/)

  globalThis.fetch = async () => ({
    status: 500,
    ok: false,
    json: async () => { throw new Error('not JSON') },
    headers: { get: () => null },
  })
  await assert.rejects(apiRequest('/index-jobs'), /Check the backend logs/)
})

test('posts a bounded semantic-search request with the CSRF header', async t => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  let captured
  globalThis.fetch = async (url, options) => {
    captured = { url, options }
    return { status: 200, ok: true, json: async () => ({ results: [] }), headers: { get: () => null } }
  }

  const response = await searchRepository(42, 'where is OAuth handled?', 5, {
    headerName: 'X-CSRF-TOKEN', token: 'csrf-token',
  })

  assert.deepEqual(response, { results: [] })
  assert.equal(captured.url, '/api/repositories/42/search')
  assert.equal(captured.options.method, 'POST')
  assert.equal(captured.options.headers['X-CSRF-TOKEN'], 'csrf-token')
  assert.deepEqual(JSON.parse(captured.options.body), { question: 'where is OAuth handled?', limit: 5 })
})

test('posts a repository question with the CSRF header and bounded result count', async t => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  let captured
  globalThis.fetch = async (url, options) => {
    captured = { url, options }
    return { status: 200, ok: true, json: async () => ({ answer: 'grounded' }), headers: { get: () => null } }
  }

  const response = await askRepository(42, 'Where is OAuth configured?', 6, {
    headerName: 'X-CSRF-TOKEN', token: 'csrf-token',
  })

  assert.deepEqual(response, { answer: 'grounded' })
  assert.equal(captured.url, '/api/repositories/42/ask')
  assert.equal(captured.options.headers['X-CSRF-TOKEN'], 'csrf-token')
  assert.deepEqual(JSON.parse(captured.options.body), { question: 'Where is OAuth configured?', limit: 6 })
})

test('loads saved repository index state through the same-origin API', async t => {
  const originalFetch = globalThis.fetch
  t.after(() => { globalThis.fetch = originalFetch })
  let captured
  globalThis.fetch = async (url, options) => {
    captured = { url, options }
    return { status: 200, ok: true, json: async () => ({ latestJob: null, latestSuccessfulSnapshot: null }), headers: { get: () => null } }
  }
  const response = await getRepositoryIndexStatus(42)
  assert.deepEqual(response, { latestJob: null, latestSuccessfulSnapshot: null })
  assert.equal(captured.url, '/api/repositories/42/index-status')
  assert.equal(captured.options.credentials, 'same-origin')
})

import assert from 'node:assert/strict'
import { test } from 'node:test'
import { indexFailureMessage } from './indexingMessages.js'

test('provides actionable messages for common asynchronous indexing failures', () => {
  assert.match(indexFailureMessage('SERVER_RESTARTED'), /Start a new indexing job/)
  assert.match(indexFailureMessage('INDEXING_TIMEOUT'), /smaller repository/)
  assert.match(indexFailureMessage('GITHUB_RATE_LIMITED'), /Wait a little/)
  assert.match(indexFailureMessage('unknown-error'), /Check your GitHub connection/)
})

import assert from 'node:assert/strict'
import { test } from 'node:test'
import { repositoryCardKey } from './repositoryIdentity.js'

test('repository cards receive distinct stable keys so local answer state cannot cross repositories', () => {
  const first = { githubRepositoryId: 42 }
  const second = { githubRepositoryId: 84 }
  assert.equal(repositoryCardKey(first), '42')
  assert.notEqual(repositoryCardKey(first), repositoryCardKey(second))
})

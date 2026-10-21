import assert from 'node:assert/strict'
import { test } from 'node:test'
import React from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import RepositoryAnswerPanel from './RepositoryAnswerPanel.js'

const source = { sourceId: 'S1', filePath: 'src/SecurityConfig.java', lineStart: 10, lineEnd: 18,
  branch: 'main', commitSha: 'commit-12345678', snippet: 'http.oauth2Login()', score: 0.91 }

test('renders generated answer separately from clickable source references', () => {
  const html = renderToStaticMarkup(React.createElement(RepositoryAnswerPanel, {
    response: { repositoryFullName: 'example/repo', branch: 'main', commitSha: 'commit-12345678', answer: 'Login is configured in the security filter chain.', insufficientEvidence: false, sources: [source] },
    repositoryUrl: 'https://github.com/example/repo',
  }))
  assert.match(html, /RepoMind answer/)
  assert.match(html, /Supporting source snippets/)
  assert.match(html, /Indexed snapshot: example\/repo · main · commit-12345678/)
  assert.match(html, /src\/SecurityConfig\.java:10-18/)
  assert.match(html, /blob\/commit-12345678\/src\/SecurityConfig\.java#L10-L18/)
  assert.match(html, /http\.oauth2Login\(\)/)
})

test('renders loading, insufficient evidence, and error states', () => {
  assert.match(renderToStaticMarkup(React.createElement(RepositoryAnswerPanel, { loading: true })), /Generating a grounded answer/)
  assert.match(renderToStaticMarkup(React.createElement(RepositoryAnswerPanel, {
    response: { repositoryFullName: 'example/repo', branch: 'main', commitSha: 'commit-12345678', answer: 'Not enough evidence.', insufficientEvidence: true, sources: [] },
  })), /Not enough evidence[\s\S]*Indexed snapshot: example\/repo · main · commit-12345678/)
  assert.match(renderToStaticMarkup(React.createElement(RepositoryAnswerPanel, { error: 'Local model unavailable.' })), /role="alert"/)
})

import React from 'react'

function sourceHref(repositoryUrl, source) {
  const encodedPath = source.filePath.split('/').map(encodeURIComponent).join('/')
  return `${repositoryUrl}/blob/${encodeURIComponent(source.commitSha)}/${encodedPath}#L${source.lineStart}-L${source.lineEnd}`
}

export default function RepositoryAnswerPanel({ loading, error, response, repositoryUrl }) {
  if (loading) return React.createElement('div', { className: 'answer-message', role: 'status' }, 'Generating a grounded answer…')
  if (error) return React.createElement('div', { className: 'indexer-error', role: 'alert' }, error)
  if (!response) return null
  const snapshotLabel = `${response.repositoryFullName} · ${response.branch} · ${response.commitSha}`
  if (response.insufficientEvidence) {
    return React.createElement('div', { className: 'answer-message', role: 'status' },
      React.createElement('p', null, response.answer),
      React.createElement('small', { className: 'answer-snapshot' }, `Indexed snapshot: ${snapshotLabel}`))
  }

  return React.createElement('section', { className: 'repository-answer', 'aria-live': 'polite' },
    React.createElement('h4', null, 'RepoMind answer'),
    React.createElement('small', { className: 'answer-snapshot' }, `Indexed snapshot: ${snapshotLabel}`),
    React.createElement('p', { className: 'repository-answer-text' }, response.answer),
    React.createElement('h5', null, 'Supporting source snippets'),
    React.createElement('div', { className: 'answer-sources' }, response.sources.map(source =>
      React.createElement('article', { className: 'search-result', key: source.sourceId },
        React.createElement('div', { className: 'search-result-heading' },
          React.createElement('a', { href: sourceHref(repositoryUrl, source), target: '_blank', rel: 'noreferrer' },
            `${source.filePath}:${source.lineStart}-${source.lineEnd}`),
          React.createElement('span', null, `${Math.round(source.score * 100)}% match`)),
        React.createElement('pre', null, source.snippet),
        React.createElement('small', null, `${source.branch} · ${source.commitSha.slice(0, 8)}`)))))
}

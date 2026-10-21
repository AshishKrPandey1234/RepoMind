export function indexFailureMessage(code) {
  switch (code) {
    case 'GITHUB_RATE_LIMITED':
      return 'GitHub rate limit reached. Wait a little, then retry indexing.'
    case 'GITHUB_AUTHORIZATION_EXPIRED':
      return 'GitHub authorization expired. Sign in again, then retry indexing.'
    case 'GITHUB_ACCESS_DENIED':
      return 'GitHub denied access to this public repository. Reconnect GitHub or verify the repository is still public.'
    case 'REPOSITORY_TREE_TOO_LARGE':
      return 'The repository tree is larger than the current indexing limit.'
    case 'REPOSITORY_FILE_LIMIT_EXCEEDED':
      return 'The repository has more eligible files than the current indexing limit.'
    case 'REPOSITORY_CONTENT_LIMIT_EXCEEDED':
      return 'Eligible source content exceeds the configured indexing limit.'
    case 'INDEXING_TIMEOUT':
      return 'Indexing took too long. Try a smaller repository or retry later.'
    case 'INDEX_QUEUE_FULL':
    case 'ACTIVE_INDEX_JOB_LIMIT':
      return 'The indexing queue is busy. Wait for active jobs to finish, then retry.'
    case 'SERVER_RESTARTED':
      return 'The server restarted before indexing finished. Start a new indexing job.'
    default:
      return 'Indexing did not complete. Check your GitHub connection and try again.'
  }
}

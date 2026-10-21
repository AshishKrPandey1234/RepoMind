import { useEffect, useState } from 'react'
import Icon from './Icon.jsx'
import { askRepository, getBranches, getIndexJob, getRepositoryIndexStatus, searchRepository, startIndexJob } from '../services/api.js'
import RepositoryAnswerPanel from './RepositoryAnswerPanel.js'
import { indexFailureMessage } from '../services/indexingMessages.js'

function RepositoryCard({ repository, csrf }) {
  const [showIndexer, setShowIndexer] = useState(false)
  const [branches, setBranches] = useState([])
  const [branchPage, setBranchPage] = useState(0)
  const [hasMoreBranches, setHasMoreBranches] = useState(false)
  const [branch, setBranch] = useState(repository.defaultBranch || '')
  const [branchLoading, setBranchLoading] = useState(false)
  const [branchError, setBranchError] = useState('')
  const [job, setJob] = useState(null)
  const [indexStatus, setIndexStatus] = useState(null)
  const [indexStatusLoading, setIndexStatusLoading] = useState(true)
  const [indexStatusError, setIndexStatusError] = useState('')
  const [jobLoading, setJobLoading] = useState(false)
  const [error, setError] = useState('')
  const [searchOpen, setSearchOpen] = useState(false)
  const [question, setQuestion] = useState('')
  const [searchLoading, setSearchLoading] = useState(false)
  const [searchError, setSearchError] = useState('')
  const [searchResponse, setSearchResponse] = useState(null)
  const [askOpen, setAskOpen] = useState(false)
  const [askLoading, setAskLoading] = useState(false)
  const [askError, setAskError] = useState('')
  const [answerResponse, setAnswerResponse] = useState(null)

  useEffect(() => {
    let active = true
    setIndexStatusLoading(true)
    getRepositoryIndexStatus(repository.githubRepositoryId)
      .then(result => { if (active) setIndexStatus(result) })
      .catch(requestError => { if (active) setIndexStatusError(requestError.message || 'Index status could not be loaded.') })
      .finally(() => { if (active) setIndexStatusLoading(false) })
    return () => { active = false }
  }, [repository.githubRepositoryId])

  async function openIndexer() {
    setShowIndexer(true)
    setBranchLoading(true)
    setBranchError('')
    try {
      const result = await getBranches(repository.githubRepositoryId, 0)
      setBranches(result.items || [])
      setBranchPage(0)
      setHasMoreBranches(result.hasNext)
      const defaultBranch = (result.items || []).find(item => item.defaultBranch)?.name
      setBranch(defaultBranch || result.items?.[0]?.name || '')
    } catch (requestError) {
      setBranchError(requestError.message || 'Branches could not be loaded.')
    } finally {
      setBranchLoading(false)
    }
  }

  async function loadMoreBranches() {
    setBranchLoading(true)
    setBranchError('')
    try {
      const nextPage = branchPage + 1
      const result = await getBranches(repository.githubRepositoryId, nextPage)
      setBranches(current => [...current, ...(result.items || [])])
      setBranchPage(nextPage)
      setHasMoreBranches(result.hasNext)
    } catch (requestError) {
      setBranchError(requestError.message || 'More branches could not be loaded.')
    } finally {
      setBranchLoading(false)
    }
  }

  async function submitIndexJob(event) {
    event.preventDefault()
    if (!csrf || !branch) return
    setJobLoading(true)
    setError('')
    try {
      setJob(await startIndexJob(repository.githubRepositoryId, branch, csrf))
    } catch (requestError) {
      setError(requestError.message || 'The indexing job could not be started.')
    } finally {
      setJobLoading(false)
    }
  }

  async function submitSearch(event) {
    event.preventDefault()
    if (!csrf || !question.trim()) return
    setSearchLoading(true)
    setSearchError('')
    setSearchResponse(null)
    try {
      setSearchResponse(await searchRepository(repository.githubRepositoryId, question.trim(), 8, csrf))
    } catch (requestError) {
      setSearchError(requestError.message || 'Code search could not be completed.')
    } finally {
      setSearchLoading(false)
    }
  }

  async function submitQuestion(event) {
    event.preventDefault()
    if (!csrf || !question.trim()) return
    setAskLoading(true)
    setAskError('')
    setAnswerResponse(null)
    try {
      setAnswerResponse(await askRepository(repository.githubRepositoryId, question.trim(), 6, csrf))
    } catch (requestError) {
      setAskError(requestError.message || 'RepoMind could not answer this question.')
    } finally {
      setAskLoading(false)
    }
  }

  const currentJob = job || indexStatus?.latestJob || null
  const activeJob = currentJob && ['QUEUED', 'RUNNING'].includes(currentJob.status)

  useEffect(() => {
    if (!currentJob || !['QUEUED', 'RUNNING'].includes(currentJob.status)) return undefined
    let active = true
    const timer = window.setTimeout(async () => {
      try {
        const updated = await getIndexJob(currentJob.id)
        if (active) setJob(updated)
        if (active && !['QUEUED', 'RUNNING'].includes(updated.status)) {
          const refreshed = await getRepositoryIndexStatus(repository.githubRepositoryId)
          if (active) setIndexStatus(refreshed)
        }
      } catch (requestError) {
        if (active) {
          setError(requestError.message || 'The indexing status could not be refreshed.')
          setJob(current => current ? { ...current } : current)
        }
      }
    }, 1500)
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [currentJob, repository.githubRepositoryId])

  return (
    <article className="repository-card">
      <div className="repository-card-heading">
        <span className="repository-icon"><Icon name="repo" size={17} /></span>
        <a href={repository.url} target="_blank" rel="noreferrer">
          {repository.fullName}
        </a>
        <span className="repository-visibility">{repository.visibility || 'public'}</span>
      </div>
      <p className="repository-description">{repository.description || 'No description provided.'}</p>
      <div className="repository-meta">
        {repository.primaryLanguage && <span><i className="language-dot" />{repository.primaryLanguage}</span>}
        {repository.defaultBranch && <span><Icon name="branch" size={13} />{repository.defaultBranch}</span>}
        <span>GitHub</span>
      </div>
      <div className="repository-index-action">
        {!showIndexer && !activeJob && !indexStatusLoading && <button className="button button-secondary" type="button" onClick={openIndexer}>
          <Icon name="layers" size={14} /> {currentJob?.status === 'SUCCEEDED' ? 'Re-index repository' : currentJob?.status === 'FAILED' ? 'Retry indexing' : 'Index repository'}
        </button>}
      </div>
      {indexStatusLoading && <div className="indexer-help" role="status">Checking saved index status…</div>}
      {!indexStatusLoading && indexStatusError && <div className="indexer-error" role="alert">{indexStatusError}</div>}
      {!indexStatusLoading && !indexStatusError && !currentJob && <div className="indexer-help" role="status">Not indexed yet. Index a branch before searching or asking questions.</div>}
      <div className="repository-search-action">
        <button className="button button-secondary" type="button" onClick={() => { setSearchOpen(open => !open); setSearchError('') }}>
          {searchOpen ? 'Close code search' : 'Search indexed code'}
        </button>
        <button className="button button-secondary" type="button" onClick={() => { setAskOpen(open => !open); setAskError('') }}>
          {askOpen ? 'Close Q&A' : 'Ask RepoMind'}
        </button>
      </div>
      {askOpen && <form className="search-panel" onSubmit={submitQuestion}>
        <label htmlFor={`ask-${repository.githubRepositoryId}`}>Ask a question about indexed code</label>
        <p className="indexer-help">Answers use this repository’s latest completed index snapshot. If none exists, index the repository first. Each answer shows the branch and commit it used.</p>
        <textarea id={`ask-${repository.githubRepositoryId}`} value={question} maxLength={1000} rows={2}
          placeholder="How does GitHub OAuth login work?" onChange={event => setQuestion(event.target.value)} />
        <button className="button button-primary" type="submit" disabled={!question.trim() || !csrf || askLoading}>
          {askLoading ? <><span className="loading-spinner" /> Generating answer…</> : 'Answer from repository code'}
        </button>
        <RepositoryAnswerPanel loading={askLoading} error={askError} response={answerResponse} repositoryUrl={repository.url} />
      </form>}
      {searchOpen && <form className="search-panel" onSubmit={submitSearch}>
        <label htmlFor={`search-${repository.githubRepositoryId}`}>Ask a question about this repository</label>
        <textarea id={`search-${repository.githubRepositoryId}`} value={question} maxLength={1000} rows={2}
          placeholder="Where is authentication configured?" onChange={event => setQuestion(event.target.value)} />
        <button className="button button-primary" type="submit" disabled={!question.trim() || !csrf || searchLoading}>
          {searchLoading ? <><span className="loading-spinner" /> Searching…</> : 'Find relevant code'}
        </button>
        {searchError && <div className="indexer-error" role="alert">{searchError}</div>}
        {searchResponse && searchResponse.results.length === 0 && <div className="indexer-help" role="status">No relevant code chunks were found in the latest completed index.</div>}
        {searchResponse?.results?.length > 0 && <div className="search-results" aria-live="polite">
          {searchResponse.results.map((result, index) => <article className="search-result" key={`${result.filePath}-${result.lineStart}-${index}`}>
            <div className="search-result-heading"><strong>{result.filePath}:{result.lineStart}-{result.lineEnd}</strong><span>{Math.round(result.score * 100)}% match</span></div>
            <pre>{result.snippet}</pre>
            <small>{result.branch} · {result.commitSha.slice(0, 8)}</small>
          </article>)}
        </div>}
      </form>}
      {showIndexer && !job && <form className="indexer-panel" onSubmit={submitIndexJob}>
        <label htmlFor={`branch-${repository.githubRepositoryId}`}>Branch to index</label>
        {branchLoading ? <span className="indexer-help">Loading branches…</span> : branchError ? <div className="indexer-error" role="alert">{branchError}<button className="button button-secondary" type="button" onClick={openIndexer}>Retry</button></div> : branches.length === 0 ? <span className="indexer-help">No branches were returned for this repository.</span> : <div className="indexer-controls">
          <select id={`branch-${repository.githubRepositoryId}`} value={branch} onChange={event => setBranch(event.target.value)}>
            {branches.map(item => <option key={item.name} value={item.name}>{item.name}{item.defaultBranch ? ' (default)' : ''}</option>)}
          </select>
          <button className="button button-primary" type="submit" disabled={!branch || jobLoading || !csrf}>
            {jobLoading ? 'Starting…' : 'Start indexing'}
          </button>
        </div>}
        {!branchLoading && !branchError && hasMoreBranches && <button className="button button-secondary load-branches-button" type="button" onClick={loadMoreBranches}>Load more branches</button>}
        {error && <div className="indexer-error" role="alert">{error}</div>}
      </form>}
      {currentJob && <div className={`index-job-status status-${currentJob.status.toLowerCase()}`} role="status">
        <div className="index-job-heading"><strong>{currentJob.status === 'SUCCEEDED' ? 'Index complete' : currentJob.status === 'FAILED' ? 'Index failed' : currentJob.status === 'QUEUED' ? 'Queued for indexing' : 'Indexing repository'}</strong><span>{currentJob.status}</span></div>
        <div className="index-job-counts">{currentJob.filesIndexed}/{currentJob.filesSeen} files · {currentJob.chunksWritten} chunks · {currentJob.filesSkipped} skipped</div>
        {currentJob.branch && <small>{currentJob.branch}{currentJob.commitSha ? ` · ${currentJob.commitSha}` : ''}</small>}
        {currentJob.status === 'FAILED' && <div className="indexer-error">{indexFailureMessage(currentJob.errorCode)}</div>}
        {currentJob.status === 'FAILED' && indexStatus?.latestSuccessfulSnapshot && <div className="indexer-help">The previous completed snapshot remains available for Q&amp;A ({indexStatus.latestSuccessfulSnapshot.branch} · {indexStatus.latestSuccessfulSnapshot.commitSha}).</div>}
        {currentJob.status === 'FAILED' && <button className="button button-secondary retry-index-button" type="button" onClick={() => { setJob(null); setShowIndexer(true); setError('') }}>Try another indexing job</button>}
      </div>}
      {error && <div className="indexer-error" role="alert">{error}</div>}
    </article>
  )
}

export default RepositoryCard

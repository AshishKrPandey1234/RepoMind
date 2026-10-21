import { useEffect, useState } from 'react'
import EmptyState from './components/EmptyState.jsx'
import Icon from './components/Icon.jsx'
import OverviewCard from './components/OverviewCard.jsx'
import RepositoryCard from './components/RepositoryCard.jsx'
import Sidebar from './components/Sidebar.jsx'
import { apiRequest, getCsrfToken, getRepositories } from './services/api.js'
import './App.css'

function App() {
  const [user, setUser] = useState(null)
  const [csrf, setCsrf] = useState(null)
  const [authLoading, setAuthLoading] = useState(true)
  const [repositories, setRepositories] = useState({ items: [], page: 0, size: 30, hasNext: false })
  const [repositoryLoading, setRepositoryLoading] = useState(false)
  const [repositoryError, setRepositoryError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let active = true
    const authError = new URLSearchParams(window.location.search).get('authError')
    if (authError === 'oauth_failed') {
      setNotice('GitHub sign-in did not complete. Check the OAuth app settings and try again.')
      window.history.replaceState({}, document.title, window.location.pathname)
    }

    async function initialize() {
      try {
        const [session, csrfToken] = await Promise.all([
          apiRequest('/auth/me'),
          getCsrfToken(),
        ])
        if (!active) return
        setUser(session.authenticated ? session.user : null)
        setCsrf(csrfToken)
        if (session.authenticated) await loadRepositories(0, () => active)
      } catch {
        if (active) setNotice('RepoMind could not reach the API. Check that the backend is running, then reload.')
      } finally {
        if (active) setAuthLoading(false)
      }
    }
    initialize()
    return () => { active = false }
  }, [])

  async function loadRepositories(page, isActive = () => true) {
    setRepositoryLoading(true)
    setRepositoryError('')
    try {
      const result = await getRepositories(page)
      if (isActive()) setRepositories(result)
    } catch (error) {
      if (!isActive()) return
      if (error.status === 401) {
        setUser(null)
        setRepositoryError('Your GitHub session has expired. Sign in again to reload repositories.')
      } else if (error.status === 429) {
        const wait = error.retryAfter ? ` Try again in about ${error.retryAfter} seconds.` : ' Try again later.'
        setRepositoryError(`GitHub rate limit reached.${wait}`)
      } else if (error.code === 'GITHUB_AUTHORIZATION_EXPIRED') {
        setUser(null)
        setRepositoryError('GitHub authorization expired. Sign in again to reconnect.')
      } else {
        setRepositoryError(error.message || 'Repositories could not be loaded.')
      }
    } finally {
      if (isActive()) setRepositoryLoading(false)
    }
  }

  function startSignIn() {
    window.location.assign('/oauth2/authorization/github')
  }

  async function signOut() {
    if (!csrf) {
      setNotice('Security token is not ready. Reload the page and try again.')
      return
    }
    try {
      await apiRequest('/auth/logout', {
        method: 'POST',
        headers: { [csrf.headerName]: csrf.token },
      })
      setUser(null)
      setRepositories({ items: [], page: 0, size: 30, hasNext: false })
      setRepositoryError('')
      setCsrf(await getCsrfToken())
      setNotice('You have signed out of GitHub.')
    } catch (error) {
      if (error.status === 403) {
        setNotice('Your session may have expired. Reload the page to refresh its security token.')
      } else {
        setNotice('Sign-out could not be completed. Please try again.')
      }
    }
  }

  const signedIn = Boolean(user)
  const pageItems = repositories.items || []

  return (
    <div className="app-shell" id="top">
      <Sidebar user={user} />
      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb"><span>Workspace</span><Icon name="chevron" size={14} /><strong>Overview</strong></div>
          <div className="topbar-actions">
            <span className={`preview-pill ${signedIn ? 'connected-pill' : ''}`}><span className="status-dot" />{authLoading ? 'Checking session' : signedIn ? 'GitHub connected' : 'Not signed in'}</span>
            {signedIn ? (
              <>
                {user.avatarUrl ? <img className="user-avatar" src={user.avatarUrl} alt={`${user.githubLogin} avatar`} /> : <div className="user-avatar" aria-label={user.githubLogin}>{user.githubLogin?.[0]?.toUpperCase() || '?'}</div>}
                <button className="button button-secondary logout-button" type="button" onClick={signOut}>Sign out</button>
              </>
            ) : <div className="user-avatar" aria-label="Not signed in">?</div>}
          </div>
        </header>

        <div className="page-content">
          <section className="welcome-panel" aria-labelledby="welcome-title">
            <div className="welcome-copy">
              <div className="eyebrow"><span className="eyebrow-line" />YOUR REPOSITORY WORKSPACE</div>
              <h1 id="welcome-title">Your codebase,<br /><span>clearer.</span></h1>
              <p>{signedIn ? `Welcome, ${user.displayName || user.githubLogin}. Your public repositories are ready to explore.` : 'Connect a GitHub account to bring your public repositories into one thoughtful workspace.'}</p>
              {!signedIn && <>
                <button className="button button-primary" type="button" onClick={startSignIn} disabled={authLoading}>
                  <Icon name="github" size={19} /> Sign in with GitHub <Icon name="arrowUpRight" size={15} />
                </button>
                <p className="button-note">GitHub authorization stays on the secure RepoMind server session.</p>
              </>}
            </div>
            <div className="welcome-art" aria-hidden="true">
              <div className="orbit orbit-one" /><div className="orbit orbit-two" /><div className="art-glow" />
              <div className="code-window">
                <div className="window-header"><span /><span /><span /><i>repository context</i></div>
                <div className="code-lines">
                  <div><b>01</b><span className="code-purple">const</span> <span className="code-blue">repo</span> = <span className="code-green">"your-project"</span></div>
                  <div><b>02</b><span className="code-purple">await</span> <span className="code-blue">understand</span>(repo)</div>
                  <div><b>03</b><span className="code-muted">// your code, in context</span></div>
                  <div><b>04</b><span className="code-cursor" /></div>
                </div>
                <div className="context-chip"><span className="context-spark">✦</span><span>Grounded in your code</span><span className="chip-lock"><Icon name="lock" size={13} /></span></div>
              </div>
              <div className="art-caption"><span className="caption-mark">✳</span><span>Understand the whole picture</span></div>
            </div>
          </section>

          {notice && <div className="notice" role="status"><Icon name="info" size={17} />{notice}<button type="button" aria-label="Dismiss notice" onClick={() => setNotice('')}><Icon name="close" size={15} /></button></div>}

          <section className="overview-section" aria-labelledby="overview-title">
            <div className="section-heading">
              <div><span className="section-kicker">AT A GLANCE</span><h2 id="overview-title">Your overview</h2></div>
              <span className="unavailable-label"><span className={`status-dot ${signedIn ? '' : 'muted'}`} />{authLoading ? 'Loading session' : signedIn ? 'GitHub account connected' : 'Waiting for connection'}</span>
            </div>
            <div className="overview-grid">
              <OverviewCard icon="repo" label="Repositories loaded" value={signedIn && !repositoryLoading ? String(pageItems.length) : '—'} description={signedIn ? 'Repositories on the current page' : 'Sign in to load your repositories'} accent="violet" />
              <OverviewCard icon="layers" label="Indexed projects" value="—" description="Indexing is planned for a later milestone" accent="blue" />
              <OverviewCard icon="message" label="Questions asked" value="—" description="Repository chat is planned for a later milestone" accent="green" />
            </div>
          </section>

          <section className="repositories-section" id="repositories" aria-labelledby="repositories-title">
            <div className="section-heading repository-heading">
              <div><span className="section-kicker">YOUR CODEBASES</span><h2 id="repositories-title">Repositories</h2></div>
              {!signedIn && <button className="button button-secondary" type="button" onClick={startSignIn} disabled={authLoading}><Icon name="plus" size={16} /> Connect GitHub</button>}
            </div>
            {repositoryLoading && <div className="repository-message" role="status"><span className="loading-spinner" />Loading repositories from GitHub…</div>}
            {!repositoryLoading && repositoryError && <div className="repository-error" role="alert"><Icon name="info" size={17} /><span>{repositoryError}</span><button className="button button-secondary" type="button" onClick={() => signedIn ? loadRepositories(repositories.page) : startSignIn()}>{signedIn ? 'Retry' : 'Sign in again'}</button></div>}
            {!authLoading && !signedIn && !repositoryError && <EmptyState onSignIn={startSignIn} />}
            {!repositoryLoading && signedIn && !repositoryError && pageItems.length === 0 && <EmptyState authenticated />}
            {!repositoryLoading && signedIn && !repositoryError && pageItems.length > 0 && <>
              <div className="repository-grid">{pageItems.map(repository => <RepositoryCard key={repository.githubRepositoryId} repository={repository} />)}</div>
              <div className="repository-pagination">
                <span>Page {repositories.page + 1}</span>
                <div>
                  <button className="button button-secondary" type="button" disabled={repositories.page === 0} onClick={() => loadRepositories(repositories.page - 1)}>Previous</button>
                  <button className="button button-secondary" type="button" disabled={!repositories.hasNext} onClick={() => loadRepositories(repositories.page + 1)}>Next</button>
                </div>
              </div>
            </>}
          </section>

          <footer className="page-footer"><span>RepoMind <span className="footer-dot">·</span> Built to make code feel familiar.</span><span className="footer-status"><span className={`status-dot ${signedIn ? '' : 'muted'}`} />{signedIn ? `Connected as ${user.githubLogin}` : 'Not connected'}</span></footer>
        </div>
      </main>
    </div>
  )
}

export default App

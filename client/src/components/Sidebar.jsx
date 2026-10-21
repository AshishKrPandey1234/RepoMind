import Icon from './Icon.jsx'

function Sidebar({ user }) {
  const connected = Boolean(user)
  return (
    <aside className="sidebar">
      <a className="brand" href="#top" aria-label="RepoMind home">
        <span className="brand-mark"><Icon name="spark" size={20} /></span>
        <span className="brand-name">repo<span>mind</span><small>REPOSITORY ASSISTANT</small></span>
      </a>
      <div className="sidebar-label">WORKSPACE</div>
      <nav className="sidebar-nav" aria-label="Main navigation">
        <a className="nav-item active" href="#top"><Icon name="grid" size={17} /><span>Overview</span></a>
        <a className="nav-item" href="#repositories"><Icon name="repo" size={17} /><span>Repositories</span><span className="nav-tag">—</span></a>
        <button className="nav-item nav-disabled" type="button" disabled aria-label="Conversations, available in a future milestone"><Icon name="message" size={17} /><span>Conversations</span><span className="soon-tag">SOON</span></button>
      </nav>
      <div className="sidebar-divider" />
      <div className="sidebar-label">YOUR CONNECTION</div>
      <div className="connection-card"><span className="connection-icon"><Icon name="github" size={17} /></span><span><strong>GitHub</strong><small>{connected ? `@${user.githubLogin}` : 'Not connected'}</small></span><span className={`connection-dot ${connected ? 'online' : ''}`} /></div>
      <div className="sidebar-bottom">
        <div className="sidebar-note"><span className="note-icon"><Icon name="info" size={15} /></span><span><strong>{connected ? 'Public repository access' : 'Workspace ready'}</strong><small>{connected ? 'Repository browsing is active; indexing is not enabled.' : 'Sign in to browse your public repositories.'}</small></span></div>
        <div className="sidebar-version"><span>RepoMind</span><span>Foundation · 01</span></div>
      </div>
    </aside>
  )
}

export default Sidebar

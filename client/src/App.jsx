import { useState } from 'react'
import EmptyState from './components/EmptyState.jsx'
import Icon from './components/Icon.jsx'
import OverviewCard from './components/OverviewCard.jsx'
import Sidebar from './components/Sidebar.jsx'
import './App.css'

function App() {
  const [notice, setNotice] = useState('')

  function explainSignIn() {
    setNotice('GitHub sign-in is not connected yet. This milestone is the local interface foundation.')
  }

  return (
    <div className="app-shell" id="top">
      <Sidebar />
      <main className="main-content">
        <header className="topbar">
          <div className="breadcrumb"><span>Workspace</span><Icon name="chevron" size={14} /><strong>Overview</strong></div>
          <div className="topbar-actions">
            <span className="preview-pill"><span className="status-dot" />Foundation preview</span>
            <div className="user-avatar" aria-label="Not signed in">?</div>
          </div>
        </header>

        <div className="page-content">
          <section className="welcome-panel" aria-labelledby="welcome-title">
            <div className="welcome-copy">
              <div className="eyebrow"><span className="eyebrow-line" />YOUR REPOSITORY WORKSPACE</div>
              <h1 id="welcome-title">Your codebase,<br /><span>clearer.</span></h1>
              <p>Connect a GitHub account to bring your repositories into one thoughtful workspace. Ask better questions about the code you work with.</p>
              <button className="button button-primary" type="button" onClick={explainSignIn}>
                <Icon name="github" size={19} /> Sign in with GitHub <Icon name="arrowUpRight" size={15} />
              </button>
              <p className="button-note">Sign-in is a preview only and is not connected yet.</p>
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
              <span className="unavailable-label"><span className="status-dot muted" />Waiting for connection</span>
            </div>
            <div className="overview-grid">
              <OverviewCard icon="repo" label="Repositories" value="—" description="Connect GitHub to load your repositories" accent="violet" />
              <OverviewCard icon="layers" label="Indexed projects" value="—" description="Indexing will be available in a later milestone" accent="blue" />
              <OverviewCard icon="message" label="Questions asked" value="—" description="Your repository conversations will appear here" accent="green" />
            </div>
          </section>

          <section className="repositories-section" id="repositories" aria-labelledby="repositories-title">
            <div className="section-heading repository-heading">
              <div><span className="section-kicker">YOUR CODEBASES</span><h2 id="repositories-title">Repositories</h2></div>
              <button className="button button-secondary" type="button" onClick={explainSignIn}><Icon name="plus" size={16} /> Connect repository</button>
            </div>
            <EmptyState onSignIn={explainSignIn} />
          </section>

          <footer className="page-footer"><span>RepoMind <span className="footer-dot">·</span> Built to make code feel familiar.</span><span className="footer-status"><span className="status-dot muted" />Not connected</span></footer>
        </div>
      </main>
    </div>
  )
}

export default App

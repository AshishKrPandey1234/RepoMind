import Icon from './Icon.jsx'

function EmptyState({ onSignIn, authenticated = false }) {
  const title = authenticated ? 'No public repositories found' : 'No repositories to show'
  const description = authenticated
    ? 'This GitHub account has no public repositories available to this authorization.'
    : 'Sign in with GitHub to see the public repositories available to your account. No sample projects or placeholder data are shown.'

  return (
    <div className="empty-state">
      <div className="empty-illustration" aria-hidden="true"><div className="empty-orbit" /><div className="empty-book"><Icon name="repo" size={30} /></div><span className="empty-spark spark-a">✦</span><span className="empty-spark spark-b">✳</span></div>
      <div className="empty-copy">
        <span className="empty-eyebrow">A FRESH START</span>
        <h3>{title}</h3>
        <p>{description}</p>
        {!authenticated && <button className="button button-secondary empty-button" type="button" onClick={onSignIn}><Icon name="github" size={17} /> Sign in with GitHub <Icon name="arrowUpRight" size={14} /></button>}
      </div>
      <div className="empty-state-foot"><span><Icon name="lock" size={13} /> Authorization stays on the server</span><span>{authenticated ? 'No repositories returned' : 'GitHub connection not active'}</span></div>
    </div>
  )
}

export default EmptyState

import Icon from './Icon.jsx'

function EmptyState({ onSignIn }) {
  return (
    <div className="empty-state">
      <div className="empty-illustration" aria-hidden="true"><div className="empty-orbit" /><div className="empty-book"><Icon name="repo" size={30} /></div><span className="empty-spark spark-a">✦</span><span className="empty-spark spark-b">✳</span></div>
      <div className="empty-copy">
        <span className="empty-eyebrow">A FRESH START</span>
        <h3>No repositories to show</h3>
        <p>Sign in with GitHub to see the repositories you can access. We won't show sample projects or placeholder data here.</p>
        <button className="button button-secondary empty-button" type="button" onClick={onSignIn}><Icon name="github" size={17} /> Sign in with GitHub <Icon name="arrowUpRight" size={14} /></button>
      </div>
      <div className="empty-state-foot"><span><Icon name="lock" size={13} /> Your repositories stay yours</span><span>GitHub connection not active</span></div>
    </div>
  )
}

export default EmptyState

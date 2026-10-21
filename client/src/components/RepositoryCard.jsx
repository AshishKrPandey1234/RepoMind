import Icon from './Icon.jsx'

function RepositoryCard({ repository }) {
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
    </article>
  )
}

export default RepositoryCard

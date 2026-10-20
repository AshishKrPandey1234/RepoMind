import Icon from './Icon.jsx'

function OverviewCard({ icon, label, value, description, accent }) {
  return (
    <article className="overview-card">
      <div className={`metric-icon ${accent}`}><Icon name={icon} size={18} /></div>
      <div className="metric-topline"><span>{label}</span><span className="metric-placeholder">NOT AVAILABLE</span></div>
      <div className="metric-value">{value}</div>
      <p>{description}</p>
    </article>
  )
}

export default OverviewCard

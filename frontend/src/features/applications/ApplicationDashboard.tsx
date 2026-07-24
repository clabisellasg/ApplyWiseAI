import { calculateApplicationMetrics } from './applicationMetrics'
import {
  APPLICATION_STATUSES,
  APPLICATION_STATUS_LABELS,
  type JobApplication,
} from './applicationTypes'

type ApplicationDashboardProps = {
  applications: JobApplication[]
}

export function ApplicationDashboard({ applications }: ApplicationDashboardProps) {
  const metrics = calculateApplicationMetrics(applications)

  return (
    <section className="application-dashboard" aria-labelledby="application-summary-heading">
      <div className="application-dashboard__heading">
        <div>
          <p className="eyebrow">At a glance</p>
          <h3 id="application-summary-heading">Application summary</h3>
        </div>
        <div className="metric-card metric-card--primary">
          <strong>{metrics.total}</strong>
          <span>Total</span>
        </div>
      </div>

      <div className="metric-grid" aria-label="Application status counts">
        {APPLICATION_STATUSES.map((status) => (
          <div className="metric-card" key={status}>
            <strong>{metrics.byStatus[status]}</strong>
            <span>{APPLICATION_STATUS_LABELS[status]}</span>
          </div>
        ))}
      </div>

      <div className="action-metrics" aria-label="Next action counts">
        <div>
          <strong>{metrics.overdue}</strong>
          <span>Overdue</span>
        </div>
        <div>
          <strong>{metrics.dueToday}</strong>
          <span>Due today</span>
        </div>
        <div>
          <strong>{metrics.upcoming}</strong>
          <span>Upcoming</span>
        </div>
      </div>
    </section>
  )
}

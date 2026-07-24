import { useState } from 'react'
import { FeedbackBanner } from '../../components/FeedbackBanner'
import {
  APPLICATION_STATUSES,
  APPLICATION_STATUS_LABELS,
  type ApplicationStatus,
  type ApplicationStatusHistory,
  type JobApplication,
} from './applicationTypes'

type ApplicationDetailsProps = {
  actionError: string | null
  application: JobApplication
  deleting: boolean
  history: ApplicationStatusHistory[]
  historyError: string | null
  historyLoading: boolean
  statusUpdating: boolean
  onBack: () => void
  onDelete: () => void
  onEdit: () => void
  onRetryHistory: () => void
  onStatusChange: (status: ApplicationStatus) => Promise<void>
}

function date(value: string | null): string {
  if (!value) {
    return 'Not set'
  }

  const [year, month, day] = value.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'long' })
    .format(new Date(year, month - 1, day))
}

function dateTime(value: string | null): string {
  return value
    ? new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(new Date(value))
    : 'Not set'
}

export function ApplicationDetails({
  actionError,
  application,
  deleting,
  history,
  historyError,
  historyLoading,
  statusUpdating,
  onBack,
  onDelete,
  onEdit,
  onRetryHistory,
  onStatusChange,
}: ApplicationDetailsProps) {
  const [nextStatus, setNextStatus] = useState<ApplicationStatus>(application.status)

  async function submitStatus() {
    await onStatusChange(nextStatus)
  }

  return (
    <section className="application-detail" aria-labelledby="application-detail-heading">
      <header className="application-detail__header">
        <div>
          <button className="text-button application-detail__back" type="button" onClick={onBack}>
            ← Back to tracker
          </button>
          <p className="eyebrow">Tracked application</p>
          <h2 id="application-detail-heading">{application.job.title}</h2>
          <p>{application.job.company}</p>
        </div>
        <span className={`application-status application-status--${application.status.toLowerCase()}`}>
          {APPLICATION_STATUS_LABELS[application.status]}
        </span>
      </header>

      {actionError && <FeedbackBanner tone="error">{actionError}</FeedbackBanner>}

      <div className="application-detail__actions">
        <div className="status-update">
          <label htmlFor="application-detail-status">Change status</label>
          <div>
            <select
              id="application-detail-status"
              value={nextStatus}
              onChange={(event) => setNextStatus(event.target.value as ApplicationStatus)}
              disabled={statusUpdating}
            >
              {APPLICATION_STATUSES.map((status) => (
                <option key={status} value={status}>{APPLICATION_STATUS_LABELS[status]}</option>
              ))}
            </select>
            <button
              className="button button--primary"
              type="button"
              onClick={() => void submitStatus()}
              disabled={statusUpdating || nextStatus === application.status}
            >
              {statusUpdating ? 'Updating…' : 'Update status'}
            </button>
          </div>
        </div>
        <div className="form-actions">
          <button className="button button--secondary" type="button" onClick={onEdit}>
            Edit details
          </button>
          <button
            className="button button--danger"
            type="button"
            onClick={onDelete}
            disabled={deleting}
          >
            {deleting ? 'Deleting…' : 'Delete application'}
          </button>
        </div>
      </div>

      <div className="application-detail__grid">
        <section className="detail-panel" aria-labelledby="application-record-heading">
          <h3 id="application-record-heading">Application record</h3>
          <dl className="detail-list">
            <div><dt>Job</dt><dd>{application.job.title} at {application.job.company}</dd></div>
            <div>
              <dt>Resume</dt>
              <dd>
                {application.resume
                  ? `${application.resume.name}${application.resume.targetRole ? ` — ${application.resume.targetRole}` : ''}`
                  : 'None'}
              </dd>
            </div>
            <div>
              <dt>Analysis</dt>
              <dd>
                {application.analysis
                  ? `${application.analysis.score}% · ${application.analysis.provider} · ${application.analysis.model}`
                  : 'None'}
              </dd>
            </div>
            <div><dt>Applied date</dt><dd>{date(application.appliedAt)}</dd></div>
            <div><dt>Next action</dt><dd>{application.nextAction ?? 'None'}</dd></div>
            <div><dt>Next-action date</dt><dd>{dateTime(application.nextActionAt)}</dd></div>
            <div><dt>Created</dt><dd>{dateTime(application.createdAt)}</dd></div>
            <div><dt>Updated</dt><dd>{dateTime(application.updatedAt)}</dd></div>
          </dl>
          <div className="application-notes">
            <h3>Notes</h3>
            <p>{application.notes ?? 'No notes added.'}</p>
          </div>
        </section>

        <section className="detail-panel" aria-labelledby="status-history-heading">
          <div className="list-heading">
            <h3 id="status-history-heading">Status history</h3>
            <span className="count-badge">{history.length}</span>
          </div>
          {historyLoading && <FeedbackBanner tone="info">Loading status history…</FeedbackBanner>}
          {historyError && (
            <FeedbackBanner tone="error">
              <span>{historyError}</span>
              <button className="banner-button" type="button" onClick={onRetryHistory}>
                Try again
              </button>
            </FeedbackBanner>
          )}
          {!historyLoading && !historyError && history.length === 0 && (
            <div className="empty-state empty-state--compact">No status history is available.</div>
          )}
          {history.length > 0 && (
            <ol className="status-history">
              {history.map((entry) => (
                <li key={entry.id}>
                  <span className="status-history__marker" aria-hidden="true" />
                  <div>
                    <strong>{APPLICATION_STATUS_LABELS[entry.newStatus]}</strong>
                    <span>
                      {entry.previousStatus
                        ? `From ${APPLICATION_STATUS_LABELS[entry.previousStatus]}`
                        : 'Initial status'}
                    </span>
                    <time dateTime={entry.changedAt}>{dateTime(entry.changedAt)}</time>
                  </div>
                </li>
              ))}
            </ol>
          )}
        </section>
      </div>
    </section>
  )
}

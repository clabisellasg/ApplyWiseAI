import {
  APPLICATION_STATUS_LABELS,
  type JobApplication,
} from './applicationTypes'

type ApplicationCardProps = {
  application: JobApplication
  isOpening: boolean
  onOpen: (id: number) => void
}

function formatDate(value: string | null): string {
  if (!value) {
    return 'Not set'
  }

  const [year, month, day] = value.split('-').map(Number)
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' })
    .format(new Date(year, month - 1, day))
}

function formatDateTime(value: string | null): string {
  return value
    ? new Intl.DateTimeFormat(undefined, {
        dateStyle: 'medium',
        timeStyle: 'short',
      }).format(new Date(value))
    : 'Not set'
}

export function ApplicationCard({ application, isOpening, onOpen }: ApplicationCardProps) {
  return (
    <article className="application-card">
      <header className="application-card__header">
        <div>
          <span className={`application-status application-status--${application.status.toLowerCase()}`}>
            {APPLICATION_STATUS_LABELS[application.status]}
          </span>
          <h3>{application.job.title}</h3>
          <p>{application.job.company}</p>
        </div>
        <button
          className="text-button"
          type="button"
          onClick={() => onOpen(application.id)}
          aria-label={`View ${application.job.title} application`}
          disabled={isOpening}
        >
          {isOpening ? 'Opening…' : 'View details'}
        </button>
      </header>

      <dl className="application-card__details">
        <div>
          <dt>Resume</dt>
          <dd>{application.resume?.name ?? 'None'}</dd>
        </div>
        <div>
          <dt>Analysis</dt>
          <dd>{application.analysis ? `${application.analysis.score}% match` : 'None'}</dd>
        </div>
        <div>
          <dt>Applied</dt>
          <dd>{formatDate(application.appliedAt)}</dd>
        </div>
        <div>
          <dt>Next action</dt>
          <dd>{application.nextAction ?? 'None'}</dd>
        </div>
        <div>
          <dt>Next-action date</dt>
          <dd>{formatDateTime(application.nextActionAt)}</dd>
        </div>
        <div>
          <dt>Last updated</dt>
          <dd>{formatDateTime(application.updatedAt)}</dd>
        </div>
      </dl>
    </article>
  )
}

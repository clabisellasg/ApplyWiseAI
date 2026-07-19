import type { JobPosting } from './jobTypes'

type JobCardProps = {
  isDeleting: boolean
  isEditing: boolean
  job: JobPosting
  onDelete: (job: JobPosting) => void
  onEdit: (job: JobPosting) => void
}

function formattedDate(value: string): string {
  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return 'Unknown date'
  }

  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

function safeSourceUrl(value: string | null): string | null {
  if (!value) {
    return null
  }

  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.toString() : null
  } catch {
    return null
  }
}

export function JobCard({ isDeleting, isEditing, job, onDelete, onEdit }: JobCardProps) {
  const sourceUrl = safeSourceUrl(job.sourceUrl)

  return (
    <article className={`record-card${isEditing ? ' record-card--editing' : ''}`}>
      <header className="record-card__header">
        <div>
          <p className="record-card__kicker">{job.company}</p>
          <h3>{job.title}</h3>
        </div>
        <p className="record-card__date">Updated {formattedDate(job.updatedAt)}</p>
      </header>

      <p className="record-card__content">{job.description}</p>

      {sourceUrl && (
        <a className="source-link" href={sourceUrl} target="_blank" rel="noreferrer">
          View source posting
        </a>
      )}
      {job.sourceUrl && !sourceUrl && (
        <p className="source-text">Source: {job.sourceUrl}</p>
      )}

      <footer className="record-card__footer">
        <span>Saved {formattedDate(job.createdAt)}</span>
        <div className="card-actions">
          <button className="text-button" type="button" onClick={() => onEdit(job)}>
            Edit
          </button>
          <button
            className="text-button text-button--danger"
            type="button"
            disabled={isDeleting}
            onClick={() => onDelete(job)}
          >
            {isDeleting ? 'Deleting…' : 'Delete'}
          </button>
        </div>
      </footer>
    </article>
  )
}

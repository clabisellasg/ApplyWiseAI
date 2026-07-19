import type { Resume } from './resumeTypes'

type ResumeCardProps = {
  isDeleting: boolean
  isEditing: boolean
  onDelete: (resume: Resume) => void
  onEdit: (resume: Resume) => void
  resume: Resume
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

export function ResumeCard({ isDeleting, isEditing, onDelete, onEdit, resume }: ResumeCardProps) {
  return (
    <article className={`record-card${isEditing ? ' record-card--editing' : ''}`}>
      <header className="record-card__header">
        <div>
          <p className="record-card__kicker">{resume.targetRole || 'General resume'}</p>
          <h3>{resume.name}</h3>
        </div>
        <p className="record-card__date">Updated {formattedDate(resume.updatedAt)}</p>
      </header>

      <p className="record-card__content record-card__content--clamped">{resume.content}</p>

      <footer className="record-card__footer">
        <span>Saved {formattedDate(resume.createdAt)}</span>
        <div className="card-actions">
          <button className="text-button" type="button" onClick={() => onEdit(resume)}>
            Edit
          </button>
          <button
            className="text-button text-button--danger"
            type="button"
            disabled={isDeleting}
            onClick={() => onDelete(resume)}
          >
            {isDeleting ? 'Deleting…' : 'Delete'}
          </button>
        </div>
      </footer>
    </article>
  )
}

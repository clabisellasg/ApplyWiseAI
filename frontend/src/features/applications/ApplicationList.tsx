import { ApplicationCard } from './ApplicationCard'
import type { JobApplication } from './applicationTypes'

type ApplicationListProps = {
  applications: JobApplication[]
  hasAnyApplications: boolean
  openingId: number | null
  onCreate: () => void
  onOpen: (id: number) => void
}

export function ApplicationList({
  applications,
  hasAnyApplications,
  openingId,
  onCreate,
  onOpen,
}: ApplicationListProps) {
  if (applications.length === 0) {
    return (
      <div className="empty-state">
        <span className="empty-state__title">
          {hasAnyApplications ? 'No applications match this status.' : 'No tracked applications yet.'}
        </span>
        <p>
          {hasAnyApplications
            ? 'Choose another status to see the rest of your tracker.'
            : 'Connect a saved job to begin tracking its progress and follow-up actions.'}
        </p>
        {!hasAnyApplications && (
          <button className="button button--primary empty-state__action" type="button" onClick={onCreate}>
            Track your first application
          </button>
        )}
      </div>
    )
  }

  return (
    <div className="application-list">
      {applications.map((application) => (
        <ApplicationCard
          application={application}
          isOpening={openingId === application.id}
          key={application.id}
          onOpen={onOpen}
        />
      ))}
    </div>
  )
}

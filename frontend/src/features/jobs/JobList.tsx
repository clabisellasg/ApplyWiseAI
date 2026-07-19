import { JobCard } from './JobCard'
import type { JobPosting } from './jobTypes'

type JobListProps = {
  deletingId: number | null
  editingId: number | null
  jobs: JobPosting[]
  onDelete: (job: JobPosting) => void
  onEdit: (job: JobPosting) => void
}

export function JobList({ deletingId, editingId, jobs, onDelete, onEdit }: JobListProps) {
  if (jobs.length === 0) {
    return (
      <div className="empty-state">
        <p className="empty-state__title">No job postings yet</p>
        <p>Add your first role using the form. It will stay here after you refresh.</p>
      </div>
    )
  }

  return (
    <div className="record-list">
      {jobs.map((job) => (
        <JobCard
          key={job.id}
          job={job}
          isDeleting={deletingId === job.id}
          isEditing={editingId === job.id}
          onDelete={onDelete}
          onEdit={onEdit}
        />
      ))}
    </div>
  )
}

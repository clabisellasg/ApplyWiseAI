import { ResumeCard } from './ResumeCard'
import type { Resume } from './resumeTypes'

type ResumeListProps = {
  deletingId: number | null
  editingId: number | null
  onDelete: (resume: Resume) => void
  onEdit: (resume: Resume) => void
  resumes: Resume[]
}

export function ResumeList({ deletingId, editingId, onDelete, onEdit, resumes }: ResumeListProps) {
  if (resumes.length === 0) {
    return (
      <div className="empty-state">
        <p className="empty-state__title">No resumes yet</p>
        <p>Add a text resume using the form. It will stay here after you refresh.</p>
      </div>
    )
  }

  return (
    <div className="record-list">
      {resumes.map((resume) => (
        <ResumeCard
          key={resume.id}
          resume={resume}
          isDeleting={deletingId === resume.id}
          isEditing={editingId === resume.id}
          onDelete={onDelete}
          onEdit={onEdit}
        />
      ))}
    </div>
  )
}

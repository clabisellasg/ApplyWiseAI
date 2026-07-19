import { useEffect, useRef, useState } from 'react'
import { getErrorMessage } from '../../api/client'
import { FeedbackBanner } from '../../components/FeedbackBanner'
import { createResume, deleteResume, getResumes, updateResume } from './resumeApi'
import { ResumeForm } from './ResumeForm'
import { ResumeList } from './ResumeList'
import type { Resume, ResumeInput } from './resumeTypes'

export function ResumesSection() {
  const [resumes, setResumes] = useState<Resume[]>([])
  const [editingResume, setEditingResume] = useState<Resume | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const saveLock = useRef(false)

  async function refreshResumes() {
    setIsLoading(true)
    setLoadError(null)

    try {
      setResumes(await getResumes())
    } catch (error) {
      setLoadError(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()

    async function loadInitialResumes() {
      try {
        setResumes(await getResumes(controller.signal))
      } catch (error) {
        if (!controller.signal.aborted) {
          setLoadError(getErrorMessage(error))
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadInitialResumes()
    return () => controller.abort()
  }, [])

  async function handleSubmit(input: ResumeInput) {
    if (saveLock.current) {
      return
    }

    saveLock.current = true
    setIsSaving(true)
    setFormError(null)
    setActionError(null)
    setSuccessMessage(null)

    try {
      if (editingResume) {
        await updateResume(editingResume.id, input)
        setSuccessMessage('Resume updated.')
        setEditingResume(null)
      } else {
        await createResume(input)
        setSuccessMessage('Resume saved.')
      }

      await refreshResumes()
    } catch (error) {
      setFormError(getErrorMessage(error))
      throw error
    } finally {
      saveLock.current = false
      setIsSaving(false)
    }
  }

  function handleEdit(resume: Resume) {
    setEditingResume(resume)
    setFormError(null)
    setActionError(null)
    setSuccessMessage(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function handleCancelEdit() {
    setEditingResume(null)
    setFormError(null)
  }

  async function handleDelete(resume: Resume) {
    if (!window.confirm(`Delete “${resume.name}”?`)) {
      return
    }

    setDeletingId(resume.id)
    setActionError(null)
    setSuccessMessage(null)

    try {
      await deleteResume(resume.id)

      if (editingResume?.id === resume.id) {
        setEditingResume(null)
        setFormError(null)
      }

      setSuccessMessage('Resume deleted.')
      await refreshResumes()
    } catch (error) {
      setActionError(getErrorMessage(error))
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <section className="feature-layout" aria-labelledby="resumes-heading">
      <aside className="form-panel">
        <ResumeForm
          key={editingResume?.id ?? 'new-resume'}
          error={formError}
          isSubmitting={isSaving}
          resume={editingResume}
          onCancel={handleCancelEdit}
          onSubmit={handleSubmit}
        />
      </aside>

      <div className="list-panel">
        <div className="list-heading">
          <div>
            <p className="eyebrow">Saved records</p>
            <h2 id="resumes-heading">Resumes</h2>
          </div>
          <span className="count-badge">{resumes.length}</span>
        </div>

        {successMessage && <FeedbackBanner tone="success">{successMessage}</FeedbackBanner>}
        {actionError && <FeedbackBanner tone="error">{actionError}</FeedbackBanner>}
        {loadError && (
          <FeedbackBanner tone="error">
            <span>{loadError}</span>
            <button className="banner-button" type="button" onClick={() => void refreshResumes()}>
              Try again
            </button>
          </FeedbackBanner>
        )}
        {isLoading && resumes.length === 0 && (
          <FeedbackBanner tone="info">Loading resumes…</FeedbackBanner>
        )}
        {isLoading && resumes.length > 0 && <p className="refresh-note">Refreshing…</p>}

        {(!loadError || resumes.length > 0) && (
          <ResumeList
            deletingId={deletingId}
            editingId={editingResume?.id ?? null}
            resumes={resumes}
            onDelete={(resume) => void handleDelete(resume)}
            onEdit={handleEdit}
          />
        )}
      </div>
    </section>
  )
}

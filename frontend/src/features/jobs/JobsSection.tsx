import { useEffect, useRef, useState } from 'react'
import { getErrorMessage } from '../../api/client'
import { FeedbackBanner } from '../../components/FeedbackBanner'
import { createJob, deleteJob, getJobs, updateJob } from './jobApi'
import { JobForm } from './JobForm'
import { JobList } from './JobList'
import type { JobInput, JobPosting } from './jobTypes'

export function JobsSection() {
  const [jobs, setJobs] = useState<JobPosting[]>([])
  const [editingJob, setEditingJob] = useState<JobPosting | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [deletingId, setDeletingId] = useState<number | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const saveLock = useRef(false)

  async function refreshJobs() {
    setIsLoading(true)
    setLoadError(null)

    try {
      setJobs(await getJobs())
    } catch (error) {
      setLoadError(getErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()

    async function loadInitialJobs() {
      try {
        setJobs(await getJobs(controller.signal))
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

    void loadInitialJobs()
    return () => controller.abort()
  }, [])

  async function handleSubmit(input: JobInput) {
    if (saveLock.current) {
      return
    }

    saveLock.current = true
    setIsSaving(true)
    setFormError(null)
    setActionError(null)
    setSuccessMessage(null)

    try {
      if (editingJob) {
        await updateJob(editingJob.id, input)
        setSuccessMessage('Job posting updated.')
        setEditingJob(null)
      } else {
        await createJob(input)
        setSuccessMessage('Job posting saved.')
      }

      await refreshJobs()
    } catch (error) {
      setFormError(getErrorMessage(error))
      throw error
    } finally {
      saveLock.current = false
      setIsSaving(false)
    }
  }

  function handleEdit(job: JobPosting) {
    setEditingJob(job)
    setFormError(null)
    setActionError(null)
    setSuccessMessage(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function handleCancelEdit() {
    setEditingJob(null)
    setFormError(null)
  }

  async function handleDelete(job: JobPosting) {
    if (!window.confirm(`Delete “${job.title}” at ${job.company}?`)) {
      return
    }

    setDeletingId(job.id)
    setActionError(null)
    setSuccessMessage(null)

    try {
      await deleteJob(job.id)

      if (editingJob?.id === job.id) {
        setEditingJob(null)
        setFormError(null)
      }

      setSuccessMessage('Job posting deleted.')
      await refreshJobs()
    } catch (error) {
      setActionError(getErrorMessage(error))
    } finally {
      setDeletingId(null)
    }
  }

  return (
    <section className="feature-layout" aria-labelledby="jobs-heading">
      <aside className="form-panel">
        <JobForm
          key={editingJob?.id ?? 'new-job'}
          error={formError}
          isSubmitting={isSaving}
          job={editingJob}
          onCancel={handleCancelEdit}
          onSubmit={handleSubmit}
        />
      </aside>

      <div className="list-panel">
        <div className="list-heading">
          <div>
            <p className="eyebrow">Saved records</p>
            <h2 id="jobs-heading">Job postings</h2>
          </div>
          <span className="count-badge">{jobs.length}</span>
        </div>

        {successMessage && <FeedbackBanner tone="success">{successMessage}</FeedbackBanner>}
        {actionError && <FeedbackBanner tone="error">{actionError}</FeedbackBanner>}
        {loadError && (
          <FeedbackBanner tone="error">
            <span>{loadError}</span>
            <button className="banner-button" type="button" onClick={() => void refreshJobs()}>
              Try again
            </button>
          </FeedbackBanner>
        )}
        {isLoading && jobs.length === 0 && (
          <FeedbackBanner tone="info">Loading job postings…</FeedbackBanner>
        )}
        {isLoading && jobs.length > 0 && <p className="refresh-note">Refreshing…</p>}

        {(!loadError || jobs.length > 0) && (
          <JobList
            deletingId={deletingId}
            editingId={editingJob?.id ?? null}
            jobs={jobs}
            onDelete={(job) => void handleDelete(job)}
            onEdit={handleEdit}
          />
        )}
      </div>
    </section>
  )
}

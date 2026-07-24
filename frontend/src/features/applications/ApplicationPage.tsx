import { useEffect, useMemo, useRef, useState } from 'react'
import { ApiError, getErrorMessage } from '../../api/client'
import { FeedbackBanner } from '../../components/FeedbackBanner'
import { getAnalyses } from '../analyses/analysisApi'
import type { Analysis } from '../analyses/analysisTypes'
import { getJobs } from '../jobs/jobApi'
import type { JobPosting } from '../jobs/jobTypes'
import { getResumes } from '../resumes/resumeApi'
import type { Resume } from '../resumes/resumeTypes'
import {
  createApplication,
  deleteApplication,
  getApplication,
  getApplicationHistory,
  getApplications,
  updateApplication,
  updateApplicationStatus,
} from './applicationApi'
import { ApplicationDashboard } from './ApplicationDashboard'
import { ApplicationDetails } from './ApplicationDetails'
import { ApplicationForm } from './ApplicationForm'
import { ApplicationList } from './ApplicationList'
import {
  APPLICATION_STATUSES,
  APPLICATION_STATUS_LABELS,
  type ApplicationStatus,
  type ApplicationStatusHistory,
  type CreateApplicationInput,
  type JobApplication,
  type UpdateApplicationInput,
} from './applicationTypes'

type ApplicationPageProps = {
  onNavigate: (section: 'jobs' | 'resumes' | 'analyses') => void
}

type PageView = 'tracker' | 'create' | 'details' | 'edit'
type StatusFilter = ApplicationStatus | 'ALL'

function applicationErrorMessage(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return getErrorMessage(error)
  }

  if (error.status === 400) {
    return `Check the application details and try again. ${error.message}`
  }
  if (error.status === 404) {
    return 'A selected job, resume, analysis, or application no longer exists. Refresh and try again.'
  }
  if (error.status === 409) {
    return 'That job is already being tracked. Open its existing application instead.'
  }
  return error.message
}

export function ApplicationPage({ onNavigate }: ApplicationPageProps) {
  const [applications, setApplications] = useState<JobApplication[]>([])
  const [jobs, setJobs] = useState<JobPosting[]>([])
  const [resumes, setResumes] = useState<Resume[]>([])
  const [analyses, setAnalyses] = useState<Analysis[]>([])
  const [selected, setSelected] = useState<JobApplication | null>(null)
  const [history, setHistory] = useState<ApplicationStatusHistory[]>([])
  const [view, setView] = useState<PageView>('tracker')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [openingId, setOpeningId] = useState<number | null>(null)
  const [isStatusUpdating, setIsStatusUpdating] = useState(false)
  const [isHistoryLoading, setIsHistoryLoading] = useState(false)
  const [isDeleting, setIsDeleting] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [formError, setFormError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const saveLock = useRef(false)

  const filteredApplications = useMemo(
    () => statusFilter === 'ALL'
      ? applications
      : applications.filter((application) => application.status === statusFilter),
    [applications, statusFilter],
  )

  useEffect(() => {
    const controller = new AbortController()

    async function loadWorkspace() {
      setIsLoading(true)
      setLoadError(null)

      try {
        const [loadedApplications, loadedJobs, loadedResumes, loadedAnalyses] = await Promise.all([
          getApplications(undefined, controller.signal),
          getJobs(controller.signal),
          getResumes(controller.signal),
          getAnalyses(controller.signal),
        ])
        setApplications(loadedApplications)
        setJobs(loadedJobs)
        setResumes(loadedResumes)
        setAnalyses(loadedAnalyses)
      } catch (error) {
        if (!controller.signal.aborted) {
          setLoadError(applicationErrorMessage(error))
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsLoading(false)
        }
      }
    }

    void loadWorkspace()
    return () => controller.abort()
  }, [])

  async function retryLoad() {
    setIsLoading(true)
    setLoadError(null)
    try {
      const [loadedApplications, loadedJobs, loadedResumes, loadedAnalyses] = await Promise.all([
        getApplications(),
        getJobs(),
        getResumes(),
        getAnalyses(),
      ])
      setApplications(loadedApplications)
      setJobs(loadedJobs)
      setResumes(loadedResumes)
      setAnalyses(loadedAnalyses)
    } catch (error) {
      setLoadError(applicationErrorMessage(error))
    } finally {
      setIsLoading(false)
    }
  }

  async function loadHistory(applicationId: number) {
    setIsHistoryLoading(true)
    setHistoryError(null)
    try {
      setHistory(await getApplicationHistory(applicationId))
    } catch (error) {
      setHistoryError(applicationErrorMessage(error))
    } finally {
      setIsHistoryLoading(false)
    }
  }

  async function openApplication(id: number) {
    if (openingId !== null) {
      return
    }

    setOpeningId(id)
    setActionError(null)
    setSuccessMessage(null)
    setHistory([])
    setHistoryError(null)
    try {
      const application = await getApplication(id)
      setSelected(application)
      setView('details')
      void loadHistory(id)
    } catch (error) {
      setActionError(applicationErrorMessage(error))
    } finally {
      setOpeningId(null)
    }
  }

  async function handleCreate(input: CreateApplicationInput) {
    if (saveLock.current) {
      return
    }

    saveLock.current = true
    setIsSaving(true)
    setFormError(null)
    try {
      const created = await createApplication(input)
      setSelected(created)
      setApplications((current) => [
        created,
        ...current.filter((application) => application.id !== created.id),
      ])
      setView('details')
      setSuccessMessage('Application added to the tracker.')
      void loadHistory(created.id)
    } catch (error) {
      setFormError(applicationErrorMessage(error))
      throw error
    } finally {
      saveLock.current = false
      setIsSaving(false)
    }
  }

  async function handleUpdate(input: UpdateApplicationInput) {
    if (!selected || saveLock.current) {
      return
    }

    saveLock.current = true
    setIsSaving(true)
    setFormError(null)
    try {
      const updated = await updateApplication(selected.id, input)
      setSelected(updated)
      setApplications((current) => current.map((application) => (
        application.id === updated.id ? updated : application
      )))
      setView('details')
      setSuccessMessage('Application details updated.')
    } catch (error) {
      setFormError(applicationErrorMessage(error))
      throw error
    } finally {
      saveLock.current = false
      setIsSaving(false)
    }
  }

  async function handleStatusChange(status: ApplicationStatus) {
    if (!selected || isStatusUpdating) {
      return
    }

    setIsStatusUpdating(true)
    setActionError(null)
    try {
      const updated = await updateApplicationStatus(selected.id, { status })
      setSelected(updated)
      setApplications((current) => current.map((application) => (
        application.id === updated.id ? updated : application
      )))
      await loadHistory(updated.id)
      setSuccessMessage(
        status === selected.status ? 'Application status was already up to date.' : 'Application status updated.',
      )
    } catch (error) {
      setActionError(applicationErrorMessage(error))
    } finally {
      setIsStatusUpdating(false)
    }
  }

  async function handleDelete() {
    if (!selected || isDeleting) {
      return
    }
    if (!window.confirm(`Delete the tracked application for “${selected.job.title}”?`)) {
      return
    }

    setIsDeleting(true)
    setActionError(null)
    try {
      await deleteApplication(selected.id)
      setApplications((current) => current.filter((application) => application.id !== selected.id))
      setSelected(null)
      setHistory([])
      setView('tracker')
      setSuccessMessage('Application removed from the tracker.')
    } catch (error) {
      setActionError(applicationErrorMessage(error))
    } finally {
      setIsDeleting(false)
    }
  }

  function showTracker() {
    setView('tracker')
    setFormError(null)
    setActionError(null)
  }

  return (
    <section className="applications-page" aria-labelledby="applications-heading">
      <header className="analysis-page__intro">
        <p className="eyebrow">Application pipeline</p>
        <h2 id="applications-heading">Application tracker</h2>
        <p>
          Track every saved opportunity, keep follow-up work visible, and review the real status
          history recorded by the backend.
        </p>
      </header>

      {successMessage && <FeedbackBanner tone="success">{successMessage}</FeedbackBanner>}

      {view === 'tracker' && (
        <>
          <ApplicationDashboard applications={applications} />
          <section className="application-tracker" aria-labelledby="tracked-applications-heading">
            <div className="application-tracker__toolbar">
              <div>
                <p className="eyebrow">Pipeline</p>
                <h3 id="tracked-applications-heading">Tracked applications</h3>
              </div>
              <div className="application-tracker__controls">
                <label htmlFor="application-status-filter">Status</label>
                <select
                  id="application-status-filter"
                  value={statusFilter}
                  onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}
                >
                  <option value="ALL">All</option>
                  {APPLICATION_STATUSES.map((status) => (
                    <option key={status} value={status}>{APPLICATION_STATUS_LABELS[status]}</option>
                  ))}
                </select>
                <button
                  className="button button--primary"
                  type="button"
                  onClick={() => {
                    setFormError(null)
                    setSuccessMessage(null)
                    setView('create')
                  }}
                >
                  Track application
                </button>
              </div>
            </div>

            {actionError && <FeedbackBanner tone="error">{actionError}</FeedbackBanner>}
            {loadError && (
              <FeedbackBanner tone="error">
                <span>{loadError}</span>
                <button className="banner-button" type="button" onClick={() => void retryLoad()}>
                  Try again
                </button>
              </FeedbackBanner>
            )}
            {isLoading && <FeedbackBanner tone="info">Loading application tracker…</FeedbackBanner>}
            {!isLoading && !loadError && (
              <ApplicationList
                applications={filteredApplications}
                hasAnyApplications={applications.length > 0}
                openingId={openingId}
                onCreate={() => setView('create')}
                onOpen={(id) => void openApplication(id)}
              />
            )}
          </section>
        </>
      )}

      {view === 'create' && (
        <section className="application-editor">
          <ApplicationForm
            analyses={analyses}
            error={formError}
            isSubmitting={isSaving}
            jobs={jobs}
            resumes={resumes}
            onCancel={showTracker}
            onCreate={handleCreate}
          />
          <aside className="application-editor__help">
            <p className="eyebrow">Before you start</p>
            <h3>Use your saved workspace records.</h3>
            <p>The backend prevents the same job from being tracked twice.</p>
            {jobs.length === 0 && (
              <button className="text-button" type="button" onClick={() => onNavigate('jobs')}>
                Go to job postings
              </button>
            )}
            {resumes.length === 0 && (
              <button className="text-button" type="button" onClick={() => onNavigate('resumes')}>
                Go to resumes
              </button>
            )}
          </aside>
        </section>
      )}

      {view === 'edit' && selected && (
        <section className="application-editor">
          <ApplicationForm
            key={selected.updatedAt}
            analyses={analyses}
            application={selected}
            error={formError}
            isSubmitting={isSaving}
            jobs={jobs}
            resumes={resumes}
            onCancel={() => {
              setFormError(null)
              setView('details')
            }}
            onUpdate={handleUpdate}
          />
        </section>
      )}

      {view === 'details' && selected && (
        <>
          <ApplicationDetails
            key={`${selected.id}-${selected.status}`}
            actionError={actionError}
            application={selected}
            deleting={isDeleting}
            history={history}
            historyError={historyError}
            historyLoading={isHistoryLoading}
            statusUpdating={isStatusUpdating}
            onBack={showTracker}
            onDelete={() => void handleDelete()}
            onEdit={() => {
              setFormError(null)
              setSuccessMessage(null)
              setView('edit')
            }}
            onRetryHistory={() => void loadHistory(selected.id)}
            onStatusChange={handleStatusChange}
          />
        </>
      )}
    </section>
  )
}

import { useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { FeedbackBanner } from '../../components/FeedbackBanner'
import type { Analysis } from '../analyses/analysisTypes'
import type { JobPosting } from '../jobs/jobTypes'
import type { Resume } from '../resumes/resumeTypes'
import {
  APPLICATION_STATUSES,
  APPLICATION_STATUS_LABELS,
  type CreateApplicationInput,
  type JobApplication,
  type UpdateApplicationInput,
} from './applicationTypes'

type ApplicationFormProps = {
  analyses: Analysis[]
  application?: JobApplication
  error: string | null
  isSubmitting: boolean
  jobs: JobPosting[]
  resumes: Resume[]
  onCancel: () => void
  onCreate?: (input: CreateApplicationInput) => Promise<void>
  onUpdate?: (input: UpdateApplicationInput) => Promise<void>
}

type FormValues = {
  jobPostingId: string
  resumeId: string
  analysisId: string
  status: CreateApplicationInput['status']
  appliedAt: string
  nextAction: string
  nextActionAt: string
  notes: string
}

function toLocalDateTimeInput(value: string | null): string {
  if (!value) {
    return ''
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }

  const offset = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offset).toISOString().slice(0, 16)
}

function optionalId(value: string): number | null {
  return value ? Number(value) : null
}

function optionalText(value: string): string | null {
  const trimmed = value.trim()
  return trimmed || null
}

function optionalDateTime(value: string): string | null {
  return value ? new Date(value).toISOString() : null
}

function initialValues(application?: JobApplication): FormValues {
  return {
    jobPostingId: application ? String(application.job.id) : '',
    resumeId: application?.resume ? String(application.resume.id) : '',
    analysisId: application?.analysis ? String(application.analysis.id) : '',
    status: application?.status ?? 'SAVED',
    appliedAt: application?.appliedAt ?? '',
    nextAction: application?.nextAction ?? '',
    nextActionAt: toLocalDateTimeInput(application?.nextActionAt ?? null),
    notes: application?.notes ?? '',
  }
}

export function ApplicationForm({
  analyses,
  application,
  error,
  isSubmitting,
  jobs,
  resumes,
  onCancel,
  onCreate,
  onUpdate,
}: ApplicationFormProps) {
  const [values, setValues] = useState<FormValues>(() => initialValues(application))
  const submitLock = useRef(false)
  const isEditing = application !== undefined
  const compatibleAnalyses = useMemo(() => {
    const jobId = Number(values.jobPostingId)
    const resumeId = optionalId(values.resumeId)

    if (!jobId) {
      return []
    }

    return analyses.filter((analysis) => (
      analysis.jobPostingId === jobId
      && (resumeId === null || analysis.resumeId === resumeId)
    ))
  }, [analyses, values.jobPostingId, values.resumeId])

  function handleChange(event: ChangeEvent<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>) {
    const { name, value } = event.target

    setValues((current) => {
      const next = { ...current, [name]: value }
      if (name === 'jobPostingId' || name === 'resumeId') {
        const analysisId = optionalId(current.analysisId)
        const selectedJobId = Number(name === 'jobPostingId' ? value : current.jobPostingId)
        const selectedResumeId = optionalId(name === 'resumeId' ? value : current.resumeId)
        const remainsCompatible = analyses.some((analysis) => (
          analysis.id === analysisId
          && analysis.jobPostingId === selectedJobId
          && (selectedResumeId === null || analysis.resumeId === selectedResumeId)
        ))

        if (!remainsCompatible) {
          next.analysisId = ''
        }
      }
      return next
    })
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (submitLock.current || isSubmitting) {
      return
    }

    submitLock.current = true
    const sharedInput: UpdateApplicationInput = {
      resumeId: optionalId(values.resumeId),
      analysisId: optionalId(values.analysisId),
      appliedAt: values.appliedAt || null,
      nextAction: optionalText(values.nextAction),
      nextActionAt: optionalDateTime(values.nextActionAt),
      notes: optionalText(values.notes),
    }

    try {
      if (isEditing) {
        await onUpdate?.(sharedInput)
      } else {
        await onCreate?.({
          jobPostingId: Number(values.jobPostingId),
          status: values.status,
          ...sharedInput,
        })
      }
    } catch {
      // The parent view keeps and presents the backend error.
    } finally {
      submitLock.current = false
    }
  }

  return (
    <form className="application-form" onSubmit={handleSubmit}>
      <header className="panel-heading">
        <div>
          <p className="eyebrow">Application tracker</p>
          <h2>{isEditing ? 'Edit application' : 'Track an application'}</h2>
        </div>
        {isEditing && <span className="edit-badge">Editing</span>}
      </header>
      <p className="panel-description">
        {isEditing
          ? 'Update the supporting records and follow-up details. Status changes are managed separately.'
          : 'Connect a saved job to your resume, analysis, and next action.'}
      </p>

      {error && <FeedbackBanner tone="error">{error}</FeedbackBanner>}
      {!isEditing && jobs.length === 0 && (
        <FeedbackBanner tone="info">Create a job posting before tracking an application.</FeedbackBanner>
      )}

      <div className="form-field">
        <label htmlFor="application-job">Job posting</label>
        <select
          id="application-job"
          name="jobPostingId"
          value={values.jobPostingId}
          onChange={handleChange}
          disabled={isEditing}
          required
        >
          <option value="">Select a job</option>
          {jobs.map((job) => (
            <option key={job.id} value={job.id}>{job.title} at {job.company}</option>
          ))}
          {isEditing && !jobs.some((job) => job.id === application.job.id) && (
            <option value={application.job.id}>
              {application.job.title} at {application.job.company}
            </option>
          )}
        </select>
      </div>

      <div className="field-grid field-grid--two-columns">
        <div className="form-field">
          <label htmlFor="application-resume">
            Resume <span className="optional-label">Optional</span>
          </label>
          <select
            id="application-resume"
            name="resumeId"
            value={values.resumeId}
            onChange={handleChange}
          >
            <option value="">No resume</option>
            {resumes.map((resume) => (
              <option key={resume.id} value={resume.id}>
                {resume.name}{resume.targetRole ? ` — ${resume.targetRole}` : ''}
              </option>
            ))}
          </select>
        </div>

        <div className="form-field">
          <label htmlFor="application-analysis">
            Analysis <span className="optional-label">Optional</span>
          </label>
          <select
            id="application-analysis"
            name="analysisId"
            value={values.analysisId}
            onChange={handleChange}
            disabled={!values.jobPostingId}
          >
            <option value="">No analysis</option>
            {compatibleAnalyses.map((analysis) => (
              <option key={analysis.id} value={analysis.id}>
                {analysis.matchScore}% · {analysis.provider} · #{analysis.id}
              </option>
            ))}
          </select>
          {values.jobPostingId && compatibleAnalyses.length === 0 && (
            <span className="field-help">No compatible saved analyses.</span>
          )}
        </div>
      </div>

      {!isEditing && (
        <div className="field-grid field-grid--two-columns">
          <div className="form-field">
            <label htmlFor="application-status">Initial status</label>
            <select
              id="application-status"
              name="status"
              value={values.status}
              onChange={handleChange}
            >
              {APPLICATION_STATUSES.map((status) => (
                <option key={status} value={status}>{APPLICATION_STATUS_LABELS[status]}</option>
              ))}
            </select>
          </div>
          <div className="form-field">
            <label htmlFor="application-applied-at">
              Applied date <span className="optional-label">Optional</span>
            </label>
            <input
              id="application-applied-at"
              name="appliedAt"
              type="date"
              value={values.appliedAt}
              onChange={handleChange}
            />
          </div>
        </div>
      )}

      {isEditing && (
        <div className="form-field">
          <label htmlFor="application-applied-at">
            Applied date <span className="optional-label">Optional</span>
          </label>
          <input
            id="application-applied-at"
            name="appliedAt"
            type="date"
            value={values.appliedAt}
            onChange={handleChange}
          />
        </div>
      )}

      <div className="field-grid field-grid--two-columns">
        <div className="form-field">
          <label htmlFor="application-next-action">
            Next action <span className="optional-label">Optional</span>
          </label>
          <input
            id="application-next-action"
            name="nextAction"
            value={values.nextAction}
            onChange={handleChange}
            maxLength={500}
            placeholder="Follow up with recruiter"
          />
        </div>
        <div className="form-field">
          <label htmlFor="application-next-action-at">
            Next-action date <span className="optional-label">Optional</span>
          </label>
          <input
            id="application-next-action-at"
            name="nextActionAt"
            type="datetime-local"
            value={values.nextActionAt}
            onChange={handleChange}
          />
        </div>
      </div>

      <div className="form-field">
        <label htmlFor="application-notes">
          Notes <span className="optional-label">Optional</span>
        </label>
        <textarea
          id="application-notes"
          name="notes"
          value={values.notes}
          onChange={handleChange}
          maxLength={10000}
          rows={6}
        />
      </div>

      <div className="form-actions">
        <button
          className="button button--primary"
          type="submit"
          disabled={isSubmitting || (!isEditing && jobs.length === 0)}
        >
          {isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Track application'}
        </button>
        <button
          className="button button--secondary"
          type="button"
          onClick={onCancel}
          disabled={isSubmitting}
        >
          Cancel
        </button>
      </div>
    </form>
  )
}

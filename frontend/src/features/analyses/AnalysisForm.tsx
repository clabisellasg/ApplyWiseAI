import { useRef, useState, type FormEvent } from 'react'
import { FeedbackBanner } from '../../components/FeedbackBanner'
import type { JobPosting } from '../jobs/jobTypes'
import type { Resume } from '../resumes/resumeTypes'
import type { CreateAnalysisInput } from './analysisTypes'

type AnalysisFormProps = {
  isLoading: boolean
  isSubmitting: boolean
  jobs: JobPosting[]
  loadError: string | null
  onNavigate: (section: 'jobs' | 'resumes') => void
  onRetry: () => void
  onSubmit: (input: CreateAnalysisInput) => Promise<void>
  resumes: Resume[]
  submissionError: string | null
}

export function AnalysisForm({
  isLoading,
  isSubmitting,
  jobs,
  loadError,
  onNavigate,
  onRetry,
  onSubmit,
  resumes,
  submissionError,
}: AnalysisFormProps) {
  const [resumeId, setResumeId] = useState('')
  const [jobPostingId, setJobPostingId] = useState('')
  const submitLock = useRef(false)
  const hasRequiredRecords = resumes.length > 0 && jobs.length > 0
  const canSubmit = hasRequiredRecords && resumeId !== '' && jobPostingId !== ''

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (!canSubmit || submitLock.current || isSubmitting) {
      return
    }

    submitLock.current = true
    try {
      await onSubmit({
        resumeId: Number(resumeId),
        jobPostingId: Number(jobPostingId),
      })
    } catch {
      // The page presents the request error next to this form.
    } finally {
      submitLock.current = false
    }
  }

  return (
    <form className="analysis-form" onSubmit={handleSubmit}>
      <div className="panel-heading">
        <div>
          <p className="eyebrow">New comparison</p>
          <h2>Run a job match</h2>
        </div>
        <span className="local-badge">Local</span>
      </div>

      <p className="panel-description">
        Compare saved text using the deterministic keyword analyzer. No external AI service is
        contacted.
      </p>

      {isLoading && <FeedbackBanner tone="info">Loading resumes and jobs…</FeedbackBanner>}
      {loadError && (
        <FeedbackBanner tone="error">
          <span>{loadError}</span>
          <button className="banner-button" type="button" onClick={onRetry}>
            Try again
          </button>
        </FeedbackBanner>
      )}
      {submissionError && <FeedbackBanner tone="error">{submissionError}</FeedbackBanner>}

      {!isLoading && !loadError && resumes.length === 0 && (
        <div className="requirement-card">
          <strong>Create a resume first</strong>
          <p>A saved text resume is required before a comparison can run.</p>
          <button className="text-button" type="button" onClick={() => onNavigate('resumes')}>
            Go to resumes
          </button>
        </div>
      )}

      {!isLoading && !loadError && jobs.length === 0 && (
        <div className="requirement-card">
          <strong>Create a job posting first</strong>
          <p>A saved job description is required before a comparison can run.</p>
          <button className="text-button" type="button" onClick={() => onNavigate('jobs')}>
            Go to job postings
          </button>
        </div>
      )}

      <div className="form-field">
        <label htmlFor="analysis-resume">Resume</label>
        <select
          id="analysis-resume"
          value={resumeId}
          onChange={(event) => setResumeId(event.target.value)}
          disabled={isLoading || resumes.length === 0}
          required
        >
          <option value="">Select a resume</option>
          {resumes.map((resume) => (
            <option key={resume.id} value={resume.id}>
              {resume.name}{resume.targetRole ? ` — ${resume.targetRole}` : ''}
            </option>
          ))}
        </select>
      </div>

      <div className="form-field">
        <label htmlFor="analysis-job">Job posting</label>
        <select
          id="analysis-job"
          value={jobPostingId}
          onChange={(event) => setJobPostingId(event.target.value)}
          disabled={isLoading || jobs.length === 0}
          required
        >
          <option value="">Select a job posting</option>
          {jobs.map((job) => (
            <option key={job.id} value={job.id}>
              {job.title} — {job.company}
            </option>
          ))}
        </select>
      </div>

      <button
        className="button button--primary analysis-submit"
        type="submit"
        disabled={!canSubmit || isSubmitting || isLoading || loadError !== null}
      >
        {isSubmitting ? 'Analyzing…' : 'Analyze match'}
      </button>
    </form>
  )
}

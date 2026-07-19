import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { FeedbackBanner } from '../../components/FeedbackBanner'
import type { JobInput, JobPosting } from './jobTypes'

type JobFormProps = {
  error: string | null
  isSubmitting: boolean
  job: JobPosting | null
  onCancel: () => void
  onSubmit: (input: JobInput) => Promise<void>
}

type JobFormValues = {
  title: string
  company: string
  description: string
  sourceUrl: string
}

const EMPTY_JOB_FORM: JobFormValues = {
  title: '',
  company: '',
  description: '',
  sourceUrl: '',
}

function initialValues(job: JobPosting | null): JobFormValues {
  if (!job) {
    return EMPTY_JOB_FORM
  }

  return {
    title: job.title,
    company: job.company,
    description: job.description,
    sourceUrl: job.sourceUrl ?? '',
  }
}

export function JobForm({ error, isSubmitting, job, onCancel, onSubmit }: JobFormProps) {
  const [values, setValues] = useState<JobFormValues>(() => initialValues(job))
  const submitLock = useRef(false)
  const isEditing = job !== null

  function handleChange(event: ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) {
    const { name, value } = event.target
    setValues((current) => ({ ...current, [name]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (submitLock.current || isSubmitting) {
      return
    }

    submitLock.current = true

    try {
      await onSubmit({
        title: values.title.trim(),
        company: values.company.trim(),
        description: values.description.trim(),
        sourceUrl: values.sourceUrl.trim() || null,
      })

      if (!isEditing) {
        setValues(EMPTY_JOB_FORM)
      }
    } catch {
      // The parent section presents the API error next to this form.
    } finally {
      submitLock.current = false
    }
  }

  return (
    <form className="record-form" onSubmit={handleSubmit}>
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Job posting</p>
          <h2>{isEditing ? 'Edit job' : 'Add a job'}</h2>
        </div>
        {isEditing && <span className="edit-badge">Editing</span>}
      </div>

      <p className="panel-description">
        Save the role details you want to compare with your resume later.
      </p>

      {error && <FeedbackBanner tone="error">{error}</FeedbackBanner>}

      <div className="field-grid field-grid--two-columns">
        <div className="form-field">
          <label htmlFor="job-title">Title</label>
          <input
            id="job-title"
            name="title"
            value={values.title}
            onChange={handleChange}
            maxLength={255}
            autoComplete="off"
            required
          />
        </div>

        <div className="form-field">
          <label htmlFor="job-company">Company</label>
          <input
            id="job-company"
            name="company"
            value={values.company}
            onChange={handleChange}
            maxLength={255}
            autoComplete="organization"
            required
          />
        </div>
      </div>

      <div className="form-field">
        <label htmlFor="job-source-url">
          Source URL <span className="optional-label">Optional</span>
        </label>
        <input
          id="job-source-url"
          name="sourceUrl"
          value={values.sourceUrl}
          onChange={handleChange}
          maxLength={2048}
          placeholder="https://company.example/jobs/123"
          type="url"
        />
      </div>

      <div className="form-field">
        <label htmlFor="job-description">Description</label>
        <textarea
          id="job-description"
          name="description"
          value={values.description}
          onChange={handleChange}
          rows={9}
          required
        />
      </div>

      <div className="form-actions">
        <button className="button button--primary" type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Save job'}
        </button>
        {isEditing && (
          <button className="button button--secondary" type="button" onClick={onCancel}>
            Cancel editing
          </button>
        )}
      </div>
    </form>
  )
}

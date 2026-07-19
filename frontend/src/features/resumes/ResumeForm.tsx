import { useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { FeedbackBanner } from '../../components/FeedbackBanner'
import type { Resume, ResumeInput } from './resumeTypes'

type ResumeFormProps = {
  error: string | null
  isSubmitting: boolean
  onCancel: () => void
  onSubmit: (input: ResumeInput) => Promise<void>
  resume: Resume | null
}

type ResumeFormValues = {
  name: string
  targetRole: string
  content: string
}

const EMPTY_RESUME_FORM: ResumeFormValues = {
  name: '',
  targetRole: '',
  content: '',
}

function initialValues(resume: Resume | null): ResumeFormValues {
  if (!resume) {
    return EMPTY_RESUME_FORM
  }

  return {
    name: resume.name,
    targetRole: resume.targetRole ?? '',
    content: resume.content,
  }
}

export function ResumeForm({ error, isSubmitting, onCancel, onSubmit, resume }: ResumeFormProps) {
  const [values, setValues] = useState<ResumeFormValues>(() => initialValues(resume))
  const submitLock = useRef(false)
  const isEditing = resume !== null

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
        name: values.name.trim(),
        targetRole: values.targetRole.trim() || null,
        content: values.content.trim(),
      })

      if (!isEditing) {
        setValues(EMPTY_RESUME_FORM)
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
          <p className="eyebrow">Resume</p>
          <h2>{isEditing ? 'Edit resume' : 'Add a resume'}</h2>
        </div>
        {isEditing && <span className="edit-badge">Editing</span>}
      </div>

      <p className="panel-description">
        Store a text version of your resume so future analysis stays grounded in your experience.
      </p>

      {error && <FeedbackBanner tone="error">{error}</FeedbackBanner>}

      <div className="form-field">
        <label htmlFor="resume-name">Name</label>
        <input
          id="resume-name"
          name="name"
          value={values.name}
          onChange={handleChange}
          maxLength={255}
          placeholder="Primary resume"
          autoComplete="off"
          required
        />
      </div>

      <div className="form-field">
        <label htmlFor="resume-target-role">
          Target role <span className="optional-label">Optional</span>
        </label>
        <input
          id="resume-target-role"
          name="targetRole"
          value={values.targetRole}
          onChange={handleChange}
          maxLength={255}
          placeholder="Software Engineer, Data Analyst, IT Support…"
          autoComplete="organization-title"
        />
      </div>

      <div className="form-field">
        <label htmlFor="resume-content">Resume content</label>
        <textarea
          id="resume-content"
          name="content"
          value={values.content}
          onChange={handleChange}
          rows={14}
          placeholder="Paste the text of your resume here."
          required
        />
      </div>

      <div className="form-actions">
        <button className="button button--primary" type="submit" disabled={isSubmitting}>
          {isSubmitting ? 'Saving…' : isEditing ? 'Save changes' : 'Save resume'}
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

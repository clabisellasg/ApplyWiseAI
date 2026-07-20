import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { AnalysisForm } from './AnalysisForm'
import type { JobPosting } from '../jobs/jobTypes'
import type { Resume } from '../resumes/resumeTypes'

const RESUME: Resume = {
  id: 1,
  name: 'Primary Resume',
  targetRole: 'Software Engineer',
  content: 'Java experience',
  createdAt: '2026-02-01T10:00:00Z',
  updatedAt: '2026-02-01T10:00:00Z',
}

const JOB: JobPosting = {
  id: 2,
  title: 'Backend Engineer',
  company: 'Genesis',
  description: 'Java required',
  sourceUrl: null,
  createdAt: '2026-02-01T10:00:00Z',
  updatedAt: '2026-02-01T10:00:00Z',
}

describe('AnalysisForm', () => {
  it('requires both a resume and job before submission', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)

    render(
      <AnalysisForm
        isLoading={false}
        isSubmitting={false}
        jobs={[JOB]}
        loadError={null}
        resumes={[RESUME]}
        submissionError={null}
        onNavigate={vi.fn()}
        onRetry={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    const submitButton = screen.getByRole('button', { name: 'Analyze match' })
    expect(submitButton).toBeDisabled()

    await user.selectOptions(screen.getByLabelText('Resume'), '1')
    expect(submitButton).toBeDisabled()

    await user.selectOptions(screen.getByLabelText('Job posting'), '2')
    expect(submitButton).toBeEnabled()
    await user.click(submitButton)

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(onSubmit).toHaveBeenCalledWith({ resumeId: 1, jobPostingId: 2 })
  })

  it('explains the empty-resume state and links to resume creation', async () => {
    const user = userEvent.setup()
    const onNavigate = vi.fn()

    render(
      <AnalysisForm
        isLoading={false}
        isSubmitting={false}
        jobs={[JOB]}
        loadError={null}
        resumes={[]}
        submissionError={null}
        onNavigate={onNavigate}
        onRetry={vi.fn()}
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getByText('Create a resume first')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Analyze match' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Go to resumes' }))

    expect(onNavigate).toHaveBeenCalledWith('resumes')
  })
})

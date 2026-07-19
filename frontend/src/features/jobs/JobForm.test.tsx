import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { JobForm } from './JobForm'

describe('JobForm', () => {
  it('submits a new job once and resets after success', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn().mockResolvedValue(undefined)

    render(
      <JobForm
        error={null}
        isSubmitting={false}
        job={null}
        onCancel={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    await user.type(screen.getByLabelText('Title'), 'Backend Engineer')
    await user.type(screen.getByLabelText('Company'), 'Genesis')
    await user.type(screen.getByLabelText('Description'), 'Build reliable services')
    await user.type(screen.getByLabelText(/Source URL/), 'https://example.com/jobs/1')
    await user.click(screen.getByRole('button', { name: 'Save job' }))

    expect(onSubmit).toHaveBeenCalledTimes(1)
    expect(onSubmit).toHaveBeenCalledWith({
      title: 'Backend Engineer',
      company: 'Genesis',
      description: 'Build reliable services',
      sourceUrl: 'https://example.com/jobs/1',
    })

    await waitFor(() => expect(screen.getByLabelText('Title')).toHaveValue(''))
    expect(screen.getByLabelText('Description')).toHaveValue('')
  })
})

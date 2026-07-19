import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { JobsSection } from './JobsSection'

describe('JobsSection', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('shows a backend API error and offers a retry', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      statusText: 'Service Unavailable',
      json: vi.fn().mockResolvedValue({ message: 'Database unavailable' }),
    })
    vi.stubGlobal('fetch', fetchMock)

    render(<JobsSection />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Database unavailable')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeEnabled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})

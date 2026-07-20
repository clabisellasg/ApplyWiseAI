import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AnalysisPage } from './AnalysisPage'
import type { Analysis } from './analysisTypes'
import type { JobPosting } from '../jobs/jobTypes'
import type { Resume } from '../resumes/resumeTypes'

const RESUME: Resume = {
  id: 1,
  name: 'Primary Resume',
  targetRole: 'Software Engineer',
  content: 'Built Java REST APIs.',
  createdAt: '2026-02-01T10:00:00Z',
  updatedAt: '2026-02-01T10:00:00Z',
}

const JOB: JobPosting = {
  id: 2,
  title: 'Backend Engineer',
  company: 'Genesis',
  description: 'Java and Docker required.',
  sourceUrl: null,
  createdAt: '2026-02-01T10:00:00Z',
  updatedAt: '2026-02-01T10:00:00Z',
}

function analysis(summary = 'One matched skill and one missing skill.'): Analysis {
  return {
    id: 7,
    resumeId: 1,
    jobPostingId: 2,
    matchScore: 50,
    summary,
    result: {
      matchScore: 50,
      summary,
      skills: [
        {
          name: 'Java',
          status: 'MATCHED',
          resumeEvidence: 'Built Java REST APIs.',
          explanation: 'Both texts mention Java.',
        },
        {
          name: 'Docker',
          status: 'MISSING',
          resumeEvidence: null,
          explanation: 'No Docker evidence was found.',
        },
      ],
      strengths: ['Java is supported by resume evidence.'],
      gaps: ['Docker is not evidenced in the resume.'],
      recommendedActions: ['Develop Docker experience before claiming it.'],
    },
    provider: 'fake',
    model: 'keyword-matcher-v1',
    promptVersion: 'v1',
    createdAt: '2026-02-01T10:15:30Z',
  }
}

function response(body: unknown, status = 200): Promise<Response> {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 503 ? 'Service Unavailable' : '',
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response)
}

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === 'string') {
    return input
  }
  return input instanceof URL ? input.toString() : input.url
}

function setupFetch(options?: { history?: Analysis[]; postError?: string; detail?: Analysis }) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = requestUrl(input)
    const method = init?.method ?? 'GET'

    if (url.endsWith('/api/resumes')) {
      return response([RESUME])
    }
    if (url.endsWith('/api/jobs')) {
      return response([JOB])
    }
    if (url.endsWith('/api/analyses') && method === 'POST') {
      return options?.postError
        ? response({ message: options.postError }, 503)
        : response(analysis())
    }
    if (url.endsWith('/api/analyses') && method === 'GET') {
      return response(options?.history ?? [])
    }
    if (url.endsWith('/api/analyses/7')) {
      return response(options?.detail ?? analysis())
    }

    throw new Error(`Unexpected request: ${method} ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('AnalysisPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('submits the selected IDs and adds the completed result to history', async () => {
    const user = userEvent.setup()
    const fetchMock = setupFetch()
    render(<AnalysisPage onNavigate={vi.fn()} />)

    await screen.findByRole('option', { name: 'Primary Resume — Software Engineer' })
    await user.selectOptions(screen.getByLabelText('Resume'), '1')
    await user.selectOptions(screen.getByLabelText('Job posting'), '2')
    await user.click(screen.getByRole('button', { name: 'Analyze match' }))

    expect(await screen.findByText('Analysis completed and saved to history.')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Primary Resume' })).toBeVisible()
    expect(screen.getByText('One matched skill and one missing skill.')).toBeVisible()
    expect(screen.getAllByRole('button', { name: /Primary Resume/ })).toHaveLength(1)
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/analyses',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ resumeId: 1, jobPostingId: 2 }),
      }),
    )
  })

  it('shows a readable API failure from analysis creation', async () => {
    const user = userEvent.setup()
    setupFetch({ postError: 'Analyzer unavailable' })
    render(<AnalysisPage onNavigate={vi.fn()} />)

    await screen.findByRole('option', { name: 'Primary Resume — Software Engineer' })
    await user.selectOptions(screen.getByLabelText('Resume'), '1')
    await user.selectOptions(screen.getByLabelText('Job posting'), '2')
    await user.click(screen.getByRole('button', { name: 'Analyze match' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Analyzer unavailable')
    expect(screen.getByRole('button', { name: 'Analyze match' })).toBeEnabled()
  })

  it('reopens a complete saved analysis from history', async () => {
    const user = userEvent.setup()
    const reopened = analysis('Reopened complete result.')
    const fetchMock = setupFetch({ history: [analysis()], detail: reopened })
    render(<AnalysisPage onNavigate={vi.fn()} />)

    const historyButton = await screen.findByRole('button', { name: /Primary Resume/ })
    await user.click(historyButton)

    expect(await screen.findByText('Reopened complete result.')).toBeVisible()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/analyses/7',
      expect.objectContaining({}),
    ))
    expect(historyButton).toHaveAttribute('aria-pressed', 'true')
  })
})

import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApplicationPage } from './ApplicationPage'
import type { Analysis } from '../analyses/analysisTypes'
import type { JobPosting } from '../jobs/jobTypes'
import type { Resume } from '../resumes/resumeTypes'
import type {
  ApplicationStatusHistory,
  JobApplication,
} from './applicationTypes'

const JOB: JobPosting = {
  id: 10,
  title: 'Platform Engineer',
  company: 'Fictional Systems',
  description: 'Build Java services.',
  sourceUrl: null,
  createdAt: '2026-07-20T00:00:00Z',
  updatedAt: '2026-07-20T00:00:00Z',
}

const OTHER_JOB: JobPosting = {
  ...JOB,
  id: 11,
  title: 'Data Analyst',
}

const RESUME: Resume = {
  id: 20,
  name: 'Engineering Resume',
  targetRole: 'Platform Engineer',
  content: 'Built Java services.',
  createdAt: '2026-07-20T00:00:00Z',
  updatedAt: '2026-07-20T00:00:00Z',
}

const ANALYSIS: Analysis = {
  id: 30,
  resumeId: RESUME.id,
  jobPostingId: JOB.id,
  matchScore: 80,
  summary: 'Grounded match.',
  result: {
    matchScore: 80,
    summary: 'Grounded match.',
    skills: [],
    strengths: [],
    gaps: [],
    recommendedActions: [],
  },
  provider: 'fake',
  model: 'keyword-matcher-v1',
  promptVersion: 'v1',
  createdAt: '2026-07-20T00:00:00Z',
  cacheHit: false,
}

function tracked(overrides: Partial<JobApplication> = {}): JobApplication {
  return {
    id: 40,
    job: { id: JOB.id, title: JOB.title, company: JOB.company },
    resume: { id: RESUME.id, name: RESUME.name, targetRole: RESUME.targetRole },
    analysis: {
      id: ANALYSIS.id,
      score: ANALYSIS.matchScore,
      provider: ANALYSIS.provider,
      model: ANALYSIS.model,
    },
    status: 'APPLIED',
    appliedAt: '2026-07-20',
    nextAction: 'Email recruiter',
    nextActionAt: '2026-07-28T09:00:00+08:00',
    notes: 'Submitted online.',
    createdAt: '2026-07-20T00:00:00Z',
    updatedAt: '2026-07-21T00:00:00Z',
    ...overrides,
  }
}

const INITIAL_HISTORY: ApplicationStatusHistory[] = [
  {
    id: 2,
    previousStatus: 'SAVED',
    newStatus: 'APPLIED',
    changedAt: '2026-07-21T00:00:00Z',
  },
  {
    id: 1,
    previousStatus: null,
    newStatus: 'SAVED',
    changedAt: '2026-07-20T00:00:00Z',
  },
]

function response(body: unknown, status = 200): Promise<Response> {
  return Promise.resolve({
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 409 ? 'Conflict' : '',
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response)
}

function requestUrl(input: RequestInfo | URL): string {
  if (typeof input === 'string') {
    return input
  }
  return input instanceof URL ? input.toString() : input.url
}

type FetchOptions = {
  applications?: JobApplication[]
  createConflict?: boolean
}

function setupFetch(options: FetchOptions = {}) {
  let applications = options.applications ?? [tracked()]
  let current = applications[0] ?? tracked()
  let history = INITIAL_HISTORY

  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const url = requestUrl(input)
    const method = init?.method ?? 'GET'

    if (url.endsWith('/api/jobs')) return response([JOB, OTHER_JOB])
    if (url.endsWith('/api/resumes')) return response([RESUME])
    if (url.endsWith('/api/analyses')) return response([ANALYSIS])
    if (url.endsWith('/api/applications') && method === 'GET') return response(applications)
    if (url.endsWith('/api/applications') && method === 'POST') {
      if (options.createConflict) {
        return response({ message: 'Job posting is already tracked.' }, 409)
      }
      current = tracked({ id: 41, status: 'SAVED', appliedAt: null })
      applications = [current, ...applications]
      history = [{
        id: 3,
        previousStatus: null,
        newStatus: 'SAVED',
        changedAt: current.createdAt,
      }]
      return response(current, 201)
    }
    if (url.endsWith(`/api/applications/${current.id}/history`)) return response(history)
    if (url.endsWith(`/api/applications/${current.id}/status`) && method === 'PATCH') {
      const status = JSON.parse(String(init?.body)).status as JobApplication['status']
      const previousStatus = current.status
      current = tracked({ ...current, status, updatedAt: '2026-07-22T00:00:00Z' })
      applications = applications.map((application) => application.id === current.id ? current : application)
      history = [{
        id: 4,
        previousStatus,
        newStatus: status,
        changedAt: '2026-07-22T00:00:00Z',
      }, ...history]
      return response(current)
    }
    if (url.endsWith(`/api/applications/${current.id}`) && method === 'PUT') {
      const input = JSON.parse(String(init?.body)) as { notes: string | null }
      current = tracked({ ...current, notes: input.notes, updatedAt: '2026-07-23T00:00:00Z' })
      applications = applications.map((application) => application.id === current.id ? current : application)
      return response(current)
    }
    if (url.endsWith(`/api/applications/${current.id}`) && method === 'DELETE') {
      applications = applications.filter((application) => application.id !== current.id)
      return response(undefined, 204)
    }
    if (url.endsWith(`/api/applications/${current.id}`) && method === 'GET') return response(current)

    throw new Error(`Unexpected request: ${method} ${url}`)
  })

  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('ApplicationPage', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('renders the tracker, dashboard counts, and status filtering', async () => {
    const user = userEvent.setup()
    setupFetch({
      applications: [
        tracked(),
        tracked({
          id: 42,
          job: { id: OTHER_JOB.id, title: OTHER_JOB.title, company: OTHER_JOB.company },
          status: 'INTERVIEW',
        }),
      ],
    })
    render(<ApplicationPage onNavigate={vi.fn()} />)

    expect(screen.getByText('Loading application tracker…')).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Platform Engineer' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Data Analyst' })).toBeVisible()
    const summary = screen.getByRole('region', { name: 'Application summary' })
    expect(within(summary).getByText('Total').previousElementSibling).toHaveTextContent('2')
    expect(within(summary).getByText('Interview')).toBeVisible()

    await user.selectOptions(screen.getByLabelText('Status'), 'INTERVIEW')

    expect(screen.queryByRole('heading', { name: 'Platform Engineer' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Data Analyst' })).toBeVisible()
  })

  it('renders empty and load-error states', async () => {
    setupFetch({ applications: [] })
    const { unmount } = render(<ApplicationPage onNavigate={vi.fn()} />)
    expect(await screen.findByText('No tracked applications yet.')).toBeVisible()
    unmount()

    vi.stubGlobal('fetch', vi.fn().mockImplementation(() => response(
      { message: 'Workspace unavailable' },
      503,
    )))
    render(<ApplicationPage onNavigate={vi.fn()} />)
    expect(await screen.findByRole('alert')).toHaveTextContent('Workspace unavailable')
  })

  it('creates an application with compatible selections and opens its details', async () => {
    const user = userEvent.setup()
    const fetchMock = setupFetch({ applications: [] })
    render(<ApplicationPage onNavigate={vi.fn()} />)

    await screen.findByText('No tracked applications yet.')
    await user.click(screen.getByRole('button', { name: 'Track application' }))
    await user.selectOptions(screen.getByLabelText('Job posting'), String(JOB.id))
    await user.selectOptions(screen.getByLabelText(/^Resume/), String(RESUME.id))
    expect(screen.getByRole('option', { name: /80% · fake · #30/ })).toBeVisible()
    await user.selectOptions(screen.getByLabelText(/^Analysis/), String(ANALYSIS.id))
    await user.selectOptions(screen.getByLabelText('Initial status'), 'APPLIED')
    await user.type(screen.getByLabelText('Notes Optional'), 'Ready to follow up.')
    await user.click(screen.getByRole('button', { name: 'Track application' }))

    expect(await screen.findByText('Application added to the tracker.')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Application record' })).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/applications',
      expect.objectContaining({
        method: 'POST',
        body: expect.stringContaining('"jobPostingId":10'),
      }),
    )
  })

  it('shows a concise conflict without leaving the create form', async () => {
    const user = userEvent.setup()
    setupFetch({ applications: [], createConflict: true })
    render(<ApplicationPage onNavigate={vi.fn()} />)

    await screen.findByText('No tracked applications yet.')
    await user.click(screen.getByRole('button', { name: 'Track application' }))
    await user.selectOptions(screen.getByLabelText('Job posting'), String(JOB.id))
    await user.click(screen.getByRole('button', { name: 'Track application' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That job is already being tracked.',
    )
    expect(screen.getByRole('heading', { name: 'Track an application' })).toBeVisible()
  })

  it('updates status and refreshes backend history and tracker data', async () => {
    const user = userEvent.setup()
    const fetchMock = setupFetch()
    render(<ApplicationPage onNavigate={vi.fn()} />)

    await user.click(await screen.findByRole('button', { name: 'View Platform Engineer application' }))
    expect(await screen.findByRole('heading', { name: 'Status history' })).toBeVisible()
    expect(await screen.findByText('From Saved')).toBeVisible()
    await user.selectOptions(screen.getByLabelText('Change status'), 'INTERVIEW')
    await user.click(screen.getByRole('button', { name: 'Update status' }))

    expect(await screen.findByText('Application status updated.')).toBeVisible()
    expect(screen.getByText('From Applied')).toBeVisible()
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/applications/40/status',
      expect.objectContaining({ method: 'PATCH' }),
    )
    expect(fetchMock.mock.calls.filter(([input]) => requestUrl(input).endsWith('/history')).length)
      .toBeGreaterThanOrEqual(2)
  })

  it('edits application details without including status or job in the PUT body', async () => {
    const user = userEvent.setup()
    const fetchMock = setupFetch()
    render(<ApplicationPage onNavigate={vi.fn()} />)

    await user.click(await screen.findByRole('button', { name: 'View Platform Engineer application' }))
    await screen.findByRole('heading', { name: 'Application record' })
    await user.click(screen.getByRole('button', { name: 'Edit details' }))
    const notes = screen.getByLabelText('Notes Optional')
    await user.clear(notes)
    await user.type(notes, 'Updated private note.')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByText('Application details updated.')).toBeVisible()
    expect(screen.getByText('Updated private note.')).toBeVisible()
    const putCall = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
    const body = JSON.parse(String(putCall?.[1]?.body)) as Record<string, unknown>
    expect(body).not.toHaveProperty('status')
    expect(body).not.toHaveProperty('jobPostingId')
  })

  it('requires delete confirmation and returns to the tracker after deletion', async () => {
    const user = userEvent.setup()
    const fetchMock = setupFetch()
    const confirm = vi.spyOn(window, 'confirm').mockReturnValueOnce(false).mockReturnValueOnce(true)
    render(<ApplicationPage onNavigate={vi.fn()} />)

    await user.click(await screen.findByRole('button', { name: 'View Platform Engineer application' }))
    await screen.findByRole('heading', { name: 'Application record' })
    await user.click(screen.getByRole('button', { name: 'Delete application' }))
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)

    await user.click(screen.getByRole('button', { name: 'Delete application' }))
    expect(await screen.findByText('Application removed from the tracker.')).toBeVisible()
    expect(screen.getByText('No tracked applications yet.')).toBeVisible()
    expect(confirm).toHaveBeenCalledTimes(2)
  })
})

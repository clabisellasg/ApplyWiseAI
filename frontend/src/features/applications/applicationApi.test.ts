import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createApplication,
  deleteApplication,
  getApplication,
  getApplicationHistory,
  getApplications,
  updateApplication,
  updateApplicationStatus,
} from './applicationApi'
import type { CreateApplicationInput, UpdateApplicationInput } from './applicationTypes'

function response(body: unknown = {}): Promise<Response> {
  return Promise.resolve({
    ok: true,
    status: body === undefined ? 204 : 200,
    statusText: '',
    json: vi.fn().mockResolvedValue(body),
  } as unknown as Response)
}

describe('applicationApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('constructs collection, detail, filter, and history requests', async () => {
    const fetchMock = vi.fn().mockImplementation(() => response([]))
    vi.stubGlobal('fetch', fetchMock)

    await getApplications()
    await getApplications('INTERVIEW')
    await getApplication(12)
    await getApplicationHistory(12)

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      'http://localhost:8080/api/applications',
      expect.objectContaining({}),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      'http://localhost:8080/api/applications?status=INTERVIEW',
      expect.objectContaining({}),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      'http://localhost:8080/api/applications/12',
      expect.objectContaining({}),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      'http://localhost:8080/api/applications/12/history',
      expect.objectContaining({}),
    )
  })

  it('constructs create, update, status, and delete requests', async () => {
    const fetchMock = vi.fn().mockImplementation(() => response({}))
    vi.stubGlobal('fetch', fetchMock)
    const createInput: CreateApplicationInput = {
      jobPostingId: 4,
      resumeId: 2,
      analysisId: 7,
      status: 'APPLIED',
      appliedAt: '2026-07-20',
      nextAction: 'Follow up',
      nextActionAt: '2026-07-25T02:00:00.000Z',
      notes: 'Submitted through company site.',
    }
    const updateInput: UpdateApplicationInput = {
      resumeId: null,
      analysisId: null,
      appliedAt: '2026-07-21',
      nextAction: null,
      nextActionAt: null,
      notes: 'Corrected date.',
    }

    await createApplication(createInput)
    await updateApplication(12, updateInput)
    await updateApplicationStatus(12, { status: 'INTERVIEW' })
    await deleteApplication(12)

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      'http://localhost:8080/api/applications',
      expect.objectContaining({ method: 'POST', body: JSON.stringify(createInput) }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      'http://localhost:8080/api/applications/12',
      expect.objectContaining({ method: 'PUT', body: JSON.stringify(updateInput) }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      'http://localhost:8080/api/applications/12/status',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ status: 'INTERVIEW' }),
      }),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      4,
      'http://localhost:8080/api/applications/12',
      expect.objectContaining({ method: 'DELETE' }),
    )
  })
})

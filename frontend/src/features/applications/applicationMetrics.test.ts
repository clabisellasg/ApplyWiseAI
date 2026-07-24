import { describe, expect, it } from 'vitest'
import { calculateApplicationMetrics } from './applicationMetrics'
import type { ApplicationStatus, JobApplication } from './applicationTypes'

function application(
  id: number,
  status: ApplicationStatus,
  nextActionAt: string | null,
): JobApplication {
  return {
    id,
    job: { id, title: `Role ${id}`, company: 'Fictional Co' },
    resume: null,
    analysis: null,
    status,
    appliedAt: null,
    nextAction: nextActionAt ? 'Follow up' : null,
    nextActionAt,
    notes: null,
    createdAt: '2026-07-20T00:00:00Z',
    updatedAt: '2026-07-20T00:00:00Z',
  }
}

describe('calculateApplicationMetrics', () => {
  it('counts statuses and calendar-day action buckets from loaded applications', () => {
    const metrics = calculateApplicationMetrics([
      application(1, 'SAVED', '2026-07-23T09:00:00+08:00'),
      application(2, 'APPLIED', '2026-07-24T08:00:00+08:00'),
      application(3, 'INTERVIEW', '2026-07-25T09:00:00+08:00'),
      application(4, 'APPLIED', null),
    ], new Date('2026-07-24T12:00:00+08:00'))

    expect(metrics).toEqual({
      total: 4,
      byStatus: {
        SAVED: 1,
        APPLIED: 2,
        INTERVIEW: 1,
        OFFER: 0,
        REJECTED: 0,
        WITHDRAWN: 0,
      },
      overdue: 1,
      dueToday: 1,
      upcoming: 1,
    })
  })

  it('ignores missing and invalid next-action dates', () => {
    const invalid = application(1, 'SAVED', 'not-a-date')
    const missing = application(2, 'WITHDRAWN', null)

    expect(calculateApplicationMetrics([invalid, missing]).overdue).toBe(0)
    expect(calculateApplicationMetrics([invalid, missing]).upcoming).toBe(0)
  })
})

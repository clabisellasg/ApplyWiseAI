import {
  APPLICATION_STATUSES,
  type ApplicationStatus,
  type JobApplication,
} from './applicationTypes'

export type ApplicationMetrics = {
  total: number
  byStatus: Record<ApplicationStatus, number>
  overdue: number
  dueToday: number
  upcoming: number
}

function localDateKey(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function calculateApplicationMetrics(
  applications: JobApplication[],
  now = new Date(),
): ApplicationMetrics {
  const byStatus = Object.fromEntries(
    APPLICATION_STATUSES.map((status) => [status, 0]),
  ) as Record<ApplicationStatus, number>
  const today = localDateKey(now)
  let overdue = 0
  let dueToday = 0
  let upcoming = 0

  for (const application of applications) {
    byStatus[application.status] += 1

    if (!application.nextActionAt) {
      continue
    }

    const actionDate = new Date(application.nextActionAt)
    if (Number.isNaN(actionDate.getTime())) {
      continue
    }

    const actionDay = localDateKey(actionDate)
    if (actionDay < today) {
      overdue += 1
    } else if (actionDay === today) {
      dueToday += 1
    } else {
      upcoming += 1
    }
  }

  return {
    total: applications.length,
    byStatus,
    overdue,
    dueToday,
    upcoming,
  }
}

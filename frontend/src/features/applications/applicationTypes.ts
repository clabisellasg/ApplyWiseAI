export const APPLICATION_STATUSES = [
  'SAVED',
  'APPLIED',
  'INTERVIEW',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
] as const

export type ApplicationStatus = (typeof APPLICATION_STATUSES)[number]

export const APPLICATION_STATUS_LABELS: Record<ApplicationStatus, string> = {
  SAVED: 'Saved',
  APPLIED: 'Applied',
  INTERVIEW: 'Interview',
  OFFER: 'Offer',
  REJECTED: 'Rejected',
  WITHDRAWN: 'Withdrawn',
}

export type JobSummary = {
  id: number
  title: string
  company: string
}

export type ResumeSummary = {
  id: number
  name: string
  targetRole: string | null
}

export type AnalysisSummary = {
  id: number
  score: number
  provider: string
  model: string
}

export type JobApplication = {
  id: number
  job: JobSummary
  resume: ResumeSummary | null
  analysis: AnalysisSummary | null
  status: ApplicationStatus
  appliedAt: string | null
  nextAction: string | null
  nextActionAt: string | null
  notes: string | null
  createdAt: string
  updatedAt: string
}

export type ApplicationStatusHistory = {
  id: number
  previousStatus: ApplicationStatus | null
  newStatus: ApplicationStatus
  changedAt: string
}

export type CreateApplicationInput = {
  jobPostingId: number
  resumeId: number | null
  analysisId: number | null
  status: ApplicationStatus
  appliedAt: string | null
  nextAction: string | null
  nextActionAt: string | null
  notes: string | null
}

export type UpdateApplicationInput = Omit<
  CreateApplicationInput,
  'jobPostingId' | 'status'
>

export type ApplicationStatusInput = {
  status: ApplicationStatus
}

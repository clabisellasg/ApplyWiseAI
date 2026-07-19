import { apiRequest } from '../../api/client'
import type { JobInput, JobPosting } from './jobTypes'

const JOBS_PATH = '/api/jobs'

export function getJobs(signal?: AbortSignal): Promise<JobPosting[]> {
  return apiRequest<JobPosting[]>(JOBS_PATH, { signal })
}

export function createJob(input: JobInput): Promise<JobPosting> {
  return apiRequest<JobPosting>(JOBS_PATH, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateJob(id: number, input: JobInput): Promise<JobPosting> {
  return apiRequest<JobPosting>(`${JOBS_PATH}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function deleteJob(id: number): Promise<void> {
  return apiRequest<void>(`${JOBS_PATH}/${id}`, { method: 'DELETE' })
}

import { apiRequest } from '../../api/client'
import type {
  ApplicationStatus,
  ApplicationStatusHistory,
  ApplicationStatusInput,
  CreateApplicationInput,
  JobApplication,
  UpdateApplicationInput,
} from './applicationTypes'

const APPLICATIONS_PATH = '/api/applications'

export function getApplications(
  status?: ApplicationStatus,
  signal?: AbortSignal,
): Promise<JobApplication[]> {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return apiRequest<JobApplication[]>(`${APPLICATIONS_PATH}${query}`, { signal })
}

export function getApplication(id: number, signal?: AbortSignal): Promise<JobApplication> {
  return apiRequest<JobApplication>(`${APPLICATIONS_PATH}/${id}`, { signal })
}

export function createApplication(input: CreateApplicationInput): Promise<JobApplication> {
  return apiRequest<JobApplication>(APPLICATIONS_PATH, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateApplication(
  id: number,
  input: UpdateApplicationInput,
): Promise<JobApplication> {
  return apiRequest<JobApplication>(`${APPLICATIONS_PATH}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function updateApplicationStatus(
  id: number,
  input: ApplicationStatusInput,
): Promise<JobApplication> {
  return apiRequest<JobApplication>(`${APPLICATIONS_PATH}/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify(input),
  })
}

export function getApplicationHistory(
  id: number,
  signal?: AbortSignal,
): Promise<ApplicationStatusHistory[]> {
  return apiRequest<ApplicationStatusHistory[]>(
    `${APPLICATIONS_PATH}/${id}/history`,
    { signal },
  )
}

export function deleteApplication(id: number): Promise<void> {
  return apiRequest<void>(`${APPLICATIONS_PATH}/${id}`, { method: 'DELETE' })
}

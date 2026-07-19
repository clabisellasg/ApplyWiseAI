import { apiRequest } from '../../api/client'
import type { Resume, ResumeInput } from './resumeTypes'

const RESUMES_PATH = '/api/resumes'

export function getResumes(signal?: AbortSignal): Promise<Resume[]> {
  return apiRequest<Resume[]>(RESUMES_PATH, { signal })
}

export function createResume(input: ResumeInput): Promise<Resume> {
  return apiRequest<Resume>(RESUMES_PATH, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

export function updateResume(id: number, input: ResumeInput): Promise<Resume> {
  return apiRequest<Resume>(`${RESUMES_PATH}/${id}`, {
    method: 'PUT',
    body: JSON.stringify(input),
  })
}

export function deleteResume(id: number): Promise<void> {
  return apiRequest<void>(`${RESUMES_PATH}/${id}`, { method: 'DELETE' })
}

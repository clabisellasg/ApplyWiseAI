import { apiRequest } from '../../api/client'
import type { Analysis, CreateAnalysisInput } from './analysisTypes'

const ANALYSES_PATH = '/api/analyses'

export function getAnalyses(signal?: AbortSignal): Promise<Analysis[]> {
  return apiRequest<Analysis[]>(ANALYSES_PATH, { signal })
}

export function getAnalysis(id: number, signal?: AbortSignal): Promise<Analysis> {
  return apiRequest<Analysis>(`${ANALYSES_PATH}/${id}`, { signal })
}

export function createAnalysis(input: CreateAnalysisInput): Promise<Analysis> {
  return apiRequest<Analysis>(ANALYSES_PATH, {
    method: 'POST',
    body: JSON.stringify(input),
  })
}

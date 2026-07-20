export type MatchStatus = 'MATCHED' | 'PARTIAL' | 'MISSING' | 'UNKNOWN'

export type SkillAssessment = {
  name: string
  status: MatchStatus
  resumeEvidence: string | null
  explanation: string
}

export type AnalysisResult = {
  matchScore: number
  summary: string
  skills: SkillAssessment[]
  strengths: string[]
  gaps: string[]
  recommendedActions: string[]
}

export type Analysis = {
  id: number
  resumeId: number
  jobPostingId: number
  matchScore: number
  summary: string
  result: AnalysisResult
  provider: string
  model: string
  promptVersion: string
  createdAt: string
}

export type CreateAnalysisInput = {
  resumeId: number
  jobPostingId: number
}

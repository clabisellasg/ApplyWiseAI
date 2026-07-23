import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { AnalysisResultView } from './AnalysisResultView'
import type { Analysis } from './analysisTypes'

const ANALYSIS: Analysis = {
  id: 7,
  resumeId: 1,
  jobPostingId: 2,
  matchScore: 63,
  summary: 'A deterministic comparison summary.',
  result: {
    matchScore: 63,
    summary: 'A deterministic comparison summary.',
    skills: [
      {
        name: 'Java',
        status: 'MATCHED',
        resumeEvidence: 'Built Java services.',
        explanation: 'Both texts mention Java.',
      },
      {
        name: 'TypeScript',
        status: 'PARTIAL',
        resumeEvidence: 'Developed JavaScript applications.',
        explanation: 'Related JavaScript evidence was found.',
      },
      {
        name: 'Docker',
        status: 'MISSING',
        resumeEvidence: null,
        explanation: 'No Docker evidence was found.',
      },
      {
        name: 'Networking',
        status: 'UNKNOWN',
        resumeEvidence: null,
        explanation: 'The available evidence is inconclusive.',
      },
    ],
    strengths: ['Java is supported by resume evidence.'],
    gaps: ['Docker is not evidenced in the resume.'],
    recommendedActions: ['Document Docker experience only if it is truthful.'],
  },
  provider: 'fake',
  model: 'keyword-matcher-v1',
  promptVersion: 'v1',
  createdAt: '2026-02-01T10:15:30Z',
  cacheHit: false,
}

describe('AnalysisResultView', () => {
  it('renders every MatchStatus group with evidence and explanations', () => {
    render(
      <AnalysisResultView
        analysis={ANALYSIS}
        resumeLabel="Primary Resume"
        jobLabel="Backend Engineer at Genesis"
      />,
    )

    expect(screen.getByText('63')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Matched skills' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Partial skills' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Missing skills' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Unknown skills' })).toBeVisible()
    expect(screen.getByText('Built Java services.')).toBeVisible()
    expect(screen.getByText('Related JavaScript evidence was found.')).toBeVisible()
    expect(screen.getAllByText('No supporting resume evidence found.')).toHaveLength(2)
    expect(screen.getByText('This result comes from a fixed keyword matcher, not generative AI.')).toBeVisible()
  })

  it('labels a reused NVIDIA result without calling it a browser cache', () => {
    render(
      <AnalysisResultView
        analysis={{ ...ANALYSIS, provider: 'nvidia', cacheHit: true }}
        resumeLabel="Primary Resume"
        jobLabel="Backend Engineer at Genesis"
      />,
    )

    expect(screen.getByText('Previously analyzed result')).toBeVisible()
    expect(screen.getByText(/No additional provider request was needed/)).toBeVisible()
    expect(screen.getByText('NVIDIA hosted analysis')).toBeVisible()
    expect(screen.queryByText(/browser cache/i)).not.toBeInTheDocument()
  })
})

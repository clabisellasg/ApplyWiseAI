import { useEffect, useRef, useState } from 'react'
import { getErrorMessage } from '../../api/client'
import { FeedbackBanner } from '../../components/FeedbackBanner'
import { getJobs } from '../jobs/jobApi'
import type { JobPosting } from '../jobs/jobTypes'
import { getResumes } from '../resumes/resumeApi'
import type { Resume } from '../resumes/resumeTypes'
import { AnalysisForm } from './AnalysisForm'
import { AnalysisHistory } from './AnalysisHistory'
import { AnalysisResultView } from './AnalysisResultView'
import { createAnalysis, getAnalyses, getAnalysis } from './analysisApi'
import type { Analysis, CreateAnalysisInput } from './analysisTypes'

type AnalysisPageProps = {
  onNavigate: (section: 'jobs' | 'resumes') => void
}

function newestFirst(analyses: Analysis[]): Analysis[] {
  return [...analyses].sort(
    (first, second) => new Date(second.createdAt).getTime() - new Date(first.createdAt).getTime(),
  )
}

export function AnalysisPage({ onNavigate }: AnalysisPageProps) {
  const [resumes, setResumes] = useState<Resume[]>([])
  const [jobs, setJobs] = useState<JobPosting[]>([])
  const [analyses, setAnalyses] = useState<Analysis[]>([])
  const [currentAnalysis, setCurrentAnalysis] = useState<Analysis | null>(null)
  const [isSourcesLoading, setIsSourcesLoading] = useState(true)
  const [isHistoryLoading, setIsHistoryLoading] = useState(true)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [openingId, setOpeningId] = useState<number | null>(null)
  const [sourceError, setSourceError] = useState<string | null>(null)
  const [historyError, setHistoryError] = useState<string | null>(null)
  const [submissionError, setSubmissionError] = useState<string | null>(null)
  const [successMessage, setSuccessMessage] = useState<string | null>(null)
  const submissionLock = useRef(false)

  useEffect(() => {
    const controller = new AbortController()

    async function loadSources() {
      const [resumeResult, jobResult] = await Promise.allSettled([
        getResumes(controller.signal),
        getJobs(controller.signal),
      ])

      if (controller.signal.aborted) {
        return
      }

      const errors: string[] = []
      if (resumeResult.status === 'fulfilled') {
        setResumes(resumeResult.value)
      } else {
        errors.push(`Resumes: ${getErrorMessage(resumeResult.reason)}`)
      }
      if (jobResult.status === 'fulfilled') {
        setJobs(jobResult.value)
      } else {
        errors.push(`Jobs: ${getErrorMessage(jobResult.reason)}`)
      }

      setSourceError(errors.length > 0 ? errors.join(' ') : null)
      setIsSourcesLoading(false)
    }

    async function loadHistory() {
      try {
        setAnalyses(newestFirst(await getAnalyses(controller.signal)))
      } catch (error) {
        if (!controller.signal.aborted) {
          setHistoryError(getErrorMessage(error))
        }
      } finally {
        if (!controller.signal.aborted) {
          setIsHistoryLoading(false)
        }
      }
    }

    void loadSources()
    void loadHistory()
    return () => controller.abort()
  }, [])

  async function retrySources() {
    setIsSourcesLoading(true)
    setSourceError(null)

    const [resumeResult, jobResult] = await Promise.allSettled([getResumes(), getJobs()])
    const errors: string[] = []

    if (resumeResult.status === 'fulfilled') {
      setResumes(resumeResult.value)
    } else {
      errors.push(`Resumes: ${getErrorMessage(resumeResult.reason)}`)
    }
    if (jobResult.status === 'fulfilled') {
      setJobs(jobResult.value)
    } else {
      errors.push(`Jobs: ${getErrorMessage(jobResult.reason)}`)
    }

    setSourceError(errors.length > 0 ? errors.join(' ') : null)
    setIsSourcesLoading(false)
  }

  async function retryHistory() {
    setIsHistoryLoading(true)
    setHistoryError(null)

    try {
      setAnalyses(newestFirst(await getAnalyses()))
    } catch (error) {
      setHistoryError(getErrorMessage(error))
    } finally {
      setIsHistoryLoading(false)
    }
  }

  async function handleSubmit(input: CreateAnalysisInput) {
    if (submissionLock.current) {
      return
    }

    submissionLock.current = true
    setIsSubmitting(true)
    setSubmissionError(null)
    setSuccessMessage(null)

    try {
      const created = await createAnalysis(input)
      setCurrentAnalysis(created)
      setAnalyses((current) => newestFirst([
        created,
        ...current.filter((analysis) => analysis.id !== created.id),
      ]))
      setSubmissionError(null)
      setSuccessMessage('Analysis completed and saved to history.')
    } catch (error) {
      setSubmissionError(getErrorMessage(error))
      throw error
    } finally {
      submissionLock.current = false
      setIsSubmitting(false)
    }
  }

  async function handleHistorySelect(id: number) {
    if (openingId !== null || currentAnalysis?.id === id) {
      return
    }

    setOpeningId(id)
    setHistoryError(null)
    setSuccessMessage(null)

    try {
      setCurrentAnalysis(await getAnalysis(id))
    } catch (error) {
      setHistoryError(getErrorMessage(error))
    } finally {
      setOpeningId(null)
    }
  }

  function resumeLabel(resumeId: number): string {
    const resume = resumes.find((candidate) => candidate.id === resumeId)
    return resume ? resume.name : `Resume #${resumeId}`
  }

  function jobLabel(jobPostingId: number): string {
    const job = jobs.find((candidate) => candidate.id === jobPostingId)
    return job ? `${job.title} at ${job.company}` : `Job posting #${jobPostingId}`
  }

  return (
    <section className="analysis-page" aria-labelledby="analysis-page-heading">
      <header className="analysis-page__intro">
        <p className="eyebrow">Deterministic comparison</p>
        <h2 id="analysis-page-heading">Job match analysis</h2>
        <p>
          Select saved source material, run the local keyword matcher, and revisit every stored
          result without sending your data to an external AI provider.
        </p>
      </header>

      <div className="analysis-layout">
        <div className="analysis-sidebar">
          <AnalysisForm
            isLoading={isSourcesLoading}
            isSubmitting={isSubmitting}
            jobs={jobs}
            loadError={sourceError}
            resumes={resumes}
            submissionError={submissionError}
            onNavigate={onNavigate}
            onRetry={() => void retrySources()}
            onSubmit={handleSubmit}
          />
          <AnalysisHistory
            analyses={analyses}
            currentId={currentAnalysis?.id ?? null}
            error={historyError}
            isLoading={isHistoryLoading}
            jobs={jobs}
            resumes={resumes}
            openingId={openingId}
            onRetry={() => void retryHistory()}
            onSelect={(id) => void handleHistorySelect(id)}
          />
        </div>

        <div className="analysis-result-column">
          {successMessage && <FeedbackBanner tone="success">{successMessage}</FeedbackBanner>}
          {currentAnalysis ? (
            <AnalysisResultView
              analysis={currentAnalysis}
              resumeLabel={resumeLabel(currentAnalysis.resumeId)}
              jobLabel={jobLabel(currentAnalysis.jobPostingId)}
            />
          ) : (
            <div className="analysis-placeholder">
              <span className="analysis-placeholder__score" aria-hidden="true">—</span>
              <p className="eyebrow">Ready when you are</p>
              <h2>Select two saved records to see their match.</h2>
              <p>
                The complete score, evidence, skill groups, gaps, and preparation actions will
                appear here.
              </p>
            </div>
          )}
        </div>
      </div>
    </section>
  )
}

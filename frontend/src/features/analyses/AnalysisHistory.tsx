import { FeedbackBanner } from '../../components/FeedbackBanner'
import type { JobPosting } from '../jobs/jobTypes'
import type { Resume } from '../resumes/resumeTypes'
import type { Analysis } from './analysisTypes'

type AnalysisHistoryProps = {
  analyses: Analysis[]
  currentId: number | null
  error: string | null
  isLoading: boolean
  jobs: JobPosting[]
  onRetry: () => void
  onSelect: (id: number) => void
  openingId: number | null
  resumes: Resume[]
}

function formattedDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return 'Unknown date'
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date)
}

export function AnalysisHistory({
  analyses,
  currentId,
  error,
  isLoading,
  jobs,
  onRetry,
  onSelect,
  openingId,
  resumes,
}: AnalysisHistoryProps) {
  function resumeLabel(resumeId: number): string {
    const resume = resumes.find((candidate) => candidate.id === resumeId)
    return resume ? resume.name : `Resume #${resumeId}`
  }

  function jobLabel(jobPostingId: number): string {
    const job = jobs.find((candidate) => candidate.id === jobPostingId)
    return job ? `${job.title} at ${job.company}` : `Job posting #${jobPostingId}`
  }

  return (
    <section className="analysis-history" aria-labelledby="analysis-history-heading">
      <div className="list-heading">
        <div>
          <p className="eyebrow">Saved results</p>
          <h2 id="analysis-history-heading">Analysis history</h2>
        </div>
        <span className="count-badge">{analyses.length}</span>
      </div>

      {isLoading && <FeedbackBanner tone="info">Loading analysis history…</FeedbackBanner>}
      {error && (
        <FeedbackBanner tone="error">
          <span>{error}</span>
          <button className="banner-button" type="button" onClick={onRetry}>
            Try again
          </button>
        </FeedbackBanner>
      )}

      {!isLoading && !error && analyses.length === 0 && (
        <div className="empty-state empty-state--compact">
          <p className="empty-state__title">No analyses yet</p>
          <p>Your completed comparisons will appear here.</p>
        </div>
      )}

      <div className="history-list">
        {analyses.map((analysis) => {
          const isCurrent = currentId === analysis.id
          const isOpening = openingId === analysis.id
          const resume = resumeLabel(analysis.resumeId)
          const job = jobLabel(analysis.jobPostingId)

          return (
            <button
              key={analysis.id}
              className={`history-item${isCurrent ? ' history-item--current' : ''}`}
              type="button"
              aria-pressed={isCurrent}
              onClick={() => onSelect(analysis.id)}
              disabled={openingId !== null}
            >
              <span className="history-item__score">{analysis.matchScore}</span>
              <span className="history-item__content">
                <strong>{resume}</strong>
                <span>{job}</span>
                <small>{isOpening ? 'Opening…' : formattedDate(analysis.createdAt)}</small>
              </span>
              {isCurrent && <span className="history-item__current">Open</span>}
            </button>
          )
        })}
      </div>
    </section>
  )
}

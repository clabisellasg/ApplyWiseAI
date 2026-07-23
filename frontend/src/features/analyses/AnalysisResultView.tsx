import { MatchScore } from './MatchScore'
import type { Analysis, MatchStatus } from './analysisTypes'

type AnalysisResultViewProps = {
  analysis: Analysis
  jobLabel: string
  resumeLabel: string
}

const STATUS_ORDER: MatchStatus[] = ['MATCHED', 'PARTIAL', 'MISSING', 'UNKNOWN']

const STATUS_LABELS: Record<MatchStatus, string> = {
  MATCHED: 'Matched skills',
  PARTIAL: 'Partial skills',
  MISSING: 'Missing skills',
  UNKNOWN: 'Unknown skills',
}

function formattedDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return 'Unknown date'
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'long',
    timeStyle: 'short',
  }).format(date)
}

export function AnalysisResultView({ analysis, jobLabel, resumeLabel }: AnalysisResultViewProps) {
  const { result } = analysis
  const isFakeProvider = analysis.provider === 'fake'

  return (
    <article className="analysis-result" aria-labelledby="analysis-result-heading">
      <header className="analysis-result__header">
        <div>
          <p className="eyebrow">Job match result</p>
          <h2 id="analysis-result-heading">{resumeLabel}</h2>
          <p className="analysis-result__job">Compared with {jobLabel}</p>
        </div>
        <span className="analysis-date">Created {formattedDate(analysis.createdAt)}</span>
      </header>

      {analysis.cacheHit && (
        <div className="cache-hit-note" role="status">
          <strong>Previously analyzed result</strong>
          <span>No additional provider request was needed for these identical inputs.</span>
        </div>
      )}

      <div className="local-analyzer-note">
        <strong>{isFakeProvider ? 'Deterministic local analysis' : 'NVIDIA hosted analysis'}</strong>
        <span>
          {isFakeProvider
            ? 'This result comes from a fixed keyword matcher, not generative AI.'
            : 'This result passed server-side structure and resume-evidence validation.'}
        </span>
      </div>

      <MatchScore score={analysis.matchScore} />

      <section className="result-section" aria-labelledby="analysis-summary-heading">
        <h3 id="analysis-summary-heading">Summary</h3>
        <p>{analysis.summary}</p>
      </section>

      <div className="skill-groups">
        {STATUS_ORDER.map((status) => {
          const skills = result.skills.filter((skill) => skill.status === status)
          if (skills.length === 0) {
            return null
          }

          return (
            <section
              key={status}
              className="skill-group"
              aria-labelledby={`skill-group-${status.toLowerCase()}`}
            >
              <div className="skill-group__heading">
                <h3 id={`skill-group-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</h3>
                <span className={`status-label status-label--${status.toLowerCase()}`}>
                  {status}
                </span>
              </div>
              <div className="skill-list">
                {skills.map((skill) => (
                  <article className="skill-card" key={`${status}-${skill.name}`}>
                    <h4>{skill.name}</h4>
                    <p>{skill.explanation}</p>
                    <div className="evidence-block">
                      <strong>Resume evidence</strong>
                      <p>{skill.resumeEvidence || 'No supporting resume evidence found.'}</p>
                    </div>
                  </article>
                ))}
              </div>
            </section>
          )
        })}

        {result.skills.length === 0 && (
          <div className="empty-state empty-state--compact">
            <p className="empty-state__title">No recognized technical skills</p>
            <p>The local analyzer could not identify a supported keyword in this job description.</p>
          </div>
        )}
      </div>

      <div className="result-lists">
        <section className="result-section">
          <h3>Strengths</h3>
          {result.strengths.length > 0 ? (
            <ul>{result.strengths.map((strength) => <li key={strength}>{strength}</li>)}</ul>
          ) : (
            <p>No matched strengths were identified.</p>
          )}
        </section>

        <section className="result-section">
          <h3>Gaps</h3>
          {result.gaps.length > 0 ? (
            <ul>{result.gaps.map((gap) => <li key={gap}>{gap}</li>)}</ul>
          ) : (
            <p>No keyword gaps were identified.</p>
          )}
        </section>

        <section className="result-section result-section--wide">
          <h3>Recommended actions</h3>
          {result.recommendedActions.length > 0 ? (
            <ol>{result.recommendedActions.map((action) => <li key={action}>{action}</li>)}</ol>
          ) : (
            <p>No additional actions were generated.</p>
          )}
        </section>
      </div>

      <details className="technical-details">
        <summary>Technical details</summary>
        <dl>
          <div><dt>Provider</dt><dd>{analysis.provider}</dd></div>
          <div><dt>Model</dt><dd>{analysis.model}</dd></div>
          <div><dt>Prompt version</dt><dd>{analysis.promptVersion}</dd></div>
        </dl>
      </details>
    </article>
  )
}

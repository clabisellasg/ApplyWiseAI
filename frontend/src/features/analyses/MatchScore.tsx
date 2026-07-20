type MatchScoreProps = {
  score: number
}

function scoreLabel(score: number): string {
  if (score >= 75) {
    return 'Strong keyword overlap'
  }
  if (score >= 50) {
    return 'Some keyword overlap'
  }
  return 'Limited keyword overlap'
}

function scoreTone(score: number): string {
  if (score >= 75) {
    return 'high'
  }
  if (score >= 50) {
    return 'medium'
  }
  return 'low'
}

export function MatchScore({ score }: MatchScoreProps) {
  const safeScore = Math.min(100, Math.max(0, score))

  return (
    <section className={`match-score match-score--${scoreTone(safeScore)}`} aria-label="Match score">
      <div className="match-score__value">
        <strong>{safeScore}</strong>
        <span>/ 100</span>
      </div>
      <div className="match-score__details">
        <p>{scoreLabel(safeScore)}</p>
        <progress max="100" value={safeScore} aria-label={`${safeScore} out of 100`} />
      </div>
    </section>
  )
}

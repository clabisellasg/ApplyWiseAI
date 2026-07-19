import type { ReactNode } from 'react'

type FeedbackBannerProps = {
  children: ReactNode
  tone: 'error' | 'info' | 'success'
}

export function FeedbackBanner({ children, tone }: FeedbackBannerProps) {
  return (
    <div
      className={`feedback-banner feedback-banner--${tone}`}
      role={tone === 'error' ? 'alert' : 'status'}
    >
      {children}
    </div>
  )
}

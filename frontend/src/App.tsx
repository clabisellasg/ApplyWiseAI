import { useEffect, useState } from 'react'
import { apiRequest } from './api/client'
import { JobsSection } from './features/jobs/JobsSection'
import { ResumesSection } from './features/resumes/ResumesSection'

type HealthResponse = {
  status: string
}

type ConnectionState = 'loading' | 'online' | 'error'
type ActiveSection = 'jobs' | 'resumes'

export function App() {
  const [connectionState, setConnectionState] = useState<ConnectionState>('loading')
  const [activeSection, setActiveSection] = useState<ActiveSection>('jobs')

  useEffect(() => {
    const controller = new AbortController()

    async function checkBackend() {
      try {
        const health = await apiRequest<HealthResponse>('/api/health', {
          signal: controller.signal,
        })
        setConnectionState(health.status === 'UP' ? 'online' : 'error')
      } catch (error) {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setConnectionState('error')
        }
      }
    }

    void checkBackend()
    return () => controller.abort()
  }, [])

  return (
    <div className="app-shell">
      <header className="site-header">
        <div className="site-header__inner">
          <a className="brand" href="#workspace" aria-label="ApplyWise AI home">
            <span className="brand-mark" aria-hidden="true">A</span>
            <span>ApplyWise AI</span>
          </a>

          <div
            className={`backend-pill backend-pill--${connectionState}`}
            role="status"
            aria-live="polite"
          >
            <span className="status-dot" aria-hidden="true" />
            {connectionState === 'loading' && 'Checking backend'}
            {connectionState === 'online' && 'Backend online'}
            {connectionState === 'error' && 'Backend offline'}
          </div>
        </div>
      </header>

      <main id="workspace">
        <section className="hero" aria-labelledby="page-title">
          <p className="eyebrow">Application workspace</p>
          <h1 id="page-title">Keep the evidence for your next opportunity in one place.</h1>
          <p>
            Save job postings and text resumes now. ApplyWise will use these records as the
            grounded source for future compatibility analysis.
          </p>
        </section>

        <nav className="section-tabs" aria-label="Workspace sections" role="tablist">
          <button
            id="jobs-tab"
            className="section-tab"
            type="button"
            role="tab"
            aria-controls="jobs-panel"
            aria-selected={activeSection === 'jobs'}
            onClick={() => setActiveSection('jobs')}
          >
            Job postings
          </button>
          <button
            id="resumes-tab"
            className="section-tab"
            type="button"
            role="tab"
            aria-controls="resumes-panel"
            aria-selected={activeSection === 'resumes'}
            onClick={() => setActiveSection('resumes')}
          >
            Resumes
          </button>
        </nav>

        <div className="workspace-panel">
          {activeSection === 'jobs' && (
            <div id="jobs-panel" role="tabpanel" aria-labelledby="jobs-tab" tabIndex={0}>
              <JobsSection />
            </div>
          )}
          {activeSection === 'resumes' && (
            <div id="resumes-panel" role="tabpanel" aria-labelledby="resumes-tab" tabIndex={0}>
              <ResumesSection />
            </div>
          )}
        </div>
      </main>

      <footer className="site-footer">
        <p>Milestone 1 · Persistent job and resume workspace</p>
      </footer>
    </div>
  )
}

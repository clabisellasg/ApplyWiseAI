import { useEffect, useState } from 'react'

type HealthResponse = {
  status: string
}

type ConnectionState = 'loading' | 'online' | 'error'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

function App() {
  const [connectionState, setConnectionState] = useState<ConnectionState>('loading')

  useEffect(() => {
    const controller = new AbortController()

    async function checkBackend() {
      try {
        const response = await fetch(`${API_BASE_URL}/api/health`, {
          signal: controller.signal,
        })

        if (!response.ok) {
          throw new Error(`Health check failed with status ${response.status}`)
        }

        const health: HealthResponse = await response.json()
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
    <main className="page-shell">
      <section className="status-card" aria-labelledby="page-title">
        <p className="eyebrow">Application readiness</p>
        <h1 id="page-title">ApplyWise AI</h1>
        <p className="intro">
          Your focused workspace for building stronger, evidence-based job applications.
        </p>

        <div className={`backend-status backend-status--${connectionState}`} role="status" aria-live="polite">
          <span className="status-dot" aria-hidden="true" />
          <div>
            <p className="status-label">Backend connection</p>
            {connectionState === 'loading' && <p className="status-message">Checking availability…</p>}
            {connectionState === 'online' && <p className="status-message">Online and ready</p>}
            {connectionState === 'error' && (
              <p className="status-message">
                Offline. Start the backend and refresh this page.
              </p>
            )}
          </div>
        </div>

        <p className="milestone-note">Milestone 0 · Walking skeleton</p>
      </section>
    </main>
  )
}

export default App


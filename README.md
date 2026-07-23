# ApplyWise AI

ApplyWise AI is a modular monolith with a Spring Boot backend and a React frontend. It stores job postings and text resumes in PostgreSQL and can compare them using either the default deterministic local keyword analyzer or NVIDIA-hosted Nemotron.

## Prerequisites

Install these tools before starting:

- Java 21
- Docker Desktop with Docker Compose
- Node.js 20 and npm

No global Maven installation is required because the repository includes the Maven wrapper.

## Run locally on Windows PowerShell

Open PowerShell in the repository root and start PostgreSQL:

```powershell
docker compose up -d
```

Open a second PowerShell window in the repository root and start the backend:

```powershell
Set-Location .\backend
.\mvnw.cmd spring-boot:run
```

Open a third PowerShell window in the repository root, install the frontend dependencies, and start Vite:

```powershell
Set-Location .\frontend
npm install
npm run dev
```

Open `http://localhost:5173`. The page checks `http://localhost:8080/api/health` and reports whether the backend is online.

## Run a fake job-match analysis

1. Open the **Job postings** section and save a job description.
2. Open the **Resumes** section and save a text resume.
3. Open **Job match**, select the saved resume and job posting, and choose **Analyze match**.
4. Review the score, skill evidence, strengths, gaps, and recommended actions. Saved results remain available in analysis history.

Fake mode uses a deterministic local keyword matcher, makes no external AI request, and requires no API key. Its result is a keyword comparison rather than generative AI output.

## NVIDIA Nemotron analysis

NVIDIA mode sends the selected resume content and job description to NVIDIA's hosted API at `https://integrate.api.nvidia.com/v1/chat/completions`. An NVIDIA API key is required. The hosted endpoint is currently a free development/trial service and may have rate or availability limits. Use synthetic or redacted resumes during development; do not send personal data unless you have reviewed NVIDIA's applicable terms and privacy practices.

The backend requests non-streaming output from `nvidia/nemotron-3-super-120b-a12b` with extended reasoning disabled. Structured output uses NVIDIA NIM guided JSON through `nvext.guided_json`, followed by strict Java validation before an analysis can be saved. Responses are read from `choices[0].message.content`; refusal and token-limit finish metadata are checked before parsing. A single complete Markdown-fenced JSON object is safely unwrapped, but commentary, malformed JSON, schema mismatches, and truncated output are rejected. The provider is instructed not to invent qualifications, and any returned resume evidence must match text in the submitted resume.

Fake remains the default. To run the backend in NVIDIA mode from a new PowerShell window, set the variables only in that process and then start Spring Boot:

```powershell
$env:AI_PROVIDER = 'nvidia'
$env:NVIDIA_API_KEY = '<your NVIDIA API key>'
$env:NVIDIA_BASE_URL = 'https://integrate.api.nvidia.com/v1'
$env:NVIDIA_MODEL = 'nvidia/nemotron-3-super-120b-a12b'
$env:NVIDIA_TIMEOUT_SECONDS = '120'
$env:NVIDIA_MAX_TOKENS = '4096'
$env:NVIDIA_MAX_INPUT_CHARACTERS = '50000'
Set-Location .\backend
.\mvnw.cmd spring-boot:run
```

If `AI_PROVIDER=nvidia` and `NVIDIA_API_KEY` is missing, startup fails clearly. Provider errors never fall back to a fake result. Automated tests mock the NVIDIA HTTP boundary, never contact NVIDIA, and consume no API quota.

Completed analyses are fingerprinted from the resume text, job description, provider, model, and prompt version. Identical requests reuse the stored result without another provider call; failed or invalid responses are never cached. NVIDIA results are also rejected unless matched and partial evidence can be grounded directly in the supplied resume.

Configuration variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `AI_PROVIDER` | `fake` | Selects `fake` or `nvidia`. |
| `NVIDIA_API_KEY` | none | Bearer token; required only for NVIDIA mode. |
| `NVIDIA_BASE_URL` | `https://integrate.api.nvidia.com/v1` | NVIDIA API base URL. |
| `NVIDIA_MODEL` | `nvidia/nemotron-3-super-120b-a12b` | Exact hosted model identifier saved with each analysis. |
| `NVIDIA_TIMEOUT_SECONDS` | `120` | Connect and response timeout. |
| `NVIDIA_MAX_TOKENS` | `4096` | Maximum generated output tokens; responses ending with a token-limit finish reason are rejected as truncated. |
| `NVIDIA_MAX_INPUT_CHARACTERS` | `50000` | Per-document input limit; oversized text is rejected, not truncated. |
| `RUN_LIVE_AI_EVALS` | `false` | Explicit safety switch required by the opt-in live evaluation profile. |

The root `.env.example` contains placeholders for local reference. Spring Boot does not automatically import that file when launched directly, so set variables in PowerShell or through your IDE. Real `.env` files and `application-local` configuration files are ignored and must never be committed.

The synthetic evaluation suite, pass criteria, limitations, and opt-in live command are documented in [`docs/EVALUATION.md`](docs/EVALUATION.md). Normal verification never runs the live profile.

To stop PostgreSQL, return to the repository root and run:

```powershell
docker compose down
```

## Verify the project

Run the backend tests from the repository root:

```powershell
Set-Location .\backend
.\mvnw.cmd test
```

Run the frontend production build from the repository root:

```powershell
Set-Location .\frontend
npm install
npm run build
```

## Local database and frontend configuration

The default database values are intended only for local development. Override them with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` when needed. The frontend can override the backend address with `VITE_API_BASE_URL`.

# ApplyWise AI

ApplyWise AI is a modular monolith with a Spring Boot backend and a React frontend. Milestone 0 provides a walking skeleton that verifies browser-to-backend connectivity and includes PostgreSQL for local development.

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

## Local configuration

The default database values are intended only for local development. Override them with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` when needed. The frontend can override the backend address with `VITE_API_BASE_URL`.


# ApplyWise AI Project Instructions

## Project and stack

- Project name: ApplyWise AI.
- Use Java 21, Spring Boot, Maven, React, TypeScript, Vite, and PostgreSQL.
- Structure the application as a modular monolith with separate `backend` and `frontend` folders.

## Backend organization

- Organize backend code by feature: `job`, `resume`, `analysis`, `application`, `ai`, `config`, and `common`.
- Each feature may contain its own controller, service, repository, entity, and DTOs.
- Controllers handle HTTP requests and responses only.
- Services contain business logic.
- Repositories handle database access.
- Never return JPA entities directly from controllers; use DTOs.
- Use constructor dependency injection.
- Do not use Lombok.

## Delivery rules

- Never commit secrets, API keys, generated build files, or environment files.
- Implement only the requested milestone.
- Preserve existing working behavior.
- Run relevant tests after every implementation.
- Before finishing, review the diff for bugs and unnecessary changes.
- Keep final reports concise: list changed files, tests run, and remaining issues.

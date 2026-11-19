# Project Overview
**Attractions** ("Atrakcje") is one microservice of a horizontally scalable trip-planning system. It owns the attraction catalog and, on request from the **Planner** ("Planer i integracja"), returns attraction proposals for a given day, enriched with weather fit (from the **Day Conditions** service, "Warunki dnia") and travel time/cost (from the **Transport** service). The Planner — not this service — chooses which attractions go into the plan.

**`architecture.md` is the source of truth** for the API contract, business rules, error format, data model and configuration. Read the relevant section before implementing or changing behavior, and update it in the same change if the contract changes.

## Tech stack
* Java 25
* Spring Boot 4.1.x (Web MVC, Validation, Data JPA, Security, Actuator)
* Jakarta Persistence (JPA / Hibernate)
* PostgreSQL, schema managed by Flyway
* Maven (wrapper `./mvnw`)
* Tests: JUnit 5, Mockito, Testcontainers (PostgreSQL), WireMock for downstream services

## Package structure
Layered architecture under `src/main/java/pl/psso/attractions`:

```
pl.psso.attractions
├── api          # *Controller, request/response DTOs (records), @RestControllerAdvice
├── service      # *Service — business logic (ProposalService, AttractionService)
├── domain       # JPA @Entity classes
├── repository   # *Repository (Spring Data JPA)
├── client       # WeatherClient, TransportClient (+ mock implementations)
└── config       # Security, HTTP clients, properties (@ConfigurationProperties)
```

# Build & Test Commands
- Build and run all tests: `./mvnw verify`
- Unit tests only: `./mvnw test`
- Single test class: `./mvnw test -Dtest=ProposalServiceTest`
- Single integration test (Failsafe): `./mvnw verify -Dit.test=ProposalControllerIT`
- Run locally with mocked dependencies: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local,mock`

Integration tests need a running Docker daemon (Testcontainers).

# Architecture & Conventions
- **Three layers**: controller (`*Controller.java`) handles HTTP, service (`*Service.java`) holds business logic, repository (`*Repository.java`) accesses the database. Controllers never call repositories or clients directly.
- Controllers only do HTTP mapping, validation (`@Valid`, Jakarta Validation constraints) and DTO mapping; no business logic and no `@Transactional` in controllers.
- Use constructor injection with `final` fields; no field injection (`@Autowired` on fields).
- Use `record` DTOs for requests and responses; never expose JPA `@Entity` classes in API responses.
- Keep `@Transactional` in the service layer; use `@Transactional(readOnly = true)` for queries. Do not hold a DB transaction open while calling Day Conditions or Transport.
- **Errors**: map all exceptions in one `@RestControllerAdvice` to the error format and codes from `architecture.md` §3.3. Never leak stack traces.
- **Money**: use `BigDecimal` for prices and costs, round results to 2 decimal places with `RoundingMode.HALF_UP`. Never use `double` for money.
- **Downstream calls** (`WeatherClient`, `TransportClient`): use Spring `RestClient` with the timeout from configuration, no retries, wrapped in a circuit breaker. A failure or timeout must degrade the response (§6), never fail the request. Call Day Conditions and Transport in parallel (virtual threads are enabled).
- Mock implementations of the clients are active under the `mock` Spring profile; they return the sample payloads from `architecture.md` §4.
- **Database**: schema changes only via Flyway migrations in `src/main/resources/db/migration` (`V<n>__<description>.sql`); `spring.jpa.hibernate.ddl-auto=validate`. Never edit an already applied migration.
- **Configuration**: read settings via `@ConfigurationProperties` (prefix `app.`), overridable by the environment variables listed in `architecture.md` §8.1. No hard-coded URLs, timeouts or secrets.
- **Security**: the service is stateless (no sessions, CSRF disabled). `/actuator/health/**` is public; all other endpoints follow the rules in `architecture.md` §9. Under the `local` profile security may be relaxed to `permitAll`.
- Code, identifiers, logs and commit messages in English; `architecture.md` is in Polish.

# Testing
- Unit tests (`*Test.java`): JUnit 5 + Mockito, no Spring context. Cover the rules from `architecture.md` §5 (weatherFit, group costs per `mode`, estimate, `maxCost` filter, sort order, haversine).
- Integration tests (`*IT.java`): `@SpringBootTest` + Testcontainers PostgreSQL, downstream services stubbed with WireMock. Cover the degradation scenarios from §6 and the error codes from §3.3.

# Definition of Done
- Run the relevant tests (`./mvnw verify` when touching more than one layer) and verify they pass.
- `architecture.md` is updated if the API, business rules, data model or configuration changed.
- Summarize modified files, key behavior changes, and any remaining assumptions.

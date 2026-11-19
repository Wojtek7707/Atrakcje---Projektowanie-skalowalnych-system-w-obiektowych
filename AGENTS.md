# Project Overview
This is a microservice of a fully scalable microservice application responsible for presenting all possible attractions to the gateway, which then selects them itself.
Backend service build:
* Java 25
* Spring Boot 4.1.2
* Java Persistance API (ORM)
* Spring Seciurity
* Postgress
Follows layered / hexagonal package structure under `src/main/java/com/example/app`.

# Build & Test Commands
- Run all unit tests: `./mvnw test` (or `./gradlew test`)
- Run single test class: `./mvnw test -Dtest=OrderServiceTest`
- Run integration tests (Testcontainers): `./mvnw verify -Dit.test=OrderControllerIT`
- Compile & check formatting/lint: `./mvnw spotbugs:check checkstyle:check` (adjust to your plugins)

# Architecture & Conventions
- Use a three-tier architecture: the controller (`*Controller.java`) is responsible for API communication, the service (`*Service.java`) is responsible for business logic, and the repository (`*Repository.java`) is responsible for connecting to the database.
- Controllers (`*Controller.java`) only handle HTTP mapping, validation (`@Valid`), and map to/from DTOs; never put business logic or `@Transactional` in controllers.
- Use constructor injection (`final` fields); avoid field injection (`@Autowired` on fields).
- Use `record` or dedicated request/response DTOs instead of exposing JPA `@Entity` classes directly in API responses.
- Keep `@Transactional` in the service layer; use read-only transactions (`@Transactional(readOnly = true)`) for queries.
- Write JUnit 5 + Mockito for unit tests (`*Test.java`) and `@SpringBootTest` + Testcontainers for integration tests (`*IT.java`).
  
# Definition of Done
- Before finishing a task, run relevant tests and verify they pass.
- Summarize modified files, key behavior changes, and any remaining assumptions.
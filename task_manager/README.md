# Task Manager REST API

A RESTful task management service built with Spring Boot, Spring Web, and Spring Data JPA. It exposes endpoints to create and retrieve tasks, with Bean Validation and a consistent response envelope across success and error cases.

## Table of Contents

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Environment Variables](#environment-variables)
- [Running the Application](#running-the-application)
- [API Reference](#api-reference)
- [Response Envelope](#response-envelope)
- [Error Handling](#error-handling)
- [Project Structure](#project-structure)
- [Testing](#testing)
- [Database](#database)
- [H2 Console](#h2-console)
- [Contributing](#contributing)
- [License](#license)

## Overview

This project is a task management backend with two endpoints:

| Method | Endpoint     | Description              |
| ------ | ------------ | ------------------------ |
| `POST` | `/task`      | Create a new task        |
| `GET`  | `/task/{id}` | Retrieve a task by its ID |

Every response — success or error — is wrapped in a consistent JSON envelope (`ApiResponse<T>`), so clients can parse responses uniformly.

## Tech Stack

| Technology                   | Version           | Purpose                              |
| ---------------------------- | ----------------- | ------------------------------------ |
| Java                         | 17                | Runtime                              |
| Spring Boot                  | 4.1.1             | Application framework                |
| Spring Web                   | BOM-managed       | REST endpoints                       |
| Spring Data JPA              | BOM-managed       | Database access                      |
| Spring Validation            | BOM-managed       | Bean Validation (`@NotBlank`, etc.)  |
| H2 Database                  | BOM-managed       | In-memory relational database        |
| Maven                        | 3.9+              | Build tool (via Maven Wrapper)       |
| JUnit 5                      | BOM-managed       | Unit and integration testing         |
| Mockito                      | BOM-managed       | Mocking in unit tests                |

Versions marked as *BOM-managed* are inherited from the `spring-boot-starter-parent` POM, so they stay consistent with the framework and require no manual maintenance.

## Prerequisites

Before you begin, make sure you have:

- **Java 17** or newer — verify with `java -version`
- **Git** — verify with `git --version`

You do **not** need to install Maven. This project includes the Maven Wrapper, so `./mvnw` will download the correct Maven version for you.

## Installation

1. **Clone the repository:**

   ```bash
   git clone <repository-url>
   cd task_manager
   ```

2. **Verify the build:**

   ```bash
   ./mvnw clean verify
   ```

   This compiles the project and runs the full test suite. You should see `BUILD SUCCESS`.

## Environment Variables

The datasource is configured through environment variables with sensible defaults for local development. To override any of them, export the variable in your shell.

| Variable      | Default                 | Description                          |
| ------------- | ----------------------- | ------------------------------------ |
| `DB_URL`      | `jdbc:h2:mem:testdb`    | JDBC connection URL                  |
| `DB_DRIVER`   | `org.h2.Driver`         | JDBC driver class                    |
| `DB_USERNAME` | `sa`                    | Database username                    |
| `DB_PASSWORD` | _(empty)_               | Database password                    |

**macOS / Linux (bash or zsh):**

```bash
export DB_USERNAME=sa
export DB_PASSWORD=your_password
```

To make these persistent, add them to `~/.zshrc` (zsh) or `~/.bashrc` (bash), then run `source ~/.zshrc`.

**Windows (PowerShell):**

```powershell
$env:DB_USERNAME = "sa"
$env:DB_PASSWORD = "your_password"
```

If no variables are set, the application starts with the defaults above (an in-memory H2 database). See [`.env.example`](.env.example) for a reference template. Never commit a real `.env` file — it is already covered by [`.gitignore`](.gitignore).

## Running the Application

```bash
./mvnw spring-boot:run
```

The service starts on `http://localhost:8080`.

To build and run a self-contained JAR:

```bash
./mvnw clean package
java -jar target/task_manager-0.0.1-SNAPSHOT.jar
```

## API Reference

### Create a Task

```http
POST /task
Content-Type: application/json
```

**Request body:**

| Field         | Type   | Required | Description                                     |
| ------------- | ------ | -------- | ----------------------------------------------- |
| `description` | string | Yes      | Description of the task (non-blank, max 255)   |
| `status`      | string | No       | Task status (non-blank if provided, max 30). Defaults to `INPROGRESS` |

**Example request:**

```json
{
  "description": "Create the POST endpoint",
  "status": "INPROGRESS"
}
```

**Response — `201 Created`:**

The `Location` header contains the URI of the newly created task.

```http
HTTP/1.1 201 Created
Location: http://localhost:8080/task/1
```

```json
{
  "success": true,
  "message": "Task created",
  "status": 201,
  "data": {
    "id": 1,
    "description": "Create the POST endpoint",
    "status": "INPROGRESS",
    "createAt": "2026-09-25T21:07:46.469",
    "updateAt": "2026-09-25T21:07:46.469"
  }
}
```

### Retrieve a Task by ID

```http
GET /task/{taskId}
```

**Example request:**

```bash
curl http://localhost:8080/task/1
```

**Response — `200 OK`:**

```json
{
  "success": true,
  "message": "Task found",
  "status": 200,
  "data": {
    "id": 1,
    "description": "Create the POST endpoint",
    "status": "INPROGRESS",
    "createAt": "2026-09-25T21:07:46.469",
    "updateAt": "2026-09-25T21:07:46.469"
  }
}
```

## Response Envelope

All responses use the `ApiResponse<T>` structure:

| Field     | Type    | Description                                             |
| --------- | ------- | ------------------------------------------------------- |
| `success` | boolean | `true` for 2xx status codes, `false` otherwise          |
| `message` | string  | Human-readable summary of the outcome                  |
| `status`  | integer | HTTP status code                                        |
| `data`    | any     | Response payload, or `null` when there is none         |

## Error Handling

`GlobalExceptionHandler` centralizes error handling. It extends Spring's `ResponseEntityExceptionHandler`, so framework-level errors keep their correct HTTP status while still being wrapped in the same envelope.

| Scenario                                    | Status | Exception                  |
| ------------------------------------------- | ------ | -------------------------- |
| `description` or `status` blank             | 400    | `MethodArgumentNotValidException` |
| Malformed JSON                              | 400    | Framework-handled          |
| Empty request body                          | 400    | Framework-handled          |
| Task body is `null`                         | 400    | `InvalidTaskException`     |
| No task found with the given ID             | 404    | `TaskNotFoundException`    |
| Repository fails to persist the task        | 500    | `TaskPersistenceException` |
| Any other unexpected error                  | 500    | Generic fallback (logged)  |

**Example — validation error (`400`):**

```json
{
  "success": false,
  "message": "Validation failed",
  "status": 400,
  "data": {
    "description": "description is required"
  }
}
```

Unexpected server errors are logged with a stack trace server-side; the client only receives a generic `500` payload, so internal details are never leaked.

## Project Structure

```text
task_manager/
├── .env.example              # Reference template for environment variables
├── .gitattributes            # Line-ending normalization rules
├── .gitignore                # Excludes build output, secrets, IDE files
├── mvnw / mvnw.cmd           # Maven Wrapper (Unix / Windows)
├── pom.xml                   # Maven build and dependencies
└── src/
    ├── main/
    │   ├── java/task_manager/
    │   │   ├── TaskManagerApplication.java        # Entry point
    │   │   ├── controller/
    │   │   │   ├── TaskManagerController.java     # REST endpoints
    │   │   │   └── model/TaskEntity.java          # JPA entity + validation
    │   │   ├── exception/
    │   │   │   ├── ApiResponse.java               # Response envelope
    │   │   │   ├── GlobalExceptionHandler.java    # Centralized error handling
    │   │   │   ├── InvalidTaskException.java
    │   │   │   ├── TaskNotFoundException.java
    │   │   │   └── TaskPersistenceException.java
    │   │   ├── repository/TaskRepository.java     # Spring Data JPA repository
    │   │   └── service/
    │   │       ├── TaskService.java               # Service interface
    │   │       └── TaskServiceImpl.java           # Service implementation
    │   └── resources/
    │       ├── application.properties             # Datasource config (env-driven)
    │       ├── banner.txt
    │       └── mocks/task.json
    └── test/
        ├── java/task_manager/
        │   ├── TaskManagerApplicationTests.java   # Context load test
        │   ├── controller/TaskManagerControllerTest.java  # MockMvc integration tests
        │   └── service/TaskServiceImplTest.java    # Mockito unit tests
        └── resources/
            ├── application-test.properties        # Isolated test datasource
            ├── data.sql                            # Seed data
            └── schema.sql
```

The project follows a layered architecture:

```text
Controller  →  Service  →  Repository  →  Database
```

Each layer has a single responsibility, and the service layer is the only place business rules live.

## Testing

Run the full test suite:

```bash
./mvnw test
```

Run a single test class:

```bash
./mvnw test -Dtest=TaskManagerControllerTest
```

| Test class                       | Type         | Approach                                                  |
| -------------------------------- | ------------ | --------------------------------------------------------- |
| `TaskManagerApplicationTests`    | Integration  | Verifies the Spring context loads                         |
| `TaskManagerControllerTest`      | Integration  | `@SpringBootTest(MOCK)` + `MockMvc`, hits the real endpoints |
| `TaskServiceImplTest`            | Unit         | Pure Mockito, no Spring context required                  |

Both integration tests activate the `test` profile via `@ActiveProfiles("test")`, which points the datasource at a dedicated in-memory database.

Tests run against an **isolated in-memory H2 database** (`jdbc:h2:mem:task_manager_test`) with `ddl-auto=create-drop`, so they never touch the development database and leave no residue behind.

The suite contains 17 tests covering creation, retrieval, validation failures, malformed JSON, empty bodies, and service-layer error paths.

## Database

The application uses H2 as an in-memory database by default:

| Setting              | Value            |
| -------------------- | ---------------- |
| `spring.jpa.hibernate.ddl-auto` | `update`   |

The schema is generated from the `TaskEntity` mapping. The `tasks` table has the following structure:

| Column        | Type          | Constraints                     |
| ------------- | ------------- | ------------------------------- |
| `id`          | BIGINT        | Primary key, auto-generated     |
| `description` | VARCHAR(255)  | Not null                        |
| `status`      | VARCHAR(30)   | Not null                        |
| `create_at`   | TIMESTAMP     | Not null, set on insert         |
| `update_at`   | TIMESTAMP     | Not null, set on insert/update  |

`createAt` and `updateAt` are populated automatically by JPA lifecycle callbacks (`@PrePersist` and `@PreUpdate`).

Because the database is in-memory, **all data is lost when the application stops.** To persist data across restarts, configure `DB_URL` to point at a file-based or server-based database.

## H2 Console

The H2 web console is enabled for local development:

- **URL:** http://localhost:8080/h2-console
- **JDBC URL:** `jdbc:h2:mem:testdb`
- **Username:** `sa`
- **Password:** _(empty)_

## Contributing

Contributions are welcome. To contribute:

1. Fork the repository and create a feature branch:

   ```bash
   git checkout -b feature/your-feature
   ```

2. Make your changes, following the existing project structure and code style.

3. Ensure the build and tests pass:

   ```bash
   ./mvnw clean verify
   ```

4. Commit using [Conventional Commits](https://www.conventionalcommits.org/):

   ```text
   feat: add task update endpoint
   fix: return 400 instead of 500 on empty request body
   docs: document environment variables
   ```

5. Open a pull request describing your changes.

## License

This project is provided as-is for educational purposes. Add a license file (for example, `MIT`) to formally define the terms of use.

# API and Smithy Design

## 1. Why Smithy

Smithy is the Interface Definition Language for the project.

It provides one central contract for:

- operation inputs/outputs;
- domain structures;
- enums/unions;
- HTTP bindings;
- generated Java model types.

This avoids maintaining one set of API DTOs by hand and a second, separate API description.

---

## 2. Module Boundary

```text
scheduler-model
    ↓ generated Java types
scheduler-api
```

`scheduler-model` owns the Smithy definitions.

`scheduler-api` depends on the generated types and implements the HTTP/business behavior in Spring Boot.

The Smithy code-generation mode is `types`, because Spring Boot—not a generated Smithy server runtime—owns request routing and application startup.

---

## 3. Identifier Shapes

At the API boundary, identifiers are modeled as constrained/named strings.

Conceptually:

```smithy
string JobId
string TaskId
```

The service layer converts them to native Java/Cassandra UUID values:

```java
UUID jobId = UUID.fromString(input.jobId());
```

This keeps the wire representation JSON-friendly while allowing Cassandra to use its native `uuid` type.

---

## 4. Schedule Model

The latest model uses a schedule type plus expression:

```text
Schedule
├── type
└── expression
```

Conceptually:

```smithy
@enum([
    { value: "CRON" },
    { value: "ONE_TIME" }
])
string ScheduleType

structure Schedule {
    @required
    type: ScheduleType

    @required
    expression: String
}
```

Examples:

### Cron

```json
{
  "type": "CRON",
  "expression": "30 15 * * 5"
}
```

### One-time

```json
{
  "type": "ONE_TIME",
  "expression": "2026-11-01T15:30:00Z"
}
```

Validation depends on `type`.

---

## 5. Task Model

Conceptual Smithy shape:

```smithy
structure TaskDefinition {
    @required
    taskId: TaskId

    @required
    name: String

    @required
    parameterSchema: StringMap
}
```

`parameterSchema` describes which fields a job must provide.

The persisted Cassandra representation is currently simple (`map<text,text>`), deliberately avoiding a complex schema language while the project is still small.

---

## 6. Job Model

Conceptually:

```smithy
structure Job {
    @required
    jobId: JobId

    @required
    taskId: TaskId

    @required
    name: String

    @required
    parameters: Document

    @required
    schedule: Schedule

    @required
    createdAt: Timestamp
}
```

The API model can use Smithy `Document` for flexible task parameters even though the initial Cassandra representation is a simple text map.

A mapper handles the persistence conversion boundary.

---

## 7. JobExecution Model

The execution model represents scheduler-visible state rather than Cassandra partitioning concerns.

Conceptual fields:

```text
jobId
executionTime
status
attempt
startedAt
completedAt
error
```

`timeBucket` is primarily a Cassandra storage concern and does not need to be exposed to external API clients unless a debugging/administrative API later requires it.

---

## 8. Operations

### Create Job

```http
POST /jobs
```

Conceptual Smithy operation:

```smithy
@http(method: "POST", uri: "/jobs", code: 201)
operation CreateJob {
    input: CreateJobInput
    output: CreateJobOutput
}
```

Example input:

```json
{
  "name": "weekly-email",
  "taskId": "f822bb44-7e0d-4da6-a0d9-9180fc318bba",
  "parameters": {
    "recipient": "user@example.com",
    "retryCount": "3",
    "subject": "TESTING IF MY API WORKS",
    "body": "TESTING"
  },
  "schedule": {
    "type": "CRON",
    "expression": "30 15 * * 5"
  }
}
```

### Get Job

```http
GET /jobs/{jobId}
```

Conceptual Smithy operation:

```smithy
@http(method: "GET", uri: "/jobs/{jobId}", code: 200)
operation GetJob {
    input: GetJobInput
    output: GetJobOutput
}
```

with:

```smithy
structure GetJobInput {
    @required
    @httpLabel
    jobId: JobId
}
```

---

## 9. No Update Operation

There is intentionally no:

```http
PUT /jobs/{jobId}
PATCH /jobs/{jobId}
```

Job immutability simplifies:

- recurrence reasoning;
- execution history;
- idempotency;
- cache behavior;
- debugging.

If a caller wants different parameters or a different schedule, they create a new job.

A cancellation operation may be added later without changing this principle because cancellation is a state transition, not mutation of the original schedule definition.

---

## 10. Spring + Generated Smithy Model Serialization

Generated Smithy types are immutable builder-based models and do not necessarily look like normal JavaBeans with zero-argument constructors.

Direct Spring/Jackson request conversion therefore caused `HttpMessageConversionException` during development.

The project solved this boundary with a custom Spring MVC argument resolver for Smithy inputs.

Conceptually:

```java
public final class SmithyArgumentResolver
        implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        // Detect supported generated Smithy input types.
    }

    @Override
    public Object resolveArgument(...) {
        // Read request body and construct the immutable Smithy model.
    }
}
```

The resolver is registered through Spring MVC configuration:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addArgumentResolvers(
            List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(smithyArgumentResolver);
    }
}
```

Jackson/Smithy document support is also registered where required for `Document` values.

This keeps controllers typed with Smithy inputs without introducing duplicate handwritten request DTOs.

---

## 11. Path Parameters

Smithy `@httpLabel` describes the API contract, but Spring MVC still owns the actual request-binding behavior in this project because the application is not using a generated Smithy server runtime.

Therefore the custom binding layer/controller must explicitly map the Spring path variable into the generated Smithy input shape.

This is why `@httpLabel` alone does not make a Spring `GET /jobs/{jobId}` parameter magically resolve.

---

## 12. Error Model

`JobNotFoundException` should map to an HTTP 404 response.

Conceptually:

```smithy
@error("client")
@httpError(404)
structure JobNotFoundException {
    @required
    message: String
}
```

Other useful client errors include:

- invalid UUID;
- unknown task;
- invalid/missing task parameters;
- invalid cron expression;
- one-time timestamp in an invalid format or unacceptable past time.

Infrastructure errors should not be exposed as client validation errors.

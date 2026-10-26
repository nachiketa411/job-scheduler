# Implementation Details

## 1. Current Development Environment

The project is being developed primarily on:

```text
OS: Windows 11
IDE: IntelliJ IDEA
JDK: Java 21
Build: Gradle Kotlin DSL
```

Local infrastructure runs in Docker.

---

## 2. Gradle Modules

### `scheduler-model`

Responsibilities:

- Smithy source files;
- Smithy validation/build;
- generated Java model types.

### `scheduler-api`

Responsibilities:

- Spring Boot application;
- controllers;
- service layer;
- Cassandra repositories;
- persistence/domain mappers;
- custom Smithy argument resolution;
- configuration.

The model module should not depend on the API module.

---

## 3. Smithy Build

Smithy generation is configured in `types` mode.

Why:

- the project needs generated model classes;
- Spring Boot owns the server runtime;
- a generated HTTP server implementation is unnecessary;
- transport concerns remain explicit in the Spring controllers/configuration.

A successful build should make the generated types available to `scheduler-api` through a Gradle project dependency.

---

## 4. Spring Web Binding

### Problem encountered

Generated Smithy inputs are immutable/builder-oriented and did not deserialize through Spring's default Jackson bean path.

This led to request conversion failures such as:

```text
HttpMessageConversionException
```

### Implemented solution

A custom `HandlerMethodArgumentResolver` recognizes supported Smithy input classes and builds them from the HTTP request.

Registration occurs through a `WebMvcConfigurer`.

This avoids creating duplicate DTOs solely for Spring deserialization.

---

## 5. JSON / Smithy Document Mapping

Flexible job parameters are represented in the API/domain model with Smithy `Document` semantics.

Persistence currently uses Cassandra maps.

A dedicated mapper boundary converts between the representations.

Logical component:

```text
ParameterDocumentMapper
```

Responsibilities:

```text
Smithy Document -> map<text,text>
map<text,text>   -> Smithy Document
```

Keeping this conversion isolated prevents Cassandra persistence choices from leaking throughout the service layer.

---

## 6. Cassandra Infrastructure

Docker Cassandra has been brought up successfully after correcting an earlier snitch configuration typo.

The active keyspace is:

```text
scheduler
```

Current logical tables:

```text
tasks
jobs
job_executions
```

Cassandra is accessed by the application through `CqlSession`-based repositories.

Repository code should keep CQL local to the repository layer.

Example structure:

```text
repository/
├── TaskRepository.java
├── JobRepository.java
└── JobExecutionRepository.java
```

---

## 7. Redis Infrastructure

Redis is running locally in Docker.

Basic connectivity has been verified with:

```text
PING -> PONG
```

Persistence/AOF is enabled in the local infrastructure setup.

The Spring application does not yet use Redis for execution dispatch.

Next application-level components will include:

```text
ExecutionQueue
RedisExecutionQueue
Watcher
Worker
```

---

## 8. Create Job Service Logic

Current service responsibilities can be represented as:

```java
public CreateJobOutput createJob(CreateJobInput input) {
    TaskDefinition task = taskRepository.findById(input.taskId())
        .orElseThrow(...);

    validateParameters(task, input.parameters());
    validateSchedule(input.schedule());

    Job job = buildJob(input);
    jobRepository.save(job);

    Instant firstExecutionTime = scheduleResolver.firstExecution(input.schedule());

    JobExecution execution = buildPendingExecution(
        job.jobId(),
        firstExecutionTime,
        0
    );

    jobExecutionRepository.save(execution);

    return CreateJobOutput.builder()
        .job(job)
        .build();
}
```

Exact class/method names may differ, but this is the implemented responsibility split.

---

## 9. Parameter Validation

When creating a job:

1. load task definition;
2. compare supplied parameter names against `parameter_schema`;
3. verify required parameters exist;
4. reject unknown/invalid parameters according to the chosen validation policy;
5. convert/store the validated parameters.

This ensures invalid jobs do not reach the execution path.

The current `map<text,text>` representation keeps validation intentionally simple while task types are still abstract.

---

## 10. Schedule Validation

### CRON

Validation should:

- require a valid five-field expression;
- normalize to Spring's seconds-first representation for evaluation;
- reject expressions the selected parser cannot represent.

### ONE_TIME

Validation should:

- parse an ISO-8601 instant;
- decide whether past timestamps are rejected at create time;
- store/compute using UTC.

Time-zone-aware cron schedules can be a later API extension rather than an implicit local-time behavior.

---

## 11. Time Bucket Utility

The execution persistence layer should centralize bucket calculation.

Conceptually:

```java
Instant bucketFor(Instant executionTime, Duration bucketSize) {
    long bucketSeconds = bucketSize.toSeconds();
    long epochSeconds = executionTime.getEpochSecond();
    long floored = (epochSeconds / bucketSeconds) * bucketSeconds;
    return Instant.ofEpochSecond(floored);
}
```

Do not duplicate this arithmetic in controller/service/watcher code.

A dedicated utility/value object ensures every component calculates the same Cassandra partition key.

---

## 12. JobExecution Repository Operations

The repository should support the minimum access patterns required by the scheduler.

```text
save(execution)
find(timeBucket, executionTime, jobId)
findBetween(timeBucket, from, to)
updateStatus(...)
```

Future lease/retry functionality may add:

```text
markRunning(...)
markSucceeded(...)
markRetrying(...)
markFailed(...)
findExpiredRunning(...)
```

Keep these as semantic repository methods rather than issuing raw CQL throughout the worker.

---

## 13. Redis Queue Abstraction

Before implementing the watcher, define a small application abstraction:

```java
public interface ExecutionQueue {
    void enqueue(ExecutionReference execution, Instant dueAt);
    Optional<ExecutionReference> claimDue(Instant now);
}
```

Redis-specific commands stay inside the implementation.

This gives the worker a scheduling abstraction rather than a dependency on Redis APIs.

---

## 14. Watcher Implementation Plan

Suggested first implementation:

```java
@Component
public class ExecutionWatcher {

    @Scheduled(fixedDelayString = "${scheduler.watcher.poll-interval}")
    public void dispatchNearFutureExecutions() {
        Instant now = clock.instant();
        Instant horizon = now.plus(lookAhead);

        for (Instant bucket : bucketCalculator.buckets(now, horizon)) {
            repository.findBetween(bucket, now, horizon).stream()
                .filter(this::eligibleForQueue)
                .forEach(this::enqueue);
        }
    }
}
```

Start with one watcher instance.

After end-to-end correctness is established, introduce distributed watcher coordination or make duplicate scans harmless through stronger idempotent state transitions.

---

## 15. Worker Implementation Plan

Suggested logical classes:

```text
Worker
ExecutionQueue
TaskHandlerRegistry
TaskHandler
RetryPolicy
ScheduleResolver
```

Pseudo-code:

```java
while (running) {
    Optional<ExecutionReference> claimed = queue.claimDue(clock.instant());

    if (claimed.isEmpty()) {
        sleep(shortIdleDelay);
        continue;
    }

    process(claimed.get());
}
```

`process` owns execution state transitions and delegates actual work to a `TaskHandler`.

---

## 16. Cron Recurrence Implementation Plan

After an execution reaches terminal success/failure according to the chosen recurrence policy:

```java
if (job.schedule().type() == CRON) {
    Instant next = scheduleResolver.nextExecution(
        job.schedule(),
        currentExecution.executionTime()
    );

    jobExecutionRepository.save(
        pendingExecution(job.jobId(), next)
    );
}
```

The insertion must be idempotent under duplicate terminal handling.

The deterministic Cassandra primary key is useful here because the same `(bucket, nextTime, jobId)` represents the same next occurrence.

---

## 17. Configuration

Useful `application.yml` sections:

```yaml
spring:
  cassandra:
    keyspace-name: scheduler
    contact-points: localhost
    port: 9042
    local-datacenter: datacenter1

scheduler:
  execution:
    bucket-size: PT1H
  watcher:
    poll-interval: PT1S
    look-ahead: PT5M
  worker:
    idle-delay: PT0.1S
  retry:
    base-delay: PT1S
    max-delay: PT5M
```

These values are examples of configuration shape, not finalized production tuning.

Redis connection configuration should be added when the application integration begins.

---

## 18. Testing Strategy

### Unit tests

- time-bucket rounding;
- cron normalization;
- next-occurrence calculation;
- task parameter validation;
- retry backoff;
- execution state transition validation.

### Repository integration tests

- insert/get task;
- insert/get job;
- range-query executions in a bucket;
- same timestamp + different job IDs coexist;
- execution status updates.

### End-to-end tests

1. create one-time job;
2. verify first execution row;
3. watcher queues it;
4. worker claims it;
5. handler executes;
6. status becomes `SUCCEEDED`.

For cron:

7. verify exactly one next execution is created.

### Failure tests

- worker crashes after claim;
- Redis restarts;
- Cassandra temporarily unavailable;
- duplicate watcher scan;
- duplicate completion callback/path;
- retryable task failure;
- terminal task failure.

---

## 19. Operational Logging

Prefer structured fields over free-form log strings.

Example:

```text
operation=create_job
jobId=...
taskId=...
scheduleType=CRON
```

Worker:

```text
operation=execute_job
jobId=...
executionTime=...
attempt=1
status=RUNNING
```

This makes later observability work significantly easier.

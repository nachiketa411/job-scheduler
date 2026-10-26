# Roadmap

## Current Baseline

Already in place:

- Gradle multi-module project;
- Java 21 / Spring Boot application;
- Smithy model/code generation;
- create/get job API path;
- custom Smithy request argument resolution;
- Cassandra local infrastructure;
- Redis local infrastructure;
- `tasks`, `jobs`, and `job_executions` tables;
- Cassandra repositories;
- task/schedule validation;
- persistence of the first `PENDING` execution when a job is created.

---

## Milestone 1 — Redis Queue Integration

Goal:

> The application can enqueue an execution ordered by its due time and atomically claim due work.

Work:

- add Redis Spring dependency/configuration;
- implement `ExecutionQueue`;
- use Sorted Set score = execution timestamp;
- use deterministic member = execution reference;
- implement atomic due-item claim;
- integration-test queue ordering.

Success criteria:

```text
enqueue A due at T+10
then enqueue B due at T+5
claim order = B, A
```

---

## Milestone 2 — Watcher / Dispatcher

Goal:

> Cassandra `PENDING` executions automatically appear in Redis before they are due.

Work:

- bucket calculation utility;
- `findBetween` repository query;
- scheduled watcher loop;
- look-ahead horizon;
- duplicate-safe enqueue;
- `PENDING -> QUEUED` transition.

Success criteria:

- a newly created execution enters Redis without manual action;
- repeated watcher scans do not create duplicate logical queue entries;
- bucket-boundary scans work correctly.

---

## Milestone 3 — Worker and Task Handler

Goal:

> A due queue entry runs a concrete task and updates Cassandra.

Work:

- `TaskHandler` interface;
- handler registry;
- initial simple task implementation;
- worker claim loop;
- load job/task/execution;
- `QUEUED -> RUNNING -> SUCCEEDED/FAILED` transitions.

Start with a deterministic low-risk task such as logging/writing a test payload before using a side-effect-heavy task.

Success criteria:

- one-time job runs without manual intervention;
- status timestamps are populated;
- stale duplicate Redis entries do not re-run terminal executions.

---

## Milestone 4 — Retry Semantics

Goal:

> Retryable worker failures do not lose scheduled work.

Work:

- retry policy abstraction;
- attempt increment;
- exponential backoff;
- `RETRYING` state;
- requeue at retry timestamp;
- terminal `FAILED` state.

Success criteria:

- intentionally failing task retries expected number of times;
- retry delays grow according to policy;
- non-retryable failures terminate directly.

---

## Milestone 5 — Cron Chaining

Goal:

> A cron job continuously schedules itself one occurrence at a time.

Work:

- next-occurrence calculation after terminal execution;
- deterministic next-execution insert;
- duplicate completion safety;
- verify `ONE_TIME` creates no next row.

Success criteria:

- at any point, a cron job has only the intended future occurrence materialized;
- processing the same terminal state twice does not create two logical next occurrences.

---

## Milestone 6 — Worker Recovery / Leases

Goal:

> Work is recovered if a worker disappears after claiming it.

Work:

- lease metadata;
- lease expiration;
- recovery scanner;
- return abandoned execution to retry path;
- distinguish active vs expired execution ownership.

Success criteria:

- kill a worker mid-task;
- execution eventually becomes eligible again;
- scheduler preserves at-least-once semantics.

---

## Milestone 7 — Multi-Instance Correctness

Goal:

> Multiple watchers and workers can run concurrently.

Work:

- atomic Redis claim;
- duplicate-safe watcher behavior;
- recurrence race protection;
- conditional state transitions where required;
- concurrency/load tests.

Success criteria:

- no execution is silently lost;
- duplicates remain bounded/understood;
- no duplicate next cron occurrence is created.

---

## Milestone 8 — Observability

Add:

- Micrometer metrics;
- execution delay histogram;
- execution duration histogram;
- success/failure/retry counters;
- Redis queue depth;
- oldest due item age;
- watcher scan latency;
- Cassandra query latency;
- structured logging;
- health endpoints.

---

## Milestone 9 — Performance Validation

Only after correctness:

- synthetic task handlers;
- increasing create-job load;
- increasing execution throughput;
- burst testing;
- measure scheduler delay p50/p95/p99;
- measure Cassandra partition sizes;
- tune bucket duration;
- tune watcher horizon/poll interval;
- tune Redis/worker concurrency.

The original 10K executions/sec design goal should be treated as something to validate experimentally, not something assumed from architecture alone.

---

## Possible Later Features

Not part of the current MVP:

- cancellation;
- per-job execution history APIs;
- task-specific typed parameter schemas;
- cron time zones;
- dead-letter inspection/replay;
- rate limits;
- execution priorities beyond scheduled time;
- job ownership/authentication;
- long-running task heartbeats;
- distributed tracing;
- admin UI;
- DAGs/workflows.

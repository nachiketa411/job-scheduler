# Failure Semantics and Idempotency

## 1. Delivery Guarantee

The scheduler targets **at-least-once execution**.

This means:

- an eligible job execution should not be silently lost;
- retries are allowed after uncertain failure;
- the same logical execution can run more than once.

Exactly-once execution is not claimed.

---

## 2. Failure Classes

### Visible failure

The task handler throws an error that the worker observes.

Example:

```text
HTTP dependency returned 503
```

Worker behavior:

1. record the failure;
2. increment attempt;
3. determine whether the error is retryable;
4. calculate exponential backoff;
5. mark `RETRYING` and schedule the next attempt, or mark terminal `FAILED`.

### Invisible/ambiguous failure

The worker disappears or loses connectivity during execution.

Example:

```text
Task external side effect succeeds
Worker crashes before Cassandra status update
```

The scheduler cannot know whether the task took effect.

To preserve delivery, the execution must eventually be retried after its lease expires.

This is the source of possible duplicate execution.

---

## 3. Why Health Pings Are Not Enough

An early design idea was for consumers to continuously ping executors to prove they were alive.

That does not scale well and does not solve the core ambiguity:

- thousands of workers produce constant health traffic;
- a live process may still be stuck on one job;
- a process can die after performing the external side effect;
- health status is not execution ownership.

A lease tied to an execution is more useful than global worker liveness.

---

## 4. Execution Lease

Future worker claiming should associate an execution with a lease expiration.

Conceptually:

```text
status = RUNNING
leaseOwner = worker-17
leaseUntil = 2026-10-30T15:35:00Z
```

If `leaseUntil < now` and the row is still non-terminal, the scheduler may recover it.

The exact schema for lease metadata has not yet been added to the current Cassandra table and belongs to the distributed-worker phase.

---

## 5. Idempotency

Before executing, the worker must re-check durable state.

```text
if status == SUCCEEDED:
    do not execute again
```

This prevents easy duplicates caused by stale Redis entries.

However, it cannot prevent the ambiguous failure case where the external action completed but `SUCCEEDED` was never stored.

Therefore the task implementation itself should be idempotent whenever possible.

---

## 6. Task-Level Idempotency

Examples:

### Sending an email

Use an idempotency key derived from the execution identity if the downstream mail provider supports idempotency.

### Updating an external record

Prefer setting the desired state with a deterministic request ID rather than issuing a blind increment.

### Internal database write

Use a unique execution key/conditional insert so a repeated worker attempt cannot create duplicate logical state.

The scheduler can provide the execution identity to handlers so they can propagate it downstream.

---

## 7. Retry Policy

Recommended retry decisions belong to the task/executor boundary.

Examples:

Retryable:

- timeout;
- transient network failure;
- dependency 429;
- dependency 5xx.

Usually non-retryable:

- malformed task parameters;
- authentication configuration error;
- permanent 4xx validation error.

The scheduler should not blindly retry every exception the same number of times.

---

## 8. Exponential Backoff

Conceptual formula:

```text
backoff(attempt) = min(base * 2^attempt, maximum)
```

Jitter should be added in a multi-worker system to prevent retry synchronization:

```text
actualDelay = randomized(backoff)
```

The initial implementation can start with deterministic exponential backoff, then add jitter once parallel retry behavior is tested.

---

## 9. Recurrence + Failure

A critical rule for cron jobs:

> Only one code path should be responsible for materializing the next occurrence.

The project design currently uses terminal execution processing.

The worker must ensure that repeated completion handling cannot generate multiple identical next executions.

Because the execution primary key is deterministic:

```text
(time_bucket, execution_time, job_id)
```

attempting to insert the same next occurrence repeatedly targets the same logical row.

The recurrence logic should still be written idempotently and tested under duplicate completion events.

---

## 10. Redis Failure

Redis is reconstructable.

If Redis restarts or loses data:

1. workers temporarily stop receiving work;
2. Cassandra still contains non-terminal executions;
3. watcher scans reconstruct the Sorted Set;
4. late executions run when recovered.

Redis persistence improves recovery speed but is not required for the durable correctness model.

---

## 11. Cassandra Failure

If Cassandra is unavailable:

- new job creation should fail rather than acknowledge an undurable job;
- watcher should stop advancing durable state;
- workers must not claim success they cannot persist;
- retry/recovery can resume after Cassandra returns.

The system should prefer delayed work over falsely reporting successful durable scheduling.

---

## 12. Duplicate Queue Entries

The Redis member must be deterministic.

Using:

```text
<jobId>@<executionEpochMillis>
```

means repeated `ZADD` calls refer to the same Sorted Set member.

This makes watcher rescans naturally idempotent at the queue layer.

---

## 13. Observability Required for Failure Analysis

Useful future metrics:

```text
jobs_created_total
executions_created_total
executions_started_total
executions_succeeded_total
executions_failed_total
execution_retries_total
execution_start_delay_ms
execution_duration_ms
watcher_scan_duration_ms
watcher_executions_enqueued_total
redis_queue_depth
redis_oldest_due_age_ms
expired_execution_leases_total
```

Logs should include at minimum:

```text
jobId
executionTime
attempt
status transition
task name
worker instance
error category
```

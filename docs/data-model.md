# Data Model

## 1. Modeling Strategy

The database model follows the scheduler's access patterns rather than a normalized relational design.

The three main concepts are deliberately separate:

```text
Task Definition -> Job -> JobExecution
```

- `tasks` changes rarely and defines what work is valid.
- `jobs` stores immutable user configuration.
- `job_executions` is write-heavy and time-oriented.

---

## 2. `tasks`

A task defines a reusable type of work and the parameter names/types that a job must provide.

Recommended/current logical schema:

```sql
CREATE TABLE scheduler.tasks (
    task_id uuid PRIMARY KEY,
    name text,
    parameter_schema map<text, text>
);
```

Example:

```text
task_id: f822bb44-7e0d-4da6-a0d9-9180fc318bba
name: send-email
parameter_schema:
  recipient -> string
  subject -> string
  body -> string
  retryCount -> integer
```

### Query pattern

```sql
SELECT * FROM scheduler.tasks WHERE task_id = ?;
```

The job service uses this row to validate a create-job request before persistence.

---

## 3. `jobs`

Jobs are immutable.

Logical schema:

```sql
CREATE TABLE scheduler.jobs (
    job_id uuid PRIMARY KEY,
    task_id uuid,
    name text,
    parameters map<text, text>,
    schedule_type text,
    schedule_expression text,
    created_at timestamp
);
```

Example row:

```text
job_id              da0d4ae1-0e03-4d58-b2d6-e2221238bfc9
task_id             f822bb44-7e0d-4da6-a0d9-9180fc318bba
name                weekly-email
parameters          {recipient, subject, body, retryCount}
schedule_type       CRON
schedule_expression 30 15 * * 5
created_at          <timestamp>
```

### Query pattern

```sql
SELECT * FROM scheduler.jobs WHERE job_id = ?;
```

There is intentionally no update-oriented access pattern in the current API.

---

## 4. `job_executions`

The execution table is designed for scheduler scans by time.

Logical schema:

```sql
CREATE TABLE scheduler.job_executions (
    time_bucket timestamp,
    execution_time timestamp,
    job_id uuid,
    status text,
    attempt int,
    started_at timestamp,
    completed_at timestamp,
    error text,
    PRIMARY KEY ((time_bucket), execution_time, job_id)
) WITH CLUSTERING ORDER BY (execution_time ASC, job_id ASC);
```

### Why this key?

```text
Partition key:  time_bucket
Clustering #1:  execution_time
Clustering #2:  job_id
```

`time_bucket` groups executions due near each other.

`execution_time` provides scheduler ordering.

`job_id` makes rows unique when multiple jobs share the same execution timestamp.

The complete Cassandra primary key is therefore unique while retaining the correct read pattern.

---

## 5. Time Buckets

An execution timestamp is rounded down to a configured bucket boundary.

For a bucket size `B` seconds:

```text
bucket = floor(epochSeconds / B) * B
```

Example for one-hour buckets:

```text
execution time = 2026-10-30 15:37:14 UTC
bucket         = 2026-10-30 15:00:00 UTC
```

A smaller bucket:

- reduces the number of rows per partition;
- increases the number of partitions the watcher may need to query.

A larger bucket:

- reduces partition fan-out;
- increases partition size and potential hot spots.

The bucket size should therefore be configuration, not hardcoded scheduler logic.

The initial design notes considered minute, 5-minute, 30-minute, and hourly buckets. A one-hour bucket is simple for early development, while load testing should determine the final production-like value.

---

## 6. Due-Execution Query

For a watcher scanning one bucket:

```sql
SELECT *
FROM scheduler.job_executions
WHERE time_bucket = ?
  AND execution_time >= ?
  AND execution_time < ?;
```

Because `execution_time` is a clustering column, Cassandra can satisfy this range scan efficiently within the known partition.

If the scan horizon crosses a bucket boundary, issue one query per affected bucket.

Example:

```text
Now:     15:58
Horizon: 16:03
Bucket size: 1 hour

Queries:
1. bucket 15:00 for [15:58, 16:00)
2. bucket 16:00 for [16:00, 16:03)
```

---

## 7. Execution Identity

The execution is uniquely identified by the Cassandra key tuple:

```text
(time_bucket, execution_time, job_id)
```

A separate `execution_id` is not required for the current schema.

When an external component such as Redis needs a single opaque identifier, serialize the tuple deterministically, for example:

```text
<jobId>@<executionEpochMillis>
```

The member only needs to be stable and unique for a job occurrence; the full canonical data remains in Cassandra.

---

## 8. Status Updates

The row evolves through status updates:

```text
PENDING
QUEUED
RUNNING
RETRYING
SUCCEEDED
FAILED
```

Example transition updates:

```sql
UPDATE scheduler.job_executions
SET status = 'RUNNING', started_at = ?
WHERE time_bucket = ?
  AND execution_time = ?
  AND job_id = ?;
```

and:

```sql
UPDATE scheduler.job_executions
SET status = 'SUCCEEDED', completed_at = ?
WHERE time_bucket = ?
  AND execution_time = ?
  AND job_id = ?;
```

The full primary key must be known to update a Cassandra row efficiently, which is why worker queue messages must retain the execution identity.

---

## 9. Retry Representation

The current schema contains `attempt` on the execution row.

Initial execution:

```text
attempt = 0
```

Each retry increments the value.

This design represents retries as attempts of the same logical scheduled occurrence rather than as independent jobs.

If detailed per-attempt history becomes important later, add a separate attempt-history table rather than overloading the current execution row.

---

## 10. Why Not Query Jobs Directly for Cron Work?

If the scheduler stored only jobs, every polling cycle would have to:

1. scan cron jobs;
2. parse every cron expression;
3. calculate next execution times;
4. decide which jobs are due.

At scale, that makes scheduler work proportional to the number of jobs rather than the number of near-term executions.

Materializing `JobExecution` rows changes the query into:

> Find executions in this known time range.

That is the access pattern Cassandra is designed to serve efficiently.

---

## 11. Consistency Expectations

The scheduler prioritizes availability and durability over globally synchronous reads.

The system should not rely on immediate cross-node read-after-write behavior for correctness. Instead:

- execution creation is idempotent/deterministic where possible;
- dispatch is retryable;
- Cassandra is eventually reconciled through repeated watcher scans;
- duplicate work is tolerated by at-least-once semantics.

---

## 12. Future Query Models

If the project later adds APIs such as:

```text
List all jobs for a user
List recent executions for a job
List failed executions
```

Cassandra should use purpose-built query tables instead of secondary scans over the current execution table.

For example:

```text
executions_by_job
jobs_by_owner
failed_executions_by_time
```

Those tables are intentionally not part of the current minimal design because the current API does not need those access patterns.

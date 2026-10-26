CREATE KEYSPACE IF NOT EXISTS scheduler
WITH replication = {
    'class': 'SimpleStrategy',
    'replication_factor': 1
};

CREATE TABLE IF NOT EXISTS scheduler.tasks (
    task_id uuid PRIMARY KEY,
    name text,
    parameter_schema map<text, text>
);

CREATE TABLE IF NOT EXISTS scheduler.jobs (
    job_id uuid PRIMARY KEY,
    created_at timestamp,
    name text,
    schedule_expression text,
    schedule_type text,
    task_id uuid,
    parameters map<text, text>
);

CREATE TABLE IF NOT EXISTS scheduler.job_executions (
    time_bucket timestamp,
    execution_time timestamp,
    job_id uuid,
    attempt int,
    completed_at timestamp,
    error text,
    started_at timestamp,
    status text,
    PRIMARY KEY (
        time_bucket,
        execution_time,
        job_id
    )
) WITH CLUSTERING ORDER BY (
    execution_time ASC,
    job_id ASC
);
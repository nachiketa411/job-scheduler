$version: "2"

namespace com.nachiketa.scheduler

structure JobExecution {
    @required
    jobId: JobId

    @required
    executionTime: Timestamp

    @required
    status: ExecutionStatus

    @required
    attempt: Integer

    startedAt: Timestamp

    completedAt: Timestamp

    error: String
}

enum ExecutionStatus {
    PENDING
    QUEUED
    RUNNING
    SUCCEEDED
    FAILED
    RETRYING
}

$version: "2"

namespace com.nachiketa.scheduler

string JobId

structure Job {
    @required
    jobId: JobId

    @required
    name: String

    @required
    taskId: TaskId

    @required
    parameters: Document

    @required
    schedule: Schedule

    @required
    createdAt: Timestamp
}

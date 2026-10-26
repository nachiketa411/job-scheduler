$version: "2"

namespace com.nachiketa.scheduler

@http(method: "POST", uri: "/jobs", code: 201)
operation CreateJob {
    input: CreateJobInput
    output: CreateJobOutput
}

structure CreateJobInput {
    @required
    name: String

    @required
    taskId: TaskId

    @required
    parameters: Document

    @required
    schedule: Schedule
}

structure CreateJobOutput {
    @required
    job: Job
}

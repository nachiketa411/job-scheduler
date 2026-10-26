$version: "2"

namespace com.nachiketa.scheduler

@readonly
@http(method: "GET", uri: "/jobs/{jobId}", code: 200)
operation GetJob {
    input: GetJobInput
    output: GetJobOutput
    errors: [
        JobNotFoundException
    ]
}

structure GetJobInput {
    @httpLabel
    @required
    jobId: String
}

structure GetJobOutput {
    @required
    job: Job
}

package com.nachiketa.scheduler.api.service;

import com.nachiketa.scheduler.api.repository.IJobExecutionRepository;
import com.nachiketa.scheduler.api.repository.IJobRepository;
import com.nachiketa.scheduler.api.repository.ITaskRepository;
import com.nachiketa.scheduler.api.service.schedule.NextExecutionCalculator;
import com.nachiketa.scheduler.api.service.validation.ParameterValidator;
import com.nachiketa.scheduler.api.service.validation.ScheduleValidator;
import com.nachiketa.scheduler.model.CreateJobInput;
import com.nachiketa.scheduler.model.CreateJobOutput;
import com.nachiketa.scheduler.model.ExecutionStatus;
import com.nachiketa.scheduler.model.GetJobInput;
import com.nachiketa.scheduler.model.GetJobOutput;
import com.nachiketa.scheduler.model.Job;
import com.nachiketa.scheduler.model.JobExecution;
import com.nachiketa.scheduler.model.JobNotFoundException;
import com.nachiketa.scheduler.model.ParameterType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class JobServiceImpl implements IJobService {

    private final ITaskRepository taskRepository;
    private final IJobRepository jobRepository;
    private final IJobExecutionRepository jobExecutionRepository;

    private final ParameterValidator parameterValidator;
    private final ScheduleValidator scheduleValidator;
    private final NextExecutionCalculator nextExecutionCalculator;

    public JobServiceImpl(
            ITaskRepository taskRepository,
            IJobRepository jobRepository,
            IJobExecutionRepository jobExecutionRepository,
            ParameterValidator parameterValidator,
            ScheduleValidator scheduleValidator,
            NextExecutionCalculator nextExecutionCalculator
    ) {
        this.taskRepository = taskRepository;
        this.jobRepository = jobRepository;
        this.jobExecutionRepository = jobExecutionRepository;
        this.parameterValidator = parameterValidator;
        this.scheduleValidator = scheduleValidator;
        this.nextExecutionCalculator = nextExecutionCalculator;
    }

    public CreateJobOutput createJob(CreateJobInput input) {
        UUID taskId = UUID.fromString(
                input.getTaskId()
        );

        Map<String, ParameterType> parameterSchema =
                taskRepository.findParameterSchema(taskId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Task does not exist: "
                                                + taskId
                                )
                        );

        parameterValidator.validate(
                parameterSchema,
                input.getParameters()
        );

        scheduleValidator.validate(
                input.getSchedule()
        );

        Job job = buildJob(input);
        jobRepository.save(job);

        JobExecution firstExecution = buildFirstExecution(job);
        jobExecutionRepository.save(firstExecution);

        return CreateJobOutput.builder()
                .job(job)
                .build();
    }

    public GetJobOutput getJob(GetJobInput input) {
        Job job = jobRepository.findById(
                    UUID.fromString(input.getJobId())
                )
                .orElseThrow(() ->
                        JobNotFoundException.builder()
                                .message("Job not found: " + input.getJobId())
                                .build()
                );
        return GetJobOutput.builder()
                .job(job)
                .build();
    }

    private Job buildJob(CreateJobInput input) {

        return Job.builder()
                .jobId(UUID.randomUUID().toString())
                .name(input.getName())
                .taskId(input.getTaskId())
                .parameters(input.getParameters())
                .schedule(input.getSchedule())
                .createdAt(Instant.now())
                .build();
    }

    private JobExecution buildFirstExecution(Job job) {

        Instant executionTime = nextExecutionCalculator.calculateFirstExecution(
                job.getSchedule()
        );

        return JobExecution.builder()
                .jobId(job.getJobId())
                .executionTime(executionTime)
                .status(ExecutionStatus.PENDING)
                .attempt(0)
                .build();
    }
}

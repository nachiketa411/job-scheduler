package com.nachiketa.scheduler.api.service;

import com.nachiketa.scheduler.api.exception.InvalidRequestException;
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
import com.nachiketa.scheduler.model.Schedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.smithy.java.core.serde.document.Document;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class JobServiceImplTest {

    @Mock
    private IJobRepository jobRepository;

    @Mock
    private ITaskRepository taskRepository;

    @Mock
    private ParameterValidator parameterValidator;

    @Mock
    private ScheduleValidator scheduleValidator;

    @Mock
    private NextExecutionCalculator nextExecutionCalculator;

    private JobServiceImpl jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobServiceImpl(
                taskRepository,
                jobRepository,
                parameterValidator,
                scheduleValidator,
                nextExecutionCalculator
        );
    }

    /**
     * Verifies that a job returned by the repository is wrapped
     * and returned correctly as a GetJobOutput.
     */
    @Test
    void getJob_whenJobExists_returnsJob() {
        UUID jobId = UUID.randomUUID();

        Job job = mock(Job.class);

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.of(job));

        GetJobInput input = GetJobInput.builder()
                .jobId(jobId.toString())
                .build();

        GetJobOutput output = jobService.getJob(input);

        assertNotNull(output);
        assertSame(job, output.getJob());

        verify(jobRepository).findById(jobId);
    }

    /**
     * Verifies that requesting a job that does not exist results
     * in a JobNotFoundException with the expected error message.
     */
    @Test
    void getJob_whenJobDoesNotExist_throwsJobNotFoundException() {
        UUID jobId = UUID.randomUUID();

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.empty());

        GetJobInput input = GetJobInput.builder()
                .jobId(jobId.toString())
                .build();

        JobNotFoundException exception = assertThrows(
                JobNotFoundException.class,
                () -> jobService.getJob(input)
        );

        assertEquals(
                "Job not found: " + jobId,
                exception.getMessage()
        );

        verify(jobRepository).findById(jobId);
    }

    /**
     * Verifies the main create-job workflow for valid input:
     * the task exists, parameters and schedule are validated,
     * the job is persisted, and its first execution is persisted.
     */
    @Test
    void createJob_whenInputIsValid_validatesInputAndPersistsJob() {
        UUID taskId = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        Map<String, ParameterType> parameterSchema = mock(Map.class);
        Document parameters = mock(Document.class);

        Schedule schedule = mock(Schedule.class);
        CreateJobInput input = mock(CreateJobInput.class);

        when(input.getTaskId()).thenReturn(taskId.toString());
        when(input.getName()).thenReturn("weekly-email");
        when(input.getParameters()).thenReturn(parameters);
        when(input.getSchedule()).thenReturn(schedule);

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(Optional.of(parameterSchema));

        Instant executionTime =
                Instant.parse("2026-10-30T15:30:00Z");

        when(nextExecutionCalculator.calculateFirstExecution(schedule))
                .thenReturn(executionTime);

        CreateJobOutput output = jobService.createJob(input);

        assertNotNull(output);
        assertNotNull(output.getJob());

        verify(taskRepository)
                .findParameterSchema(taskId);

        verify(parameterValidator)
                .validate(
                        parameterSchema,
                        parameters
                );

        verify(scheduleValidator)
                .validate(schedule);

        verify(jobRepository)
                .saveWithFirstExecution(
                        any(Job.class),
                        any(JobExecution.class)
                );
    }

    /**
     * Verifies that createJob constructs and persists a Job
     * containing the values supplied by the caller as well as
     * generated fields such as jobId and createdAt.
     */
    @Test
    void createJob_whenInputIsValid_persistsCorrectJob() {
        UUID taskId = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        Map<String, ParameterType> parameterSchema = mock(Map.class);
        Document parameters = mock(Document.class);

        Schedule schedule = mock(Schedule.class);
        CreateJobInput input = mock(CreateJobInput.class);

        when(input.getTaskId()).thenReturn(taskId.toString());
        when(input.getName()).thenReturn("weekly-email");
        when(input.getParameters()).thenReturn(parameters);
        when(input.getSchedule()).thenReturn(schedule);

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(Optional.of(parameterSchema));

        when(nextExecutionCalculator.calculateFirstExecution(schedule))
                .thenReturn(
                        Instant.parse("2026-10-30T15:30:00Z")
                );

        jobService.createJob(input);

        ArgumentCaptor<Job> jobCaptor =
                ArgumentCaptor.forClass(Job.class);

        verify(jobRepository)
                .saveWithFirstExecution(
                        jobCaptor.capture(),
                        any(JobExecution.class)
                );

        Job savedJob = jobCaptor.getValue();

        assertNotNull(savedJob.getJobId());
        assertDoesNotThrow(
                () -> UUID.fromString(savedJob.getJobId())
        );

        assertEquals(
                "weekly-email",
                savedJob.getName()
        );

        assertEquals(
                taskId.toString(),
                savedJob.getTaskId()
        );

        assertSame(
                schedule,
                savedJob.getSchedule()
        );

        assertSame(
                parameters,
                savedJob.getParameters()
        );

        assertNotNull(savedJob.getCreatedAt());
    }

    /**
     * Verifies that creating a valid job also creates its first
     * pending JobExecution using the execution time calculated
     * from the job's schedule.
     */
    @Test
    void createJob_whenInputIsValid_createsFirstExecution() {
        UUID taskId = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        Map<String, ParameterType> parameterSchema = mock(Map.class);
        Document parameters = mock(Document.class);

        Schedule schedule = mock(Schedule.class);
        CreateJobInput input = mock(CreateJobInput.class);

        when(input.getTaskId()).thenReturn(taskId.toString());
        when(input.getName()).thenReturn("weekly-email");
        when(input.getParameters()).thenReturn(parameters);
        when(input.getSchedule()).thenReturn(schedule);

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(Optional.of(parameterSchema));

        Instant executionTime =
                Instant.parse("2026-10-30T15:30:00Z");

        when(nextExecutionCalculator.calculateFirstExecution(schedule))
                .thenReturn(executionTime);

        CreateJobOutput output =
                jobService.createJob(input);

        ArgumentCaptor<JobExecution> executionCaptor =
                ArgumentCaptor.forClass(JobExecution.class);

        verify(jobRepository)
                .saveWithFirstExecution(
                        any(Job.class),
                        executionCaptor.capture()
                );

        JobExecution execution =
                executionCaptor.getValue();

        assertEquals(
                output.getJob().getJobId(),
                execution.getJobId()
        );

        assertEquals(
                executionTime,
                execution.getExecutionTime()
        );

        assertEquals(
                ExecutionStatus.PENDING,
                execution.getStatus()
        );

        assertEquals(
                0,
                execution.getAttempt()
        );
    }

    /**
     * Verifies that job creation fails immediately when the referenced
     * task does not exist and that no validation or persistence work
     * occurs after the task lookup fails.
     */
    @Test
    void createJob_whenTaskDoesNotExist_throwsInvalidRequestException() {
        UUID taskId = UUID.randomUUID();

        CreateJobInput input = mock(CreateJobInput.class);

        when(input.getTaskId())
                .thenReturn(taskId.toString());

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(Optional.empty());

        InvalidRequestException exception =
                assertThrows(
                        InvalidRequestException.class,
                        () -> jobService.createJob(input)
                );

        assertEquals(
                "Task does not exist: " + taskId,
                exception.getMessage()
        );

        verify(taskRepository)
                .findParameterSchema(taskId);

        verifyNoInteractions(
                parameterValidator,
                scheduleValidator,
                nextExecutionCalculator,
                jobRepository
        );
    }

    /**
     * Verifies that when parameter validation fails, job creation stops immediately
     * and neither the Job nor its first JobExecution is persisted.
     */
    @Test
    void createJob_whenParameterValidationFails_doesNotPersistAnything() {
        UUID taskId = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        Map<String, ParameterType> parameterSchema = mock(Map.class);

        Document parameters = mock(Document.class);
        CreateJobInput input = mock(CreateJobInput.class);

        when(input.getTaskId())
                .thenReturn(taskId.toString());

        when(input.getParameters())
                .thenReturn(parameters);

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(Optional.of(parameterSchema));

        doThrow(new IllegalArgumentException("Invalid parameters"))
                .when(parameterValidator)
                .validate(parameterSchema, parameters);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> jobService.createJob(input)
        );

        assertEquals(
                "Invalid parameters",
                exception.getMessage()
        );

        verify(taskRepository)
                .findParameterSchema(taskId);

        verify(parameterValidator)
                .validate(parameterSchema, parameters);

        verifyNoInteractions(
                scheduleValidator,
                nextExecutionCalculator,
                jobRepository
        );
    }

    /**
     * Verifies that when schedule validation fails, the job is not persisted
     * and no JobExecution is created.
     */
    @Test
    void createJob_whenScheduleValidationFails_doesNotPersistAnything() {
        UUID taskId = UUID.randomUUID();

        @SuppressWarnings("unchecked")
        Map<String, ParameterType> parameterSchema = mock(Map.class);

        Document parameters = mock(Document.class);
        Schedule schedule = mock(Schedule.class);
        CreateJobInput input = mock(CreateJobInput.class);

        when(input.getTaskId()).thenReturn(taskId.toString());
        when(input.getParameters()).thenReturn(parameters);
        when(input.getSchedule()).thenReturn(schedule);

        when(taskRepository.findParameterSchema(taskId))
                .thenReturn(Optional.of(parameterSchema));

        doThrow(new IllegalArgumentException("Invalid schedule"))
                .when(scheduleValidator)
                .validate(schedule);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> jobService.createJob(input)
        );

        assertEquals(
                "Invalid schedule",
                exception.getMessage()
        );

        verify(taskRepository)
                .findParameterSchema(taskId);

        verify(parameterValidator)
                .validate(parameterSchema, parameters);

        verify(scheduleValidator)
                .validate(schedule);

        verifyNoInteractions(
                nextExecutionCalculator,
                jobRepository
        );
    }

    /**
     * Verifies that a malformed taskId is rejected as an invalid request
     * before any repository or validation work occurs.
     */
    @Test
    void createJob_whenTaskIdIsNotUuid_throwsInvalidRequestException() {
        CreateJobInput input = mock(CreateJobInput.class);

        when(input.getTaskId())
                .thenReturn("not-a-uuid");

        InvalidRequestException exception = assertThrows(
                InvalidRequestException.class,
                () -> jobService.createJob(input)
        );

        assertEquals(
                "taskId must be a valid UUID: not-a-uuid",
                exception.getMessage()
        );

        verifyNoInteractions(
                taskRepository,
                parameterValidator,
                scheduleValidator,
                nextExecutionCalculator,
                jobRepository
        );
    }

    /**
     * Verifies that a malformed jobId is rejected as an invalid request
     * without querying the repository.
     */
    @Test
    void getJob_whenJobIdIsNotUuid_throwsInvalidRequestException() {
        GetJobInput input = GetJobInput.builder()
                .jobId("not-a-uuid")
                .build();

        assertThrows(
                InvalidRequestException.class,
                () -> jobService.getJob(input)
        );

        verifyNoInteractions(jobRepository);
    }
}

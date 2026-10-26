package com.nachiketa.scheduler.api.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.nachiketa.scheduler.api.mapper.ParameterDocumentMapper;
import com.nachiketa.scheduler.model.Job;
import com.nachiketa.scheduler.model.ParameterType;
import com.nachiketa.scheduler.model.Schedule;
import com.nachiketa.scheduler.model.ScheduleType;
import org.springframework.stereotype.Repository;
import software.amazon.smithy.java.core.serde.document.Document;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JobRepositoryImpl implements IJobRepository {

    private final CqlSession session;
    private final ITaskRepository taskRepository;
    private final ParameterDocumentMapper parameterMapper;

    public JobRepositoryImpl(
            CqlSession session,
            ITaskRepository taskRepository,
            ParameterDocumentMapper parameterMapper
    ) {
        this.session = session;
        this.taskRepository = taskRepository;
        this.parameterMapper = parameterMapper;
    }

    @Override
    public void save(Job job) {
        Map<String, String> parameters =
                parameterMapper.toStorage(
                        job.getParameters()
                );

        session.execute(
                SimpleStatement.newInstance(
                        """
                                INSERT INTO jobs (
                                    job_id,
                                    name,
                                    task_id,
                                    parameters,
                                    schedule_type,
                                    schedule_expression,
                                    created_at
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?)
                                """,
                        UUID.fromString(job.getJobId()),
                        job.getName(),
                        UUID.fromString(job.getTaskId()),
                        parameters,
                        job.getSchedule().getType().getValue(),
                        job.getSchedule().getExpression(),
                        job.getCreatedAt()
                )
        );
    }

    @Override
    public Optional<Job> findById(UUID jobId) {
        Row row = session.execute(
                SimpleStatement.newInstance(
                        """
                                SELECT
                                    job_id,
                                    name,
                                    task_id,
                                    parameters,
                                    schedule_type,
                                    schedule_expression,
                                    created_at
                                FROM jobs
                                WHERE job_id = ?
                                """,
                        jobId
                )
        ).one();

        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(toJob(row));
    }

    private Job toJob(Row row) {
        UUID jobId = Objects.requireNonNull(
                row.getUuid("job_id"),
                "job_id cannot be null"
        );
        UUID taskId = Objects.requireNonNull(
                row.getUuid("task_id"),
                "task_id cannot be null"
        );
        String jobName = row.getString("name");
        Instant createdAt = row.getInstant("created_at");

        Map<String, String> storedParameters =
                row.getMap(
                        "parameters",
                        String.class,
                        String.class
                );

        Map<String, ParameterType> parameterSchema =
                taskRepository.findParameterSchema(taskId)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Task does not exist: "
                                                + taskId
                                )
                        );

        Document parameters =
                parameterMapper.fromStorage(
                        storedParameters != null
                                ? storedParameters
                                : Map.of(),
                        parameterSchema
                );

        String scheduleType = Objects.requireNonNull(
                row.getString("schedule_type"),
                "schedule_type cannot be null"
        );
        String scheduleExpression = row.getString("schedule_expression");

        Schedule schedule = Schedule.builder()
                .type(ScheduleType.from(scheduleType))
                .expression(scheduleExpression)
                .build();

        return Job.builder()
                .jobId(jobId.toString())
                .name(jobName)
                .taskId(taskId.toString())
                .parameters(parameters)
                .schedule(schedule)
                .createdAt(createdAt)
                .build();
    }
}

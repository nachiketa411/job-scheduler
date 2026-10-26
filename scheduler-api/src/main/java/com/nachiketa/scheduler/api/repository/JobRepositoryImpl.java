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
        UUID jobId = row.getUuid("job_id");
        UUID taskId = row.getUuid("task_id");
        String jobName = row.getString("name");
        Instant created_at = row.getInstant("created_at");
        assert jobId != null;
        assert taskId != null;

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

        String schedule_type = row.getString("schedule_type");
        String schedule_expression = row.getString("schedule_expression");
        assert schedule_type != null;

        Schedule schedule = Schedule.builder()
                .type(ScheduleType.from(schedule_type))
                .expression(schedule_expression)
                .build();

        return Job.builder()
                .jobId(jobId.toString())
                .name(jobName)
                .taskId(taskId.toString())
                .parameters(parameters)
                .schedule(schedule)
                .createdAt(created_at)
                .build();
    }
}

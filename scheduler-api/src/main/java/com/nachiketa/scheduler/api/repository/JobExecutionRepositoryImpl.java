package com.nachiketa.scheduler.api.repository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.nachiketa.scheduler.model.ExecutionStatus;
import com.nachiketa.scheduler.model.JobExecution;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JobExecutionRepositoryImpl implements IJobExecutionRepository {

    private static final long BUCKET_SIZE_SECONDS = 300;
    private final CqlSession session;

    public JobExecutionRepositoryImpl(
            CqlSession session
    ) {
        this.session = session;
    }

    @Override
    public void save(JobExecution execution) {
        Instant executionTime = execution.getExecutionTime();
        Instant timeBucket = calculateTimeBucket(executionTime);

        session.execute(
                SimpleStatement.newInstance(
                        """
                                INSERT INTO job_executions (
                                    time_bucket,
                                    execution_time,
                                    job_id,
                                    status,
                                    attempt,
                                    started_at,
                                    completed_at,
                                    error
                                )
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        timeBucket,
                        executionTime,
                        UUID.fromString(execution.getJobId()),
                        execution.getStatus().getValue(),
                        execution.getAttempt(),
                        execution.getStartedAt(),
                        execution.getCompletedAt(),
                        execution.getError()
                )
        );
    }

    @Override
    public Optional<JobExecution> find(
            UUID jobId,
            Instant executionTime
    ) {
        Instant timeBucket = calculateTimeBucket(executionTime);
        Row row = session.execute(
                SimpleStatement.newInstance(
                        """
                                SELECT
                                    execution_time,
                                    job_id,
                                    status,
                                    attempt,
                                    started_at,
                                    completed_at,
                                    error
                                FROM job_executions
                                WHERE time_bucket = ?
                                AND execution_time = ?
                                AND job_id = ?
                                """,
                        timeBucket,
                        executionTime,
                        jobId
                )
        ).one();

        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(
                toJobExecution(row)
        );
    }

    @Override
    public List<JobExecution> findInWindow(
            Instant timeBucket,
            Instant from,
            Instant to
    ) {
        ResultSet resultSet = session.execute(
                SimpleStatement.newInstance(
                        """
                        SELECT
                            execution_time,
                            job_id,
                            status,
                            attempt,
                            started_at,
                            completed_at,
                            error
                        FROM job_executions
                        WHERE time_bucket = ?
                          AND execution_time >= ?
                          AND execution_time < ?
                        """,
                        timeBucket,
                        from,
                        to
                )
        );

        List<JobExecution> executions = new ArrayList<>();
        for (Row row: resultSet) {
            executions.add(toJobExecution(row));
        }

        return executions;
    }

    private JobExecution toJobExecution(Row row) {
        UUID jobId = row.getUuid("job_id");
        String executionStatus = row.getString("status");

        assert jobId != null;
        assert executionStatus != null;



        return JobExecution.builder()
                .jobId(jobId.toString())
                .executionTime(
                        row.getInstant(
                                "execution_time"
                        )
                )
                .status(
                        ExecutionStatus.from(executionStatus)
                )
                .attempt(
                        row.getInt("attempt")
                )
                .startedAt(
                        row.getInstant("started_at")
                )
                .completedAt(
                        row.getInstant(
                                "completed_at"
                        )
                )
                .error(
                        row.getString("error")
                )
                .build();
    }

    private Instant calculateTimeBucket(
            Instant executionTime) {

        long epochSeconds =
                executionTime.getEpochSecond();

        long bucketStart =
                Math.floorDiv(
                        epochSeconds,
                        BUCKET_SIZE_SECONDS
                ) * BUCKET_SIZE_SECONDS;

        return Instant.ofEpochSecond(
                bucketStart
        );
    }
}

package com.nachiketa.scheduler.api.repository;

import com.nachiketa.scheduler.model.JobExecution;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IJobExecutionRepository {

    void save(JobExecution execution);

    Optional<JobExecution> find(
            UUID jobId,
            Instant executionTime
    );

    List<JobExecution> findInWindow(
            Instant timeBucket,
            Instant from,
            Instant to
    );
}

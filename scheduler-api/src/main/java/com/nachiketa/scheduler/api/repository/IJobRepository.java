package com.nachiketa.scheduler.api.repository;

import com.nachiketa.scheduler.model.Job;
import com.nachiketa.scheduler.model.JobExecution;

import java.util.Optional;
import java.util.UUID;

public interface IJobRepository {

    void save(Job job);

    void saveWithFirstExecution(Job job, JobExecution firstExecution);

    Optional<Job> findById(UUID jobId);
}

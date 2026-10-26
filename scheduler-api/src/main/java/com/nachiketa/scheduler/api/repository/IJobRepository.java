package com.nachiketa.scheduler.api.repository;

import com.nachiketa.scheduler.model.Job;

import java.util.Optional;
import java.util.UUID;

public interface IJobRepository {

    void save(Job job);

    Optional<Job> findById(UUID jobId);
}

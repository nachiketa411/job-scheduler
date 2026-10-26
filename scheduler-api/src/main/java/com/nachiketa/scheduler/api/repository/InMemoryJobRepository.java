package com.nachiketa.scheduler.api.repository;

import com.nachiketa.scheduler.model.Job;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryJobRepository {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();

    public void save(Job job) {
        jobs.put(job.getJobId(), job);
    }

    public Optional<Job> findById(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

}

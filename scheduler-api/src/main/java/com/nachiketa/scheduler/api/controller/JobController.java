package com.nachiketa.scheduler.api.controller;

import com.nachiketa.scheduler.api.service.IJobService;
import com.nachiketa.scheduler.model.CreateJobInput;
import com.nachiketa.scheduler.model.CreateJobOutput;
import com.nachiketa.scheduler.model.GetJobInput;
import com.nachiketa.scheduler.model.GetJobOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class JobController {
    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final IJobService jobService;

    public JobController(IJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/test")
    public String test() {
        return "Scheduler API is working";
    }

    @PostMapping
    public ResponseEntity<CreateJobOutput> createJob(
            @RequestBody CreateJobInput input
    ) {
        log.info("Creating job '{}' for task {}", input.getName(), input.getTaskId());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(jobService.createJob(input));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<GetJobOutput> getJob(
            @PathVariable String jobId
    ) {
        GetJobInput input = GetJobInput.builder()
                .jobId(jobId)
                .build();

        log.debug("Fetching job {}", jobId);
        return ResponseEntity.ok(
                jobService.getJob(input)
        );
    }

}
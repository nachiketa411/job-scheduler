package com.nachiketa.scheduler.api.controller;

import com.nachiketa.scheduler.api.service.IJobService;
import com.nachiketa.scheduler.model.CreateJobInput;
import com.nachiketa.scheduler.model.GetJobInput;
import com.nachiketa.scheduler.model.GetJobOutput;
import com.nachiketa.scheduler.model.Job;
import com.nachiketa.scheduler.model.JobNotFoundException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
public class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IJobService jobService;

    /**
     * Verifies that GET /jobs/{jobId} returns HTTP 200
     * when the requested job exists.
     */
    @Test
    void getJob_whenJobExists_returns200() throws Exception {
        String jobId = UUID.randomUUID().toString();

        Job job = mock(Job.class);

        GetJobOutput output = GetJobOutput.builder()
                .job(job)
                .build();

        when(jobService.getJob(any(GetJobInput.class)))
                .thenReturn(output);

        mockMvc.perform(
                        get("/jobs/{jobId}", jobId)
                )
                .andExpect(status().isOk());
    }

    /**
     * Verifies that the jobId path variable is correctly converted
     * into the Smithy GetJobInput passed to the service layer.
     */
    @Test
    void getJob_passesPathVariableToService() throws Exception {
        String jobId = UUID.randomUUID().toString();

        Job job = mock(Job.class);

        GetJobOutput output = GetJobOutput.builder()
                .job(job)
                .build();

        when(jobService.getJob(any(GetJobInput.class)))
                .thenReturn(output);

        mockMvc.perform(
                        get("/jobs/{jobId}", jobId)
                )
                .andExpect(status().isOk());

        ArgumentCaptor<GetJobInput> inputCaptor =
                ArgumentCaptor.forClass(GetJobInput.class);

        verify(jobService)
                .getJob(inputCaptor.capture());

        assertEquals(
                jobId,
                inputCaptor.getValue().getJobId()
        );
    }

    /**
     * Verifies that JobNotFoundException from the service layer
     * is translated into an HTTP 404 response.
     */
    @Test
    void getJob_whenJobDoesNotExist_returns404() throws Exception {
        String jobId = UUID.randomUUID().toString();

        when(jobService.getJob(any(GetJobInput.class)))
                .thenThrow(
                        JobNotFoundException.builder()
                                .message("Job not found: " + jobId)
                                .build()
                );

        mockMvc.perform(
                        get("/jobs/{jobId}", jobId)
                )
                .andExpect(status().isNotFound());
    }

    /**
     * Verifies that POST /jobs accepts a valid job request
     * and returns HTTP 201 Created.
     */
    @Test
    void createJob_withValidJson_returns201() throws Exception {
        String requestBody = """
            {
              "name": "weekly-email",
              "taskId": "f822bb44-7e0d-4da6-a0d9-9180fc318bba",
              "parameters": {
                "recipient": "user@example.com",
                "retryCount": "3",
                "subject": "Test subject",
                "body": "Test body"
              },
              "schedule": {
                "type": "CRON",
                "expression": "30 15 * * 5"
              }
            }
            """;

        when(jobService.createJob(any(CreateJobInput.class)))
                .thenReturn(null);

        mockMvc.perform(
                        post("/jobs")
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(status().isCreated());
    }

    /**
     * Verifies that POST /jobs correctly deserializes the request JSON
     * into the generated Smithy CreateJobInput passed to the service.
     */
    @Test
    void createJob_deserializesJsonIntoSmithyInput() throws Exception {
        String taskId =
                "f822bb44-7e0d-4da6-a0d9-9180fc318bba";

        String requestBody = """
            {
              "name": "weekly-email",
              "taskId": "%s",
              "parameters": {
                "recipient": "user@example.com",
                "retryCount": "3",
                "subject": "Test subject",
                "body": "Test body"
              },
              "schedule": {
                "type": "CRON",
                "expression": "30 15 * * 5"
              }
            }
            """.formatted(taskId);

        when(jobService.createJob(any(CreateJobInput.class)))
                .thenReturn(null);

        mockMvc.perform(
                        post("/jobs")
                                .contentType("application/json")
                                .content(requestBody)
                )
                .andExpect(status().isCreated());

        ArgumentCaptor<CreateJobInput> inputCaptor =
                ArgumentCaptor.forClass(CreateJobInput.class);

        verify(jobService)
                .createJob(inputCaptor.capture());

        CreateJobInput input = inputCaptor.getValue();

        assertEquals(
                "weekly-email",
                input.getName()
        );

        assertEquals(
                taskId,
                input.getTaskId()
        );

        assertEquals(
                "CRON",
                input.getSchedule().getType().toString()
        );

        assertEquals(
                "30 15 * * 5",
                input.getSchedule().getExpression()
        );
    }
}

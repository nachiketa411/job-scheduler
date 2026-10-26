package com.nachiketa.scheduler.api.service;

import com.nachiketa.scheduler.model.CreateJobInput;
import com.nachiketa.scheduler.model.CreateJobOutput;
import com.nachiketa.scheduler.model.GetJobInput;
import com.nachiketa.scheduler.model.GetJobOutput;

public interface IJobService {

    CreateJobOutput createJob(CreateJobInput input);

    GetJobOutput getJob(GetJobInput input);
}

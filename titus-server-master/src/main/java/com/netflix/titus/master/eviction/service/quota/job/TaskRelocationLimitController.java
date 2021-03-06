/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.titus.master.eviction.service.quota.job;

import java.util.List;
import java.util.Optional;

import com.netflix.titus.api.eviction.model.EvictionQuota;
import com.netflix.titus.api.jobmanager.model.job.Job;
import com.netflix.titus.api.jobmanager.model.job.Task;
import com.netflix.titus.api.jobmanager.model.job.disruptionbudget.DisruptionBudget;
import com.netflix.titus.api.jobmanager.model.job.disruptionbudget.RelocationLimitDisruptionBudgetPolicy;
import com.netflix.titus.api.jobmanager.service.JobManagerException;
import com.netflix.titus.api.jobmanager.service.V3JobOperations;
import com.netflix.titus.api.model.Level;
import com.netflix.titus.api.model.reference.Reference;
import com.netflix.titus.common.util.tuple.Pair;
import com.netflix.titus.master.eviction.service.quota.ConsumptionResult;
import com.netflix.titus.master.eviction.service.quota.QuotaController;

public class TaskRelocationLimitController implements QuotaController<Job<?>> {

    private final Job<?> job;
    private final V3JobOperations jobOperations;
    private final EffectiveJobDisruptionBudgetResolver budgetResolver;
    private final int perTaskLimit;

    private static final ConsumptionResult TASK_NOT_FOUND = ConsumptionResult.rejected("Task not found");

    private final ConsumptionResult taskLimitExceeded;

    public TaskRelocationLimitController(Job<?> job,
                                         V3JobOperations jobOperations,
                                         EffectiveJobDisruptionBudgetResolver budgetResolver) {
        this.job = job;
        this.jobOperations = jobOperations;
        this.budgetResolver = budgetResolver;
        this.perTaskLimit = computePerTaskLimit(budgetResolver.resolve(job));
        this.taskLimitExceeded = buildTaskRelocationLimitExceeded();
    }

    private TaskRelocationLimitController(Job<?> updatedJob,
                                          int perTaskLimit,
                                          TaskRelocationLimitController previous) {
        this.job = updatedJob;
        this.jobOperations = previous.jobOperations;
        this.perTaskLimit = perTaskLimit;
        this.budgetResolver = previous.budgetResolver;
        this.taskLimitExceeded = buildTaskRelocationLimitExceeded();
    }

    @Override
    public EvictionQuota getQuota(Reference reference) {
        if (reference.getLevel() == Level.Task) {
            return getTaskQuota(reference);
        }
        return getJobQuota(reference);
    }

    private EvictionQuota getJobQuota(Reference jobReference) {
        EvictionQuota.Builder quotaBuilder = EvictionQuota.newBuilder().withReference(jobReference);

        List<Task> tasks;
        try {
            tasks = jobOperations.getTasks(job.getId());
        } catch (JobManagerException e) {
            return quotaBuilder
                    .withQuota(0)
                    .withMessage("Internal error: %s", e.getMessage())
                    .build();
        }

        int quota = 0;
        for (Task task : tasks) {
            if (task.getEvictionResubmitNumber() < perTaskLimit) {
                quota++;
            }
        }

        return quota > 0
                ? quotaBuilder.withQuota(quota).withMessage("Per task limit is %s", perTaskLimit).build()
                : quotaBuilder.withQuota(0).withMessage("Each task of the job reached its maximum eviction limit %s", perTaskLimit).build();
    }

    private EvictionQuota getTaskQuota(Reference taskReference) {
        String taskId = taskReference.getName();

        EvictionQuota.Builder quotaBuilder = EvictionQuota.newBuilder().withReference(taskReference);

        Optional<Pair<Job<?>, Task>> jobTaskOpt = jobOperations.findTaskById(taskId);
        if (!jobTaskOpt.isPresent()) {
            return quotaBuilder.withQuota(0).withMessage("Task not found").build();
        }
        Task task = jobTaskOpt.get().getRight();

        int counter = task.getEvictionResubmitNumber();
        if (counter < perTaskLimit) {
            return quotaBuilder
                    .withQuota(1)
                    .withMessage("Per task limit is %s, and restart count is %s", perTaskLimit, counter)
                    .build();
        }

        return quotaBuilder.withQuota(0).withMessage(taskLimitExceeded.getRejectionReason().get()).build();
    }

    @Override
    public ConsumptionResult consume(String taskId) {
        Optional<Pair<Job<?>, Task>> jobTaskPair = jobOperations.findTaskById(taskId);
        if (!jobTaskPair.isPresent()) {
            return TASK_NOT_FOUND;
        }
        Task task = jobTaskPair.get().getRight();

        int counter = task.getEvictionResubmitNumber();
        if (counter >= perTaskLimit) {
            return taskLimitExceeded;
        }
        return ConsumptionResult.approved();
    }

    @Override
    public void giveBackConsumedQuota(String taskId) {
    }

    @Override
    public TaskRelocationLimitController update(Job<?> updatedJob) {
        int perTaskLimit = computePerTaskLimit(budgetResolver.resolve(updatedJob));
        if (perTaskLimit == this.perTaskLimit) {
            return this;
        }
        return new TaskRelocationLimitController(updatedJob, perTaskLimit, this);
    }

    private static int computePerTaskLimit(DisruptionBudget budget) {
        RelocationLimitDisruptionBudgetPolicy policy = (RelocationLimitDisruptionBudgetPolicy) budget.getDisruptionBudgetPolicy();
        return policy.getLimit();
    }

    private ConsumptionResult buildTaskRelocationLimitExceeded() {
        return ConsumptionResult.rejected("Task relocation limit exceeded (limit=" + perTaskLimit + ')');
    }
}

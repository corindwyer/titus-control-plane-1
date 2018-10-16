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

package com.netflix.titus.supplementary.relocation.workflow.step;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.netflix.titus.supplementary.relocation.descheduler.DeschedulerService;
import com.netflix.titus.supplementary.relocation.model.DeschedulingResult;
import com.netflix.titus.supplementary.relocation.model.TaskRelocationPlan;

/**
 * Tasks to be relocated now are identified in this step. Termination of the selected tasks should not violate the
 * disruption budget constraints (unless explicitly requested).
 */
public class DeschedulerStep {

    private final DeschedulerService deschedulerService;

    public DeschedulerStep(DeschedulerService deschedulerService) {
        this.deschedulerService = deschedulerService;
    }

    /**
     * Accepts collection of tasks that must be relocated, and their relocation was planned ahead of time.
     * For certain scenarios ahead of planning is not possible or desirable. For example during agent defragmentation,
     * the defragmentation process must be down quickly, otherwise it may become quickly obsolete.
     *
     * @return a collection of tasks to terminate now. This collection may include tasks from the 'mustBeRelocatedTasks'
     * collection if their deadline has passed. It may also include tasks that were not planned ahead of time
     * for relocation.
     */
    public Map<String, TaskRelocationPlan> deschedule(Map<String, TaskRelocationPlan> tasksToEvict) {
        List<DeschedulingResult> deschedulingResult = deschedulerService.deschedule(tasksToEvict);

        return deschedulingResult.stream()
                .collect(Collectors.toMap(d -> d.getTask().getId(), DeschedulingResult::getTaskRelocationPlan));
    }
}

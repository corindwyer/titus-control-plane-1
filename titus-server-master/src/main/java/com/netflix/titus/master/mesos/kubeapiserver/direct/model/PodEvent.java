/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.titus.master.mesos.kubeapiserver.direct.model;

import java.util.Objects;
import java.util.Optional;

import com.netflix.titus.api.jobmanager.model.job.Task;
import com.netflix.titus.api.jobmanager.model.job.TaskStatus;
import io.kubernetes.client.openapi.models.V1Node;
import io.kubernetes.client.openapi.models.V1Pod;

public abstract class PodEvent {

    protected final String taskId;
    protected final V1Pod pod;

    protected PodEvent(V1Pod pod) {
        this.taskId = pod.getMetadata().getName();
        this.pod = pod;
    }

    public String getTaskId() {
        return taskId;
    }

    public V1Pod getPod() {
        return pod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PodEvent podEvent = (PodEvent) o;
        return Objects.equals(taskId, podEvent.taskId) &&
                Objects.equals(pod, podEvent.pod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId, pod);
    }

    @Override
    public String toString() {
        return "PodEvent{" +
                "taskId='" + taskId + '\'' +
                ", pod=" + pod +
                '}';
    }

    public static PodAddedEvent onAdd(V1Pod pod) {
        return new PodAddedEvent(pod);
    }

    public static PodUpdatedEvent onUpdate(V1Pod oldPod, V1Pod newPod, Optional<V1Node> node) {
        return new PodUpdatedEvent(oldPod, newPod, node);
    }

    public static PodDeletedEvent onDelete(V1Pod pod, boolean deletedFinalStateUnknown, Optional<V1Node> node) {
        return new PodDeletedEvent(pod, deletedFinalStateUnknown, node);
    }

    public static PodNotFoundEvent onPodNotFound(Task task, TaskStatus finalTaskStatus) {
        return new PodNotFoundEvent(task, finalTaskStatus);
    }
}

/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.titus.common.model.admission;

import com.netflix.archaius.api.annotations.DefaultValue;
import com.netflix.titus.common.model.sanitizer.ValidationError;

public interface AdmissionValidatorConfiguration {
    @DefaultValue("SOFT")
    String getErrorType();

    default ValidationError.Type toValidatorErrorType() {
        return ValidationError.Type.valueOf(getErrorType().trim().toUpperCase());
    }
}

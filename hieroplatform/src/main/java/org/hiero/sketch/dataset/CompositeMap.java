/*
 * Copyright (c) 2017 VMware Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
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
 *
 */

package org.hiero.sketch.dataset;

import org.hiero.sketch.dataset.api.IMap;

public class CompositeMap<T, S, V> implements IMap<T, V> {
    final IMap<T, S> first;
    final IMap<S, V> second;

    public CompositeMap(IMap<T, S> first, IMap<S, V> second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public V apply(T data) {
        S second = this.first.apply(data);
        return this.second.apply(second);
    }
}

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

package org.hiero.sketch.table;

import org.hiero.sketch.table.api.ContentsKind;
import org.hiero.sketch.table.api.IIntColumn;

import java.security.InvalidParameterException;

/**
 * Column of integers, implemented as an array of integers and a BitSet of missing values.
 */
public final class IntArrayColumn
        extends BaseArrayColumn
        implements IIntColumn {
    private final int[] data;

    private void validate() {
        if (this.description.kind != ContentsKind.Integer)
            throw new InvalidParameterException("Kind should be Integer " + this.description.kind);
    }

    public IntArrayColumn(final ColumnDescription description, final int size) {
        super(description, size);
        this.validate();
        this.data = new int[size];
    }

    public IntArrayColumn(final ColumnDescription description, final int[] data) {
        super(description, data.length);
        this.validate();
        this.data = data;
    }

    @Override
    public int sizeInRows() {
        return this.data.length;
    }

    @Override
    public int getInt(final int rowIndex) {
        return this.data[rowIndex];
    }

    public void set(final int rowIndex, final int value) {
        this.data[rowIndex] = value;
    }
}

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

package org.hiero.sketch;

import org.hiero.utils.IntArrayGenerator;
import org.hiero.sketch.table.IntArrayColumn;
import org.hiero.sketch.table.api.IndexComparator;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Test for IntArrayColumn class
 */
public class IntArrayTest {


    @Test
    public void testRandArray(){
        final int size = 1000;
        final int range = 1000;
        final IntArrayColumn col = IntArrayGenerator.getRandIntArray(size, range, "Test");
        final IndexComparator comp = col.getComparator();
        final Integer[] order = new Integer[size];
        for (int i = 0; i < size; i++) {
            order[i] = i;
        }
        Arrays.sort(order, comp);
        for (int i = 0; i < (size - 1); i++) {
            assertTrue(col.getInt(order[i]) <= col.getInt(order[i+1]));
        }
    }

    void checkContents(IntArrayColumn col) {
        for (int i = 0; i < col.sizeInRows(); i++) {
            if ((i % 5) == 0)
                assertTrue(col.isMissing(i));
            else {
                assertFalse(col.isMissing(i));
                assertEquals(i, col.getInt(i));
            }
        }
    }

    /* Test for constructor using length and no arrays*/
    @Test
    public void testIntArrayZero() {
        final int size = 100;
        final IntArrayColumn col = IntArrayGenerator.generateIntArray(size);
        assertEquals(col.sizeInRows(), size);
        checkContents(col);
    }

    /* Test for constructor using data array */
    @Test
    public void testIntArrayOne() {
        final int size = 100;

        final int[] data = new int[size];
        for (int i = 0; i < size; i++)
            data[i] = i;
        final IntArrayColumn col = new IntArrayColumn(IntArrayGenerator.desc, data);
        for (int i = 0; i < size; i++)
            if ((i % 5) == 0)
                col.setMissing(i);
        assertEquals(col.sizeInRows(), size);
        checkContents(col);
    }
}

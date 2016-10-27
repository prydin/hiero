package org.hiero.sketch;

import org.hiero.sketch.table.ColumnDescription;
import org.hiero.sketch.table.api.ContentsKind;
import org.hiero.sketch.table.IntArrayColumn;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;

public class ColumnPerfTest {
    @Test
    public void test() {
        final IntArrayColumn col;
        final int size = 1000000;
        final int testnum = 100;
        int temp = 0;

        final long[] results = new long[testnum];
        final long[] Cresults = new long[testnum];

        final ColumnDescription desc = new ColumnDescription("test", ContentsKind.Int, false);
        col = new IntArrayColumn(desc, size);
        for (int i=0; i < size; i++)
            col.set(i, i);
        for (int j=0; j<testnum; j++) {
            final long startTime = System.nanoTime();
            for (int i = 0; i < size; i++)
                temp += col.getInt(i);
            final long endTime = System.nanoTime();
            results[j] = endTime - startTime;
        }

        int temp1 = 0;
        final int[] Rcol = new int[size];
        for (int i=0; i < size; i++)
            Rcol[i]=i;
        for (int j=0; j<testnum; j++) {
            final long startTime = System.nanoTime();
            for (int i = 0; i < size; i++)
                temp1 += Rcol[i];
            final long endTime = System.nanoTime();
            Cresults[j] = endTime - startTime;
        }
        assertEquals(temp, temp1);
        System.out.println(temp);
        System.out.println("Comparing function call to Direct manimulation of Array");
        TestUtil.Percentiles(results);
        TestUtil.Percentiles(Cresults);
    }
}

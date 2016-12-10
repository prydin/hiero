package org.hiero.sketch;

import org.hiero.sketch.table.ColumnDescription;
import org.hiero.sketch.table.DoubleListColumn;
import org.hiero.sketch.table.api.ContentsKind;
import org.hiero.sketch.table.IntArrayColumn;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColumnTest {
    @Test
    public void testIntColumn() {
        final IntArrayColumn col;
        final int size = 100;

        final ColumnDescription desc = new ColumnDescription("test", ContentsKind.Int, false);
        col = new IntArrayColumn(desc, size);
        for (int i=0; i < size; i++)
            col.set(i, i);

        assertEquals( col.sizeInRows(), size );
        assertEquals( col.getInt(0), 0 );
        for (int i=0; i < size; i++)
            assertEquals(i, col.getInt(i));
        assertEquals( col.asDouble(0, null), 0.0, 1e-3 );
    }

    @Test
    public void testDoubleListColumn() {
        final DoubleListColumn col;
        final int size = 10000000;

        final ColumnDescription desc = new ColumnDescription("test", ContentsKind.Double, false);
        col = new DoubleListColumn(desc);
        for (int i=0; i < size; i++)
            col.append(i);


        assertEquals( col.sizeInRows(), size );
        assertEquals( col.getDouble(0), 0.0, 10e-3 );
        for (int i=0; i < size; i++)
            assertEquals((double)i, col.getDouble(i), 1e-3);
        assertEquals( col.asDouble(0, null), 0.0, 1e-3 );
    }
}
/*
 * Copyright (c) 2017 VMWare Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.hiero;

import org.hiero.sketch.dataset.ConcurrentSketch;
import org.hiero.sketch.dataset.TripleSketch;
import org.hiero.sketch.dataset.api.IDataSet;
import org.hiero.sketch.spreadsheet.*;
import org.hiero.sketch.table.RecordOrder;
import org.hiero.sketch.table.TableFilter;
import org.hiero.sketch.table.api.IColumn;
import org.hiero.sketch.table.api.ITable;
import org.hiero.utils.Converters;

import javax.annotation.Nullable;
import javax.websocket.Session;
import java.io.Serializable;
import java.util.function.Function;

public final class TableTarget extends RpcTarget {
    private final IDataSet<ITable> table;
    TableTarget(IDataSet<ITable> table) {
        this.table = table;
    }

    @HieroRpc
    void getSchema(RpcRequest request, Session session) {
        SummarySketch ss = new SummarySketch();
        this.runSketch(this.table, ss, request, session);
    }

    @HieroRpc
    void getTableView(RpcRequest request, Session session) {
        RecordOrder ro = request.parseArgs(RecordOrder.class);
        // TODO: number of rows should depend on screen size
        NextKSketch nk = new NextKSketch(ro, null, 20);
        this.runSketch(this.table, nk, request, session);
    }

    static class ColumnAndRange implements Serializable {
        String columnName = "";
        double min;
        double max;
        // view size
        double width;
        double height;
        // only used for histogram
        int bucketCount;
    }

    @HieroRpc
    void histogram(RpcRequest request, Session session) {
        ColumnAndRange info = request.parseArgs(ColumnAndRange.class);
        // TODO: use height in histogram computation
        int width = (int)info.width;
        if (info.min >= info.max)
            width = 1;
        BucketsDescriptionEqSize cdfBuckets = new BucketsDescriptionEqSize(info.min, info.max, width);
        Hist1DLightSketch cdf = new Hist1DLightSketch(cdfBuckets, info.columnName, null);
        BucketsDescriptionEqSize buckets = new BucketsDescriptionEqSize(info.min, info.max, info.bucketCount);
        Hist1DSketch sk = new Hist1DSketch(buckets, info.columnName, null);
        ConcurrentSketch<ITable, Histogram1DLight, Histogram1D> csk =
                new ConcurrentSketch<ITable, Histogram1DLight, Histogram1D>(cdf, sk);
        this.runSketch(this.table, csk, request, session);
    }

    static class ColPair {
        @Nullable
        ColumnAndRange first;
        @Nullable
        ColumnAndRange second;
    }

    @HieroRpc
    void histogram2D(RpcRequest request, Session session) {
        ColPair info = request.parseArgs(ColPair.class);
        Converters.checkNull(info.first);
        Converters.checkNull(info.second);
        int width = (int)info.first.width;
        if (info.first.min >= info.first.max)
            width = 1;
        BucketsDescriptionEqSize cdfBuckets =
                new BucketsDescriptionEqSize(info.first.min, info.first.max, width);
        Hist1DLightSketch cdf = new Hist1DLightSketch(cdfBuckets, info.first.columnName, null);

        BucketsDescriptionEqSize buckets1 = new BucketsDescriptionEqSize(
                info.first.min, info.first.max, info.first.bucketCount);
        Hist1DSketch sk1 = new Hist1DSketch(buckets1, info.first.columnName, null);

        BucketsDescriptionEqSize buckets2 = new BucketsDescriptionEqSize(
                info.second.min, info.second.max, info.second.bucketCount);
        Hist1DSketch sk2 = new Hist1DSketch(buckets2, info.second.columnName, null);

        TripleSketch<ITable, Histogram1DLight, Histogram1D, Histogram1D> csk =
                new TripleSketch<ITable, Histogram1DLight, Histogram1D, Histogram1D>(cdf, sk1, sk2);
        this.runSketch(this.table, csk, request, session);
    }

    static class Columns {
        String col1 = "";
        String col2 = "";
    }

    @HieroRpc
    void range2D(RpcRequest request, Session session) {
        Columns cols = request.parseArgs(Columns.class);
        // TODO: create a string converter if necessary
        BasicColStatSketch sk1 = new BasicColStatSketch(cols.col1, null, 0, 1.0);
        BasicColStatSketch sk2 = new BasicColStatSketch(cols.col2, null, 0, 1.0);
        ConcurrentSketch<ITable, BasicColStats, BasicColStats> csk =
                new ConcurrentSketch<>(sk1, sk2);
        this.runSketch(this.table, csk, request, session);
    }

    @HieroRpc
    void range(RpcRequest request, Session session) {
        String column = request.parseArgs(String.class);
        // TODO: create a string converter if necessary
        BasicColStatSketch sk = new BasicColStatSketch(column, null, 0, 1.0);
        this.runSketch(this.table, sk, request, session);
    }

    static class RangeFilter implements TableFilter, Serializable {
        final ColumnAndRange args;
        @Nullable
        IColumn column;

        RangeFilter(ColumnAndRange args) {
            this.args = args;
            this.column = null;
        }

        @Override
        public void setTable(ITable table) {
            IColumn col = table.getColumn(this.args.columnName);
            this.column = Converters.checkNull(col);
        }

        public boolean test(int rowIndex) {
            if (Converters.checkNull(this.column).isMissing(rowIndex))
                return false;
            // TODO: use a proper string converter
            double d = this.column.asDouble(rowIndex, null);
            return this.args.min <= d && d <= this.args.max;
        }
    }

    @HieroRpc
    void filterRange(RpcRequest request, Session session) {
        ColumnAndRange info = request.parseArgs(ColumnAndRange.class);
        RangeFilter filter = new RangeFilter(info);
        FilterMap fm = new FilterMap(filter);
        Function<IDataSet<ITable>, RpcTarget> factory = TableTarget::new;
        this.runMap(this.table, fm, factory, request, session);
    }

    @Override
    public String toString() {
        return "TableTarget object, " + super.toString();
    }
}

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

package org.hiero.sketch.storage;

import com.opencsv.CSVReader;
import org.hiero.sketch.table.BaseListColumn;
import org.hiero.sketch.table.ColumnDescription;
import org.hiero.sketch.table.Schema;
import org.hiero.sketch.table.Table;
import org.hiero.sketch.table.api.ContentsKind;
import org.hiero.sketch.table.api.IColumn;
import org.hiero.sketch.table.api.ITable;
import org.hiero.utils.Converters;

import javax.annotation.Nullable;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Knows how to read a CSV file (comma-separated file).
 */
public class CsvFileReader {
    public static class CsvConfiguration {
        /**
         * Field separator in CSV file.
         */
        public final char separator = ',';
        /**
         * If true we allow a row to have fewer columns; the row is padded with "nulls".
         */
        public boolean allowFewerColumns;
        /**
         * If true the file is expected to have a header row.
         */
        public boolean hasHeaderRow;
        /**
         * If not zero it specifies the expected number of columns.
         */
        public int columnCount;
        /**
         * If true columns are allowed to contain "nulls".
         */
        public boolean allowMissingData;
        /**
         * If non-null it specifies the expected file schema.
         * In this case columnCount is ignored.  If schema is not specified
         * all columns are treated as strings.
         */
        @Nullable
        public Schema schema;
    }

    protected final Path filename;
    protected final CsvConfiguration configuration;
    protected int actualColumnCount;
    @Nullable
    protected Schema actualSchema;
    protected int currentRow;
    protected int currentColumn;
    @Nullable
    protected BaseListColumn[] columns;

    public CsvFileReader(final Path path, CsvConfiguration configuration) {
        this.filename = path;
        this.configuration = configuration;
        this.currentRow = 0;
        this.currentColumn = 0;
    }

    // May return null when an error occurs.
    @Nullable
    public ITable read() throws IOException {
        if (this.configuration.schema != null)
            this.actualSchema = this.configuration.schema;

        try (Reader file = new FileReader(this.filename.toString());
             CSVReader reader = new CSVReader(file, this.configuration.separator)) {
            if (this.configuration.hasHeaderRow) {
                @Nullable
                String[] line = reader.readNext();
                if (line == null)
                    throw new RuntimeException("Missing header row " + this.filename.toString());
                if (this.configuration.schema == null) {
                    this.actualSchema = new Schema();
                    int index = 0;
                    for (String col : line) {
                        if (col.isEmpty())
                            col = this.actualSchema.newColumnName("Column_" + Integer.toString(index));
                        ColumnDescription cd = new ColumnDescription(col,
                                ContentsKind.String,
                                this.configuration.allowMissingData);
                        this.actualSchema.append(cd);
                        index++;
                    }
                } else {
                    this.currentRow++;
                }
            }

            String[] firstLine = null;
            if (this.actualSchema == null) {
                this.actualSchema = new Schema();
                if (this.configuration.columnCount == 0) {
                    firstLine = reader.readNext();
                    if (firstLine == null)
                        throw new RuntimeException("Cannot create schema from empty CSV file");
                    this.actualColumnCount = firstLine.length;
                }

                for (int i = 0; i < this.configuration.columnCount; i++) {
                    ColumnDescription cd = new ColumnDescription("Column " + Integer.toString(i),
                            ContentsKind.String, this.configuration.allowMissingData);
                    this.actualSchema.append(cd);
                }
            }

            Converters.checkNull(this.actualSchema);
            this.actualColumnCount = this.actualSchema.getColumnCount();
            List<IColumn> columns = new ArrayList<IColumn>(this.actualColumnCount);
            this.columns = new BaseListColumn[this.actualColumnCount];
            int index = 0;
            for (String col : this.actualSchema.getColumnNames()) {
                ColumnDescription cd = Converters.checkNull(this.actualSchema.getDescription(col));
                BaseListColumn column = BaseListColumn.create(cd);
                columns.add(column);
                this.columns[index++] = column;
            }

            if (firstLine != null)
                this.append(firstLine);
            while (true) {
                String[] line = reader.readNext();
                if (line == null)
                    break;
                this.append(line);
            }
            return new Table(columns);
        }
    }

    protected void append(String[] data) {
        try {
            Converters.checkNull(this.columns);
            int columnCount = this.columns.length;
            if (data.length > columnCount)
                this.error("Too many columns " + data.length + " vs " + columnCount);
            for (this.currentColumn = 0; this.currentColumn < data.length; this.currentColumn++)
                this.columns[this.currentColumn].parseAndAppendString(data[this.currentColumn]);
            if (data.length < columnCount) {
                if (!this.configuration.allowFewerColumns)
                    this.error("Too few columns " + data.length + " vs " + columnCount);
                else {
                    for (int i = data.length; i < columnCount; i++)
                        this.columns[i].parseAndAppendString("");
                }
            }
            this.currentRow++;
            if ((this.currentRow % 10000) == 0) {
                System.out.print(".");
                System.out.flush();
            }
        } catch (Exception ex) {
            this.error(ex);
        }
    }

    protected String errorMessage() {
        return "Error while parsing CSV file " + this.filename.toString() +
                " line " + this.currentRow + " column " +
                Converters.checkNull(this.columns)[this.currentColumn].getName();
    }

    protected void error(String message) {
        throw new RuntimeException(this.errorMessage() + ": " + message);
    }

    protected void error(Exception ex) {
        throw new RuntimeException(this.errorMessage(), ex);
    }
}

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

package org.hiero;

import org.hiero.sketch.storage.CsvFileReader;
import org.hiero.sketch.storage.CsvFileWriter;
import org.hiero.sketch.table.HashSubSchema;
import org.hiero.sketch.table.Schema;
import org.hiero.sketch.table.api.ITable;
import org.hiero.utils.Converters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.hiero.utils.TestTables;

/**
 * TODO: delete this class
 * This entry point is only used for preparing some data files for a demo.
 * It takes files named like data/On_Time_On_Time_Performance_*_*.csv and
 * removes some columns from them.  Optionally, it can also split these into
 * smaller files each.
 */
public class Main {
    static final String dataFolder = "../data";
    static final String csvFile = "On_Time_Sample.csv";
    static final String schemaFile = "On_Time.schema";

    public static void main(String[] args) throws IOException {
        String[] columns = {
                "DayOfWeek", "FlightDate", "UniqueCarrier",
                "Origin", "OriginCityName", "OriginState", "Dest", "DestState",
                "DepTime", "DepDelay", "ArrTime", "ArrDelay", "Cancelled",
                "ActualElapsedTime", "Distance"
        };

        System.out.println("Splitting files in folder " + dataFolder);
        Path path = Paths.get(dataFolder, schemaFile);
        Schema schema = Schema.readFromJsonFile(path);
        HashSubSchema subschema = new HashSubSchema(columns);
        Schema proj = schema.project(subschema);
        proj.writeToJsonFile(Paths.get(dataFolder, "short.schema"));

        // If non-zero, split each table into parts of this size.
        final int splitSize = 0; // 1 << 16;

        String prefix = "On_Time_On_Time_Performance";
        Path folder = Paths.get(dataFolder);
        Stream<Path> files = Files.walk(folder, 1);
        files.filter(f -> {
            String filename = f.getFileName().toString();
            if (!filename.endsWith("csv")) return false;
            if (!filename.startsWith(prefix)) return false;
            return true;
        }).sorted(Comparator.comparing(Path::toString))
             .forEach(f -> {
                 String filename = f.getFileName().toString();
                 String end = filename.substring(prefix.length() + 1);
                 CsvFileReader.CsvConfiguration config = new CsvFileReader.CsvConfiguration();
                 config.allowFewerColumns = false;
                 config.hasHeaderRow = true;
                 config.allowMissingData = false;
                 config.schema = schema;
                 CsvFileReader r = new CsvFileReader(f, config);

                 ITable tbl = null;
                 try {
                     System.out.println("Reading " + f);
                     tbl = r.read();
                 } catch (IOException e) {
                     e.printStackTrace();
                 }
                 Converters.checkNull(tbl);

                 ITable p = tbl.project(proj);

                 if (splitSize > 0) {
                     List<ITable> pieces = TestTables.splitTable(p, splitSize);

                     int index = 0;
                     for (ITable t : pieces) {
                         String baseName = end.substring(0, end.lastIndexOf("."));
                         String name = baseName + "-" + Integer.toString(index) + ".csv";
                         Path outpath = Paths.get(dataFolder, name);
                         CsvFileWriter writer = new CsvFileWriter(outpath);
                         try {
                             System.out.println("Writing " + outpath);
                             writer.writeTable(t);
                         } catch (IOException e) {
                             e.printStackTrace();
                         }
                         index++;
                     }
                 } else {
                     Path outpath = Paths.get(dataFolder, end);
                     CsvFileWriter writer = new CsvFileWriter(outpath);
                     try {
                         System.out.println("Writing " + outpath);
                         writer.writeTable(p);
                     } catch (IOException e) {
                         e.printStackTrace();
                     }
                 }
             });
    }
}

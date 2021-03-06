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

import {
    IHtmlElement, ScrollBar, Renderer, FullPage, HieroDataView, formatNumber, significantDigits, percent
} from "./ui";
import {RemoteObject, PartialResult, ICancellable} from "./rpc";
import Rx = require('rx');
import {RangeCollector} from "./histogram";
import {DropDownMenu, ContextMenu, PopupMenu} from "./menu";
import {Converters} from "./util";

// These classes are direct counterparts to server-side Java classes
// with the same names.  JSON serialization
// of the Java classes produces JSON that can be directly cast
// into these interfaces.

// I can't use an enum for ContentsKind because JSON deserialization does not
// return an enum from a string.

export type ContentsKind = "Category" | "Json" | "String" | "Integer" | "Double" | "Date" | "Interval";

export interface IColumnDescription {
    readonly kind: ContentsKind;
    readonly name: string;
    readonly allowMissing: boolean;
}

// Direct counterpart to Java class
export class ColumnDescription implements IColumnDescription {
    readonly kind: ContentsKind;
    readonly name: string;
    readonly allowMissing: boolean;

    constructor(v : IColumnDescription) {
        this.kind = v.kind;
        this.name = v.name;
        this.allowMissing = v.allowMissing;
    }
}

// Direct counterpart to Java class
export interface Schema {
    [index: number] : IColumnDescription;
    length: number;
}

export interface RowView {
    count: number;
    values: any[];
}

// Direct counterpart to Java class
export interface ColumnSortOrientation {
    columnDescription: IColumnDescription;
    isAscending: boolean;
}

// Direct counterpart to Java class
class RecordOrder {
    constructor(public sortOrientationList: Array<ColumnSortOrientation>) {}
    public length(): number { return this.sortOrientationList.length; }
    public get(i: number): ColumnSortOrientation { return this.sortOrientationList[i]; }

    // Find the index of a specific column; return -1 if columns is not in the sort order
    public find(col: string): number {
        for (let i = 0; i < this.length(); i++)
            if (this.sortOrientationList[i].columnDescription.name == col)
                return i;
        return -1;
    }
    public hide(col: string): void {
        let index = this.find(col);
        if (index == -1)
            // already hidden
            return;
        this.sortOrientationList.splice(index, 1);
    }
    public show(cso: ColumnSortOrientation) {
        let index = this.find(cso.columnDescription.name);
        if (index != -1)
            this.sortOrientationList.splice(index, 1);
        this.sortOrientationList.splice(0, 0, cso);
    }
    public showIfNotVisible(cso: ColumnSortOrientation) {
        let index = this.find(cso.columnDescription.name);
        if (index == -1)
            this.sortOrientationList.push(cso);
    }
    public clone() : RecordOrder {
        return new RecordOrder(this.sortOrientationList.slice(0));
    }
}

export class TableDataView {
    public schema?: Schema;
    // Total number of rows in the complete table
    public rowCount: number;
    public startPosition?: number;
    public rows?: RowView[];
}

/* Example table view:
-------------------------------------------
| pos | count | col0 v1 | col1 ^0 | col2 |
-------------------------------------------
| 10  |     3 | Mike    |       0 |      |
 ------------------------------------------
 | 13 |     6 | Jon     |       1 |      |
 ------------------------------------------
 */

export class TableView extends RemoteObject
    implements IHtmlElement, HieroDataView {
    protected static initialTableId: string = null;

    // Data view part: received from remote site
    protected schema?: Schema;
    // Logical position of first row displayed
    protected startPosition?: number;
    // Total rows in the table
    protected rowCount?: number;
    protected order: RecordOrder;
    // Computed
    // Logical number of data rows displayed; includes count of each data row
    protected dataRowsDisplayed: number;
    // HTML part
    protected top : HTMLDivElement;
    protected scrollBar : ScrollBar;
    protected htmlTable : HTMLTableElement;
    protected tHead : HTMLTableSectionElement;
    protected tBody: HTMLTableSectionElement;
    protected page: FullPage;
    protected currentData: TableDataView;

    public constructor(id: string, page: FullPage) {
        super(id);

        this.order = new RecordOrder([]);
        this.setPage(page);
        if (TableView.initialTableId == null)
            TableView.initialTableId = id;
        this.top = document.createElement("div");
        this.top.style.flexDirection = "column";
        this.top.style.display = "flex";
        this.top.style.flexWrap = "nowrap";
        this.top.style.justifyContent = "flex-start";
        this.top.style.alignItems = "stretch";
        let menu = new DropDownMenu([
            { text: "View", subMenu: new ContextMenu([
                { text: "home", action: () => { TableView.goHome(this.page); } },
                { text: "refresh", action: () => { this.refresh(); } },
                { text: "all rows", action: () => { this.showAllRows(); } },
                { text: "no rows", action: () => { this.setOrder(new RecordOrder([])); } }
            ])},
            /*
            { text: "Data", subMenu: new ContextMenu([
                { text: "find", action: () => {} },
                { text: "filter", action: () => {} }
            ]),
            } */
        ]);
        this.top.appendChild(menu.getHTMLRepresentation());
        this.top.appendChild(document.createElement("hr"));
        this.htmlTable = document.createElement("table");
        this.scrollBar = new ScrollBar();

        // to force the scroll bar next to the table we put them in yet another div
        let tblAndBar = document.createElement("div");
        tblAndBar.style.flexDirection = "row";
        tblAndBar.style.display = "flex";
        tblAndBar.style.flexWrap = "nowrap";
        tblAndBar.style.justifyContent = "flex-start";
        tblAndBar.style.alignItems = "stretch";
        this.top.appendChild(tblAndBar);
        tblAndBar.appendChild(this.htmlTable);
        tblAndBar.appendChild(this.scrollBar.getHTMLRepresentation());
    }

    protected setOrder(o: RecordOrder): void {
        this.order = o;  // TODO: this should be set by the renderer
        let rr = this.createRpcRequest("getTableView", o);
        rr.invoke(new TableRenderer(this.getPage(), this, rr));
    }

    protected showAllRows(): void {
        if (this.schema == null) {
            this.page.reportError("No data loaded");
            return;
        }

        let o = this.order.clone();
        for (let i = 0; i < this.schema.length; i++) {
            let c = this.schema[i];
            o.showIfNotVisible({ columnDescription: c, isAscending: true });
        }
        this.setOrder(o);
    }

    // Navigate back to the first table known
    public static goHome(page: FullPage): void {
        if (TableView.initialTableId == null)
            return;

        let table = new TableView(TableView.initialTableId, page);
        page.setHieroDataView(table);
        let rr = table.createRpcRequest("getSchema", null);
        rr.invoke(new TableRenderer(page, table, rr));
    }

    columnIndex(colName: string): number {
        if (this.schema == null)
            return null;
        for (let i = 0; i < this.schema.length; i++)
            if (this.schema[i].name == colName)
                return i;
        return null;
    }

    findColumn(colName: string): IColumnDescription {
        let colIndex = this.columnIndex(colName);
        if (colIndex != null)
            return this.schema[colIndex];
        return null;
    }

    setPage(page: FullPage) {
        if (page == null)
            throw("null FullPage");
        this.page = page;
    }

    getPage() : FullPage {
        if (this.page == null)
            throw("Page not set");
        return this.page;
    }

    getSortOrder(column: string): [boolean, number] {
        for (let i = 0; i < this.order.length(); i++) {
            let o = this.order.get(i);
            if (o.columnDescription.name == column)
                return [o.isAscending, i];
        }
        return null;
    }

    public isVisible(column: string): boolean {
        let so = this.getSortOrder(column);
        return so != null;
     }

    public isAscending(column: string): boolean {
        let so = this.getSortOrder(column);
        if (so == null) return null;
        return so[0];
    }

    public getSortIndex(column: string): number {
        let so = this.getSortOrder(column);
        if (so == null) return null;
        return so[1];
    }

    public getSortArrow(column: string): string {
        let asc = this.isAscending(column);
        if (asc == null)
            return "";
        else if (asc)
            return "&dArr;";
        else
            return "&uArr;";
    }

    private addHeaderCell(thr: Node, cd: ColumnDescription) : HTMLElement {
        let thd = document.createElement("th");
        let label = cd.name;
        if (!this.isVisible(cd.name)) {
            thd.style.fontWeight = "normal";
        } else {
            label += " " +
                this.getSortArrow(cd.name) + this.getSortIndex(cd.name);
        }
        thd.innerHTML = label;
        thr.appendChild(thd);
        return thd;
    }

    public showColumn(columnName: string, order: number) : void {
        // order is 0 to hide
        //         -1 to sort descending
        //          1 to sort ascending
        let o = this.order.clone();
        if (order != 0) {
            let col = this.findColumn(columnName);
            if (col == null)
                return;
            o.show({ columnDescription: col, isAscending: order > 0 });
        } else {
            o.hide(columnName);
        }
        this.setOrder(o);
    }

    public histogram(columnName: string): void {
        let rr = this.createRpcRequest("range", columnName);
        let cd = this.findColumn(columnName);
        rr.invoke(new RangeCollector(cd, this.getPage(), this, rr));
    }

    public refresh(): void {
        if (this.currentData == null) {
            this.page.reportError("Nothing to refresh");
            return;
        }
        this.updateView(this.currentData);
    }

    public updateView(data: TableDataView) : void {
        this.currentData = data;
        this.dataRowsDisplayed = 0;
        this.startPosition = data.startPosition;
        this.rowCount = data.rowCount;
        if (this.schema == null)
            this.schema = data.schema;

        if (this.tHead != null)
            this.tHead.remove();
        if (this.tBody != null)
            this.tBody.remove();
        this.tHead = this.htmlTable.createTHead();
        let thr = this.tHead.appendChild(document.createElement("tr"));

        // These two columns are always shown
        let cds : ColumnDescription[] = [];
        let posCd = new ColumnDescription({
            kind: "Integer",
            name: "(position)",
            allowMissing: false });
        let ctCd = new ColumnDescription({
            kind: "Integer",
            name: "(count)",
            allowMissing: false });

        // Create column headers
        this.addHeaderCell(thr, posCd);
        this.addHeaderCell(thr, ctCd);
        if (this.schema == null)
            return;

        for (let i = 0; i < this.schema.length; i++) {
            let cd = new ColumnDescription(this.schema[i]);
            cds.push(cd);
            let thd = this.addHeaderCell(thr, cd);
            let menu = new PopupMenu([
                {text: "sort asc", action: () => this.showColumn(cd.name, 1) },
                {text: "sort desc", action: () => this.showColumn(cd.name, -1) },
                {text: "hide", action: () => this.showColumn(cd.name, 0)}
             ]);
            if (cd.kind != "Json" &&
                cd.kind != "String" &&
                cd.kind != "Category")  // TODO: delete this
                menu.addItem({text: "histogram", action: () => this.histogram(cd.name) });

            thd.onclick = () => menu.toggleVisibility();
            thd.appendChild(menu.getHTMLRepresentation());
        }
        this.tBody = this.htmlTable.createTBody();

        // Add row data
        if (data.rows != null) {
            for (let i = 0; i < data.rows.length; i++)
                this.addRow(data.rows[i], cds);
        }

        // Create table footer
        let footer = this.tBody.insertRow();
        let cell = footer.insertCell(0);
        cell.colSpan = this.schema.length + 2;
        cell.className = "footer";

        let perc = "";
        if (this.rowCount > 0) {
            perc = percent(this.dataRowsDisplayed / this.rowCount);
            perc = " (" + perc + ")";
        }

        cell.textContent = "Showing " + formatNumber(this.dataRowsDisplayed) +
            " of " + formatNumber(this.rowCount) + " rows" + perc;

        this.updateScrollBar();
    }

    private updateScrollBar(): void {
        if (this.startPosition == null || this.rowCount == null)
            return;
        console.log("Scroll bar ", this.startPosition/this.rowCount,
            (this.startPosition+this.dataRowsDisplayed)/this.rowCount);
        this.setScroll(this.startPosition / this.rowCount,
            (this.startPosition + this.dataRowsDisplayed) / this.rowCount);
    }

    public getRowCount() : number {
        return this.tBody.childNodes.length;
    }

    public getColumnCount() : number {
        return this.schema.length;
    }

    public getHTMLRepresentation() : HTMLElement {
        return this.top;
    }

    protected static convert(val: any, kind: ContentsKind): string {
        if (kind == "Integer" || kind == "Double")
            return String(val);
        else if (kind == "Date")
            return Converters.dateFromDouble(<number>val).toDateString();
        else if (kind == "Category" || kind == "String")
            return <string>val;
        else
            return "?";  // TODO
    }

    public addRow(row : RowView, cds: ColumnDescription[]) : void {
        let trow = this.tBody.insertRow();

        let cell = trow.insertCell(0);
        cell.style.textAlign = "right";
        cell.textContent = significantDigits(this.startPosition + this.dataRowsDisplayed);

        cell = trow.insertCell(1);
        cell.style.textAlign = "right";
        cell.textContent = significantDigits(row.count);

        for (let i = 0; i < cds.length; i++) {
            let cd = cds[i];
            cell = trow.insertCell(i + 2);

            let dataIndex = this.order.find(cd.name);
            if (dataIndex == -1)
                continue;
            if (this.isVisible(cd.name)) {
                cell.style.textAlign = "right";
                cell.textContent = TableView.convert(row.values[dataIndex], cd.kind);
            }
        }
        this.dataRowsDisplayed += row.count;
    }

    public setScroll(top: number, bottom: number) : void {
        this.scrollBar.setPosition(top, bottom);
    }
}

export class TableRenderer extends Renderer<TableDataView> {
    constructor(page: FullPage,
                protected table: TableView,
                operation: ICancellable) {
        super(page, operation, "Geting table info");
    }

    onNext(value: PartialResult<TableDataView>): void {
        super.onNext(value);
        this.table.updateView(value.data);
    }
}

export class RemoteTableReceiver extends Renderer<string> {
    public remoteTableId: string;

    constructor(page: FullPage, operation: ICancellable) {
        super(page, operation, "Get schema");
    }

    protected getTableSchema(tableId: string) {
        let table = new TableView(tableId, this.page);
        this.page.setHieroDataView(table);
        let rr = table.createRpcRequest("getSchema", null);
        rr.invoke(new TableRenderer(this.page, table, rr));
    }

    public onNext(value: PartialResult<string>): void {
        super.onNext(value);
        if (value.data != null)
            this.remoteTableId = value.data;
    }

    public onCompleted(): void {
        this.finished();
        if (this.remoteTableId == null)
            return;
        this.getTableSchema(this.remoteTableId);
    }
}

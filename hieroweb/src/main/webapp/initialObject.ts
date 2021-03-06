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

import {RemoteObject, ICancellable, RpcReceiver} from "./rpc";
import {RemoteTableReceiver} from "./table";
import {FullPage} from "./ui";

class FileNames extends RemoteObject {
    constructor(remoteObjectId: string) {
        super(remoteObjectId);
    }

    public loadTable(page: FullPage): void {
        let rr = this.createRpcRequest("loadTable", null);
        let observer = new RemoteTableReceiver(page, rr);
        rr.invoke(observer);
    }
}

class FileNamesReceiver extends RpcReceiver<string> {
    private files: FileNames;

    constructor(protected page: FullPage, operation: ICancellable) {
        super(page.progressManager.newProgressBar(operation, "Find files"),
            page.getErrorReporter());
    }

    // only one reply expected
    public onNext(value: string): void {
        if (value != null)
            this.files = new FileNames(value);
    }

    public onCompleted(): void {
        this.finished();
        if (this.files)
            this.files.loadTable(this.page);
    }
}

export class InitialObject extends RemoteObject {
    public static instance: InitialObject = new InitialObject();

    // The "0" argument is the object id for the initial object.
    // It must match the id of the object declared in RpcServer.java.
    // This is a "well-known" name used for bootstrapping the system.
    private constructor() { super("0"); }

    public loadTable(page: FullPage): void {
        let rr = this.createRpcRequest("prepareFiles", null);
        let observer = new FileNamesReceiver(page, rr);
        rr.invoke(observer);
    }
}

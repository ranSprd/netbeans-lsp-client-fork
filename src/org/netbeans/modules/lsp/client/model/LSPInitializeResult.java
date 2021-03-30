/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.lsp.client.model;

import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerInfo;

/**
 * Wrapper class around LSB4j InitializeResult. It contains some addtional service methods.
 * @author ranSprd
 */
public class LSPInitializeResult {
    
    private final InitializeResult org;
    private final LSPServerCapabilities capabilities;

    public LSPInitializeResult(InitializeResult org) {
        this.org = org;
        if (org != null) {
            this.capabilities = new LSPServerCapabilities(org.getCapabilities());
        } else {
            // @todo - use a implementation with default values
            this.capabilities = new LSPServerCapabilities(null);
        }
    }

    public LSPServerCapabilities getCapabilities() {
        return capabilities;
    }

    public ServerInfo getServerInfo() {
        return org.getServerInfo();
    }
    
}

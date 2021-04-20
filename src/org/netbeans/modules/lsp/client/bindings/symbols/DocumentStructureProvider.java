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
package org.netbeans.modules.lsp.client.bindings.symbols;

import java.util.HashMap;
import java.util.Map;
import javax.swing.text.Document;
import org.netbeans.modules.lsp.client.LSPWorkingPool;
import org.netbeans.modules.lsp.client.Utils;
import org.openide.filesystems.FileObject;

/**
 *
 * Central data/token/symbol cache for a document
 * 
 * @author ranSprd
 */
public enum DocumentStructureProvider {
    
    INSTANCE;
    
    private Map<String, ParsedDocumentData> documents = new HashMap<>();
    
    public ParsedDocumentData register(Document document, FileObject file) { 
        if (document == null || file == null) {
            return null;
        }
        
        String uri = Utils.toURI(file);

        synchronized(this) {
            return documents.computeIfAbsent(uri, (x) -> createAndInitialize(document, uri));
        }
    }
    
    public void unregister(FileObject file) {
        if (file == null) {
            return;
        }
        String uri = Utils.toURI(file);
        synchronized(this) {
            ParsedDocumentData data = documents.remove(uri);
            if (data != null) {
                data.close();
            }
        }
    }
    
    /**
     * The client should ask the server to recompute the semantic tokens.
     */
    public void refresh() {
        //@todo
    }
    
    private ParsedDocumentData createAndInitialize(Document document, String fileUri) {
        ParsedDocumentData pdd = new ParsedDocumentData(document, fileUri);
        LSPWorkingPool.ASYNC.post( () -> pdd.refresh());
        return pdd;
    }
    
}

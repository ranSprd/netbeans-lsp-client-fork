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
package org.netbeans.modules.lsp.client.bindings;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.netbeans.modules.lsp.client.LSPBindings;
import org.netbeans.modules.lsp.client.LSPWorkingPool;
import org.netbeans.modules.lsp.client.Utils;
import org.openide.filesystems.FileObject;

/**
 * A 'codelens' represents additional code informations, like the number of references,
 * a way to run tests, etc....
 * 
 * THAT is WorkInProgress
 * 
 * @author ranSprd
 */
public class CodeLensImpl implements LSPWorkingPool.BackgroundTask {


    @Override
    public void run(LSPBindings bindings, FileObject file) {
        try { 
            String uri = Utils.toURI(file);
            TextDocumentService docService = bindings.getTextDocumentService();

            CodeLensParams clp = new CodeLensParams();
            clp.setTextDocument(new TextDocumentIdentifier(uri));
            
            List<? extends CodeLens> codeLens = docService.codeLens(clp).get();
            
            System.out.println(codeLens);
            
            for(CodeLens cl : codeLens) {
                CodeLens resolved = docService.resolveCodeLens(cl).get();
                System.out.println( resolved);
                
            }

        } catch (ExecutionException ex) {
//                LOG.log(Level.WARNING, "Can't load data from TextDocumentService", ex);
//                setKeys(Collections.emptyList());
        } catch (InterruptedException ex) {
            //try again:
            LSPWorkingPool.addBackgroundTask(file, this);
        }
    }
    
}

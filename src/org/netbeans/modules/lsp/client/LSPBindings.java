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
package org.netbeans.modules.lsp.client;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.netbeans.modules.lsp.client.model.LSPInitializeResult;
import org.openide.filesystems.FileObject;

/**
 *
 * @author lahvac
 */
public class LSPBindings {

    private static final Logger LOG = Logger.getLogger(LSPBindings.class.getName());

    private final Set<FileObject> openedFiles = new HashSet<>();



    private final LanguageServer server;
    private final LSPInitializeResult initResult;
    private final Process process;

    protected LSPBindings(LanguageServer server, InitializeResult initResult, Process process) {
        this.server = server;
        this.initResult = new LSPInitializeResult(initResult);
        this.process = process;
    }

    /**
     * A notification to ask the server to exit its process.
     */
    public void shutdown() {
        if (this.server != null) {
            this.server.exit();
        }
    }
    
    /**
     * send shutdown message to the server and killRunningServerProcess the process afterwards
     */
    public void shutdownAndKill() {
        if (this.server != null) {

            try {
//            LOG.log(Level.WARNING, "shutting down LSP server");
                this.server.shutdown().get();
            } catch (InterruptedException | ExecutionException ex) {
                LOG.log(Level.FINE, null, ex);
            }
            killRunningServerProcess();
        }
    }
    
    public void killRunningServerProcess() {
        if (this.process != null) {
            this.process.destroy();
        }
    }
    
    public boolean isProcessAlive() {
        return (process != null && process.isAlive());
    }
    
    public TextDocumentService getTextDocumentService() {
        return server.getTextDocumentService();
    }

    public WorkspaceService getWorkspaceService() {
        return server.getWorkspaceService();
    }

    public LSPInitializeResult getInitResult() {
        //XXX: defenzive copy?
        return initResult;
    }


    public Set<FileObject> getOpenedFiles() {
        return openedFiles;
    }

    /**
     * The {@code LSPReference} adds cleanup actions to LSP Bindings after the
     * bindings are GCed. The backing process is shutdown and the process
     * terminated.
     */
    protected static class LSPReference extends WeakReference<LSPBindings> implements Runnable {
        private final LanguageServer server;
        private final Process process;

        @SuppressWarnings("AccessingNonPublicFieldOfAnotherObject")
        public LSPReference(LSPBindings t, ReferenceQueue<? super LSPBindings> rq) {
            super(t, rq);
            this.server = t.server;
            this.process = t.process;
        }

        @Override
        public void run() {
            if(! process.isAlive()) {
                return;
            }
            CompletableFuture<Object> shutdownResult = server.shutdown();
            for (int i = 0; i < 300; i--) {
                try {
                    shutdownResult.get(100, TimeUnit.MILLISECONDS);
                    break;
                } catch (TimeoutException ex) {
                } catch (InterruptedException | ExecutionException ex) {
                    break;
                }
            }
            this.server.exit();
            try {
                if(! process.waitFor(30, TimeUnit.SECONDS)) {
                    process.destroy();
                }
            } catch (InterruptedException ex) {
                process.destroy();
            }

        }
    }
}

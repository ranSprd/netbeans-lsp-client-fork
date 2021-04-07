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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.text.Document;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionContext;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.LogTraceParams;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.ProgressParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.ShowDocumentParams;
import org.eclipse.lsp4j.ShowDocumentResult;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.netbeans.modules.lsp.client.LSPBindings;
import org.netbeans.modules.lsp.client.Utils;
import org.netbeans.modules.lsp.client.log.LogStorage;
import org.netbeans.spi.editor.hints.ChangeInfo;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.ErrorDescriptionFactory;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.editor.hints.HintsController;
import org.netbeans.spi.editor.hints.LazyFixList;
import org.netbeans.spi.editor.hints.Severity;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.Exceptions;
import org.openide.util.RequestProcessor;

/**
 *
 * @author lahvac
 */
public class LanguageClientImpl implements LanguageClient {

    private static final Logger LOG = Logger.getLogger(LanguageClientImpl.class.getName());
    private static final RequestProcessor WORKER = new RequestProcessor(LanguageClientImpl.class.getName(), 1, false, false);
    
    private static final Map<DiagnosticSeverity, Severity> severityMap = new EnumMap<>(DiagnosticSeverity.class);
    
    static {
        severityMap.put(DiagnosticSeverity.Error, Severity.ERROR);
        severityMap.put(DiagnosticSeverity.Hint, Severity.HINT);
        severityMap.put(DiagnosticSeverity.Information, Severity.HINT);
        severityMap.put(DiagnosticSeverity.Warning, Severity.WARNING);
    }

    private LSPBindings bindings;
    private boolean allowCodeActions;

    public void setBindings(LSPBindings bindings) {
        this.bindings = bindings;
        this.allowCodeActions = bindings.getInitResult().getCapabilities().hasCodeActionSupport();
    }

    /**
     * The telemetry notification is sent from the server to the client to ask
     * the client to log a telemetry event.
     */
    @Override
    public void telemetryEvent(Object arg0) {
        System.err.println("telemetry: " + arg0);
    }

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams pdp) {
        try {
            FileObject file = URLMapper.findFileObject(new URI(pdp.getUri()).toURL());
            EditorCookie ec = file.getLookup().lookup(EditorCookie.class);
            Document doc = ec != null ? ec.getDocument() : null;
            if (doc == null)
                return ; //ignore...
            List<ErrorDescription> diags = pdp.getDiagnostics().stream().map(d -> {
                LazyFixList fixList = allowCodeActions ? new DiagnosticFixList(pdp.getUri(), d) : ErrorDescriptionFactory.lazyListForFixes(Collections.emptyList());
                return ErrorDescriptionFactory.createErrorDescription(severityMap.get(d.getSeverity()), d.getMessage(), fixList, file, Utils.getOffset(doc, d.getRange().getStart()), Utils.getOffset(doc, d.getRange().getEnd()));
            }).collect(Collectors.toList());
            HintsController.setErrors(doc, LanguageClientImpl.class.getName(), diags);
        } catch (URISyntaxException | MalformedURLException ex) {
            LOG.log(Level.FINE, null, ex);
        }
    }


    /**
     * The workspace/applyEdit request is sent from the server to the client to modify 
     * resource on the client side.
     */
    @Override
    public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
        Utils.applyWorkspaceEdit(params.getEdit());
        return CompletableFuture.completedFuture(new ApplyWorkspaceEditResponse(true));
    }

    /**
     * The show message notification is sent from a server to a client to ask the client 
     * to display a particular message in the user interface.
     */
    @Override
    public void showMessage(MessageParams arg0) {
        System.err.println("showMessage: " + arg0);
    }

    /**
     * The show message request is sent from a server to a client to ask the
     * client to display a particular message in the user interface. In addition
     * to the show message notification the request allows to pass actions and
     * to wait for an answer from the client.
     */
    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams arg0) {
        System.err.println("showMessageRequest: " +arg0);
        return null; //???
    }

    /**
     * The log message notification is send from the server to the client to ask
     * the client to log a particular message.
     */
    @Override
    public void logMessage(MessageParams arg0) {
        switch(arg0.getType()) {
            case Error: LogStorage.ALL.error(arg0.getMessage());
                        break;
            case Warning: LogStorage.ALL.warning(arg0.getMessage());
                        break;
            default: LogStorage.ALL.info(arg0.getMessage());
                        break;
        }
//        System.err.println("logMessage: " + arg0);
    }
    
    
    /**
     * The workspace/configuration request is sent from the server to the client to fetch
     * configuration settings from the client. The request can fetch several configuration settings
     * in one roundtrip. The order of the returned configuration settings correspond to the
     * order of the passed ConfigurationItems (e.g. the first item in the response is the
     * result for the first configuration item in the params).
     * 
     */
    @Override
    public CompletableFuture<List<Object>> configuration(ConfigurationParams configurationParams) {
        CompletableFuture<List<Object>> result = new CompletableFuture<>();
        WORKER.post(() -> {
            List<Object> outcome = new ArrayList<>();
            for (ConfigurationItem ci : configurationParams.getItems()) {
                outcome.add(null);
            }
            result.complete(outcome);
        });
        return result;
    }
    
    /**
     * The workspace/workspaceFolders request is sent from the server to the client
     * to fetch the current open list of workspace folders.
     *
     * @return null in the response if only a single file is open in the tool,
     *         an empty array if a workspace is open but no folders are configured,
     *         the workspace folders otherwise.
     * 
     */
    @Override
    public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
        CompletableFuture<List<WorkspaceFolder>> result = new CompletableFuture<>();
        WORKER.post(() -> {
            List<WorkspaceFolder> outcome = new ArrayList<>();
            
            // @todo add folder logic
            result.complete(outcome);
        });
        return result;
    }

    /**
     * The client/registerCapability request is sent from the server to the client
     * to register for a new capability on the client side.
     * Not all clients need to support dynamic capability registration.
     * A client opts in via the ClientCapabilities.dynamicRegistration property
     */
    @Override
    public CompletableFuture<Void> registerCapability(RegistrationParams params) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        // @todo
        return result;
    }

    /**
     * The client/unregisterCapability request is sent from the server to the client
     * to unregister a previously register capability.
     */
    @Override
    public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        // @todo
        return result;
    }

    /**
     * The show document request is sent from a server to a client to ask the
     * client to display a particular document in the user interface.
     * 
     */
    @Override
    public CompletableFuture<ShowDocumentResult> showDocument(ShowDocumentParams params) {
        CompletableFuture<ShowDocumentResult> result = new CompletableFuture<>();
        WORKER.post(() -> {
            ShowDocumentResult outcome = new ShowDocumentResult();
            // @todo show and log success here
            outcome.setSuccess(true);
            
            result.complete(outcome);
        });
        return result;
    }

    /**
     * This request is sent from the server to the client to ask the client to create a work done progress.
     * 
     */
    @Override
    public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        // @todo
        return result;
    }

    /**
     * The base protocol offers also support to report progress in a generic fashion.
     * This mechanism can be used to report any kind of progress including work done progress
     * (usually used to report progress in the user interface using a progress bar)
     * and partial result progress to support streaming of results.
     * 
     * Since 3.15.0
     */
    @Override
    public void notifyProgress(ProgressParams params) {
        // @todo
    }

    /**
     * A notification to log the trace of the server's execution. The amount and content of these
     * notifications depends on the current trace configuration. If trace is 'off', the server
     * should not send any logTrace notification. If trace is 'message', the server should not
     * add the 'verbose' field in the LogTraceParams.
     *
     * $/logTrace should be used for systematic trace reporting. For single debugging messages,
     * the server should send window/logMessage notifications.
     * 
     */
    @Override
    public void logTrace(LogTraceParams params) {
        // @todo
        System.out.println("logTrace: " +params);
    }

    /**
     * A notification that should be used by the client to modify the trace setting of the server.
     * 
     */
    @Override
    public void setTrace(SetTraceParams params) {
        // @todo
    }

    /**
     * The `workspace/semanticTokens/refresh` request is sent from the server to the client.
     * Servers can use it to ask clients to refresh the editors for which this server
     * provides semantic tokens. As a result the client should ask the server to recompute
     * the semantic tokens for these editors. This is useful if a server detects a project wide
     * configuration change which requires a re-calculation of all semantic tokens.
     * Note that the client still has the freedom to delay the re-calculation of the semantic tokens
     * if for example an editor is currently not visible.
     * 
     */
    @Override
    public CompletableFuture<Void> refreshSemanticTokens() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        // @todo
        return result;
    }

    /**
     * The {@code workspace/codeLens/refresh} request is sent from the server to the client.
     * Servers can use it to ask clients to refresh the code lenses currently shown in editors.
     * As a result the client should ask the server to recompute the code lenses for these editors.
     * This is useful if a server detects a configuration change which requires a re-calculation of
     * all code lenses. Note that the client still has the freedom to delay the re-calculation of
     * the code lenses if for example an editor is currently not visible.
     * 
     */
    @Override
    public CompletableFuture<Void> refreshCodeLenses() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        // @todo
        return result;
    }
    
    
    

    private final class DiagnosticFixList implements LazyFixList {

        private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
        private final String fileUri;
        private final Diagnostic diagnostic;
        private List<Fix> fixes;
        private boolean computing;
        private boolean computed;

        public DiagnosticFixList(String fileUri, Diagnostic diagnostic) {
            this.fileUri = fileUri;
            this.diagnostic = diagnostic;
        }

        @Override
        public void addPropertyChangeListener(PropertyChangeListener l) {
            pcs.addPropertyChangeListener(l);
        }

        @Override
        public void removePropertyChangeListener(PropertyChangeListener l) {
            pcs.removePropertyChangeListener(l);
        }

        @Override
        public boolean probablyContainsFixes() {
            return true;
        }

        @Override
        public synchronized List<Fix> getFixes() {
            if (!computing && !computed) {
                computing = true;
                bindings.runOnBackground(() -> {
                    try {
                        List<Either<Command, CodeAction>> commands =
                                bindings.getTextDocumentService().codeAction(new CodeActionParams(new TextDocumentIdentifier(fileUri),
                                        diagnostic.getRange(),
                                        new CodeActionContext(Collections.singletonList(diagnostic)))).get();
                        List<Fix> fixes = commands.stream()
                                                  .map(cmd -> new CommandBasedFix(cmd))
                                                  .collect(Collectors.toList());
                        synchronized (this) {
                            this.fixes = Collections.unmodifiableList(fixes);
                            this.computed = true;
                            this.computing = false;
                        }
                        pcs.firePropertyChange(PROP_COMPUTED, null, null);
                        pcs.firePropertyChange(PROP_FIXES, null, null);
                    } catch (InterruptedException | ExecutionException ex) {
                        Exceptions.printStackTrace(ex);
                    }
                });
            }
            return fixes;
        }

        @Override
        public synchronized boolean isComputed() {
            return computed;
        }

        private class CommandBasedFix implements Fix {

            private final Either<Command, CodeAction> cmd;

            public CommandBasedFix(Either<Command, CodeAction> cmd) {
                this.cmd = cmd;
            }

            @Override
            public String getText() {
                return cmd.isLeft() ? cmd.getLeft().getTitle() : cmd.getRight().getTitle();
            }

            @Override
            public ChangeInfo implement() throws Exception {
                Utils.applyCodeAction(bindings, cmd);
                return null;
            }
        }
        
    }
}

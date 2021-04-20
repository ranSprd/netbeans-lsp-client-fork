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

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.Document;
import org.eclipse.lsp4j.ApplyWorkspaceEditParams;
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
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
import org.eclipse.lsp4j.UnregistrationParams;
import org.eclipse.lsp4j.WorkDoneProgressCreateParams;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.netbeans.modules.lsp.client.LSPBindings;
import org.netbeans.modules.lsp.client.Utils;
import org.netbeans.modules.lsp.client.bindings.hints.HintsAndErrorsProvider;
import org.netbeans.modules.lsp.client.log.LogStorage;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.HintsController;
import org.openide.cookies.EditorCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.URLMapper;
import org.openide.util.RequestProcessor;

/**
 *
 * @author lahvac
 */
public class LanguageClientImpl implements LanguageClient {

    private static final Logger LOG = Logger.getLogger(LanguageClientImpl.class.getName());
    private static final RequestProcessor WORKER = new RequestProcessor(LanguageClientImpl.class.getName(), 1, false, false);
    
    private boolean allowCodeActions;
    private HintsAndErrorsProvider hintsAndErrorsProvider;
    
    public void setBindings(LSPBindings bindings) {
        this.allowCodeActions = bindings.getInitResult().getCapabilities().hasCodeActionSupport();
        this.hintsAndErrorsProvider = new HintsAndErrorsProvider(bindings);
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
        if (allowCodeActions && hintsAndErrorsProvider != null) {
            try {
                FileObject file = URLMapper.findFileObject(new URI(pdp.getUri()).toURL());
                EditorCookie ec = file.getLookup().lookup(EditorCookie.class);
                Document doc = ec != null ? ec.getDocument() : null;
                if (doc == null) {
                    return ; //ignore...
                }

                WORKER.post(() -> {                
                    List<ErrorDescription> errorDescriptions = hintsAndErrorsProvider.transform(pdp, file, doc);
                    HintsController.setErrors(doc, LanguageClientImpl.class.getName(), errorDescriptions);
                });
                
            } catch (URISyntaxException | MalformedURLException ex) {
                LOG.log(Level.FINE, null, ex);
            }
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
    public void logMessage(MessageParams message) {
        switch(message.getType()) {
            case Error: LogStorage.ALL.error(message.getMessage());
                        break;
            case Warning: LogStorage.ALL.warning(message.getMessage());
                        break;
            default: LogStorage.ALL.info(message.getMessage());
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
                System.out.println("server ask for config " +ci);
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
        System.err.println("register Capabilities " +params);
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
    
    
}
